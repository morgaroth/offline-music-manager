package io.morgaroth.media.library.jobs

import io.circe.Decoder

case class YoutubeDLMeta(
  description: String,
  title: String,
  track: Option[String],
  artist: Option[String],
  thumbnail: String,
  fulltitle: String,
  _filename: Option[String],
) derives Decoder

class URLFetchError(title: String, artist: String, album: String, searchUrl: String)
  extends Throwable(s"cannot fetch $title - $artist ($album), search it again $searchUrl")
