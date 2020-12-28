package io.morgaroth.media.library

import java.time.ZonedDateTime

package object common {
  implicit val orderingOfZonedDateTime: Ordering[ZonedDateTime] = _ compareTo _

}
