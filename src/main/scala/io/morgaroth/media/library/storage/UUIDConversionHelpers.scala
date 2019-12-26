package io.morgaroth.media.library.storage

import java.util.UUID

import com.mongodb.casbah.commons.conversions.MongoConversionHelper
import org.bson.{BSON, Transformer}

object UUIDConversionHelpers extends MongoConversionHelper {

  private val encoder = new Transformer {
    log.trace("Encoding an UUID.")

    def transform(o: AnyRef): AnyRef = {
      o match {
        case uuid: UUID => uuid.toString
        case _ => o
      }
    }
  }

  private val clazz = classOf[UUID]

  override def register(): Unit = {
    super.register()
    BSON.addEncodingHook(clazz, encoder)
  }

  override def unregister(): Unit = {
    super.unregister()
    BSON.removeEncodingHook(clazz, encoder)
  }
}
