package io.morgaroth.media.library.storage

/** What kind of media a track is. `Music` is extracted to mp3; `Video` is
  * transcoded to H.264/AAC MP4 for broad player compatibility (Jellyfin + old
  * webOS). Existing rows default to `Music`.
  */
enum MediaType:
  case Music, Video

  lazy val dbRepr: String = this.toString.toLowerCase

object MediaType:
  val all: Vector[MediaType] = Vector(MediaType.Music, MediaType.Video)
  val byDbRepr: Map[String, MediaType] = all.map(x => x.dbRepr -> x).toMap
  val default: MediaType = MediaType.Music
