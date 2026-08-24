package io.morgaroth.media.library

import java.time.ZonedDateTime

package object common:
  given Ordering[ZonedDateTime] = _ compareTo _
