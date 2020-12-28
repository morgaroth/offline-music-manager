package io.morgaroth.media.library

import java.time.LocalDateTime

package object common {
  implicit val orderingOfLocalDateTime: Ordering[LocalDateTime] = _ compareTo _

}
