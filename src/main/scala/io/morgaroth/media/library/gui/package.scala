package io.morgaroth.media.library

import io.morgaroth.media.library.storage.Track

package object gui {

  implicit class ds(in: Option[Track]) {
    def orEmpty(extractor: Track => String): String = {
      in.map(extractor).getOrElse("")
    }
  }
}
