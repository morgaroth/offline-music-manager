package io.morgaroth.media.library.storage

import com.mongodb.casbah.commons.conversions.MongoConversionHelper
import org.bson.{BSON, Transformer}

import java.time.{ZoneId, ZoneOffset}
import java.util.Date


object JavaLocalDateTimeHelpers extends JavaLocalDateTimeSerializer with JavaLocalDateTimeDeserializer

trait JavaLocalDateTimeSerializer extends MongoConversionHelper {

  private val encodeTypeLocalDateTime = classOf[java.time.LocalDateTime]

  private val transformer = new Transformer {
    log.trace("Encoding a java.time.LocalDateTime.")

    def transform(o: AnyRef): AnyRef = o match {
      case l: java.time.LocalDateTime => Date.from(l.atZone(ZoneId.systemDefault()).toInstant)
      case _ => o
    }

  }

  override def register() {
    log.debug("Hooking up java.time.LocalDateTime serializer.")

    BSON.addEncodingHook(encodeTypeLocalDateTime, transformer)
    super.register()
  }

  override def unregister() {
    log.debug("De-registering java.time.LocalDateTime serializer.")
    BSON.removeEncodingHooks(encodeTypeLocalDateTime)
    super.unregister()
  }
}

trait JavaLocalDateTimeDeserializer extends MongoConversionHelper {

  private val encodeType = classOf[java.util.Date]
  private val transformer = new Transformer {
    log.trace("Decoding JDK Dates .")

    def transform(o: AnyRef): AnyRef = o match {
      case jdkDate: java.util.Date =>
        jdkDate.toInstant.atZone(ZoneId.systemDefault()).toLocalDateTime
      case _ => o
    }
  }

  override def register() {
    log.debug("Hooking up java.time.LocalDateTime deserializer")

    BSON.addDecodingHook(encodeType, transformer)
    super.register()
  }

  override def unregister() {
    log.debug("De-registering java.time.LocalDateTime deserializer.")
    BSON.removeDecodingHooks(encodeType)
    super.unregister()
  }
}