import { Type } from "typebox";
import { definePluginEntry } from "openclaw/plugin-sdk/plugin-entry";

/**
 * OpenClaw plugin for the offline music library.
 *
 * This is a thin adapter: each tool maps to an endpoint of the Scala
 * `MusicLibraryHttp` service (zio-http). The plugin holds no music logic of its
 * own; it turns the HTTP API into typed, discoverable agent tools.
 *
 * Read/list tools are required; mutating and side-effecting tools
 * (add/update/fetch) are optional and must be allowlisted before the agent can
 * call them.
 */

const DEFAULT_BASE_URL = "http://127.0.0.1:8080";

function resolveBaseUrl(api: unknown): string {
  // Plugin config is validated against configSchema.baseUrl; fall back to the
  // env var, then the local default.
  const cfg = (api as { config?: { baseUrl?: string } } | undefined)?.config;
  return (
    cfg?.baseUrl ??
    process.env.MUSIC_LIBRARY_BASE_URL ??
    DEFAULT_BASE_URL
  ).replace(/\/+$/, "");
}

type FetchResult = { ok: boolean; status: number; body: string };

async function httpJson(
  baseUrl: string,
  method: string,
  path: string,
  body?: unknown,
): Promise<FetchResult> {
  const res = await fetch(`${baseUrl}${path}`, {
    method,
    headers: body === undefined ? {} : { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  return { ok: res.ok, status: res.status, body: text };
}

/** Wrap an HTTP result into the OpenClaw tool result shape. */
function toToolResult(result: FetchResult) {
  const text = result.ok
    ? result.body
    : `HTTP ${result.status}: ${result.body}`;
  return {
    content: [{ type: "text" as const, text }],
    details: { status: result.status, ok: result.ok, body: result.body },
  };
}

const resultSchema = Type.Object(
  {
    status: Type.Number(),
    ok: Type.Boolean(),
    body: Type.String(),
  },
  { additionalProperties: false },
);

function encodeQuery(params: Record<string, string | number | undefined>): string {
  const entries = Object.entries(params).filter(
    ([, v]) => v !== undefined && v !== "",
  );
  if (entries.length === 0) return "";
  const qs = entries
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`)
    .join("&");
  return `?${qs}`;
}

export default definePluginEntry({
  id: "music-library",
  name: "Offline Music Library",
  description:
    "Search, inspect, edit, and fetch tracks in the offline music library",
  register(api) {
    const baseUrl = resolveBaseUrl(api);

    // --- Read tools (required) ---

    api.registerTool({
      name: "music_search",
      description:
        "Search tracks by artist/title/status, or do a generic text search. Returns matching tracks as JSON.",
      parameters: Type.Object({
        artist: Type.Optional(Type.String()),
        title: Type.Optional(Type.String()),
        status: Type.Optional(
          Type.Union([
            Type.Literal("draft"),
            Type.Literal("final"),
            Type.Literal("deleted"),
          ]),
        ),
        q: Type.Optional(
          Type.String({ description: "Generic full-text query across all fields" }),
        ),
        page: Type.Optional(Type.Number({ description: "Page for generic search (default 1)" })),
        limit: Type.Optional(Type.Number()),
      }),
      outputSchema: resultSchema,
      async execute(_id, params) {
        const query = encodeQuery({
          artist: params.artist,
          title: params.title,
          status: params.status,
          q: params.q,
          page: params.page,
          limit: params.limit,
        });
        return toToolResult(await httpJson(baseUrl, "GET", `/tracks${query}`));
      },
    });

    api.registerTool({
      name: "music_get_track",
      description: "Get a single track by its UUID.",
      parameters: Type.Object({ id: Type.String({ description: "Track UUID" }) }),
      outputSchema: resultSchema,
      async execute(_id, params) {
        return toToolResult(
          await httpJson(baseUrl, "GET", `/tracks/${encodeURIComponent(params.id)}`),
        );
      },
    });

    api.registerTool({
      name: "music_list_playlists",
      description: "List all playlists and the tracks in each.",
      parameters: Type.Object({}),
      outputSchema: resultSchema,
      async execute() {
        return toToolResult(await httpJson(baseUrl, "GET", "/playlists"));
      },
    });

    api.registerTool({
      name: "music_job_status",
      description: "Check the status of an async fetch job by its job id.",
      parameters: Type.Object({ jobId: Type.String() }),
      outputSchema: resultSchema,
      async execute(_id, params) {
        return toToolResult(
          await httpJson(baseUrl, "GET", `/jobs/${encodeURIComponent(params.jobId)}`),
        );
      },
    });

    // --- Mutating tools (optional; require allowlist) ---

    api.registerTool(
      {
        name: "music_add_track",
        description: "Add a new track from a source URL.",
        parameters: Type.Object({ url: Type.String() }),
        outputSchema: resultSchema,
        async execute(_id, params) {
          return toToolResult(
            await httpJson(baseUrl, "POST", "/tracks", { url: params.url }),
          );
        },
      },
      { name: "music_add_track", optional: true },
    );

    api.registerTool(
      {
        name: "music_update_track",
        description:
          "Update one field of a track. Field is one of: artist, title, album, url, status, start-at, end-at. For start-at/end-at, pass an empty value to clear.",
        parameters: Type.Object({
          id: Type.String({ description: "Track UUID" }),
          field: Type.Union([
            Type.Literal("artist"),
            Type.Literal("title"),
            Type.Literal("album"),
            Type.Literal("url"),
            Type.Literal("status"),
            Type.Literal("start-at"),
            Type.Literal("end-at"),
          ]),
          value: Type.String(),
        }),
        outputSchema: resultSchema,
        async execute(_id, params) {
          // The Scala API reads the field name from the JSON body key. Map the
          // dashed path segments to their camelCase body keys.
          const bodyKey =
            params.field === "start-at"
              ? "startAt"
              : params.field === "end-at"
                ? "endAt"
                : params.field;
          return toToolResult(
            await httpJson(
              baseUrl,
              "PATCH",
              `/tracks/${encodeURIComponent(params.id)}/${params.field}`,
              { [bodyKey]: params.value },
            ),
          );
        },
      },
      { name: "music_update_track", optional: true },
    );

    api.registerTool(
      {
        name: "music_fetch_track",
        description:
          "Trigger an async download/convert for a track. Returns a job id immediately; poll music_job_status for progress.",
        parameters: Type.Object({
          id: Type.String({ description: "Track UUID" }),
          force: Type.Optional(
            Type.Number({ description: "Force level: 0 none, 1 re-extract, 2 re-encode" }),
          ),
          debug: Type.Optional(Type.Boolean()),
        }),
        outputSchema: resultSchema,
        async execute(_id, params) {
          const query = encodeQuery({
            force: params.force,
            debug: params.debug === undefined ? undefined : String(params.debug),
          });
          return toToolResult(
            await httpJson(
              baseUrl,
              "POST",
              `/tracks/${encodeURIComponent(params.id)}/fetch${query}`,
            ),
          );
        },
      },
      { name: "music_fetch_track", optional: true },
    );
  },
});
