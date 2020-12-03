package io.morgaroth.media.library.gui

import io.morgaroth.media.library.storage.Track

package object panes {

  implicit class ds(in: Option[Track]) {
    def orEmpty(extractor: Track => String): String = {
      in.map(extractor).getOrElse("")
    }
  }
}
