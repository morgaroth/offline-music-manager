package io.morgaroth.media.library.storage

import com.mongodb.casbah.commons.conversions.MongoConversionHelper
import org.bson.{BSON, Transformer}

import java.time.ZoneId
import java.util.Date


object JavaZonedDateTimeHelpers extends JavaZonedDateTimeSerializer with JavaZonedDateTimeDeserializer

trait JavaZonedDateTimeSerializer extends MongoConversionHelper {

  private val encodeTypeZonedDateTime = classOf[java.time.ZonedDateTime]

  private val transformer = new Transformer {
    log.trace("Encoding a java.time.v.")

    def transform(o: AnyRef): AnyRef = o match {
      case l: java.time.ZonedDateTime => Date.from(l.toInstant)
      case another => another
    }

  }

  override def register() {
    log.debug("Hooking up java.time.ZonedDateTime serializer.")

    BSON.addEncodingHook(encodeTypeZonedDateTime, transformer)
    super.register()
  }

  override def unregister() {
    log.debug("De-registering java.time.ZonedDateTime serializer.")
    BSON.removeEncodingHooks(encodeTypeZonedDateTime)
    super.unregister()
  }
}

trait JavaZonedDateTimeDeserializer extends MongoConversionHelper {

  private val encodeType = classOf[java.util.Date]
  private val transformer = new Transformer {
    log.trace("Decoding JDK Dates .")

    def transform(o: AnyRef): AnyRef = o match {
      case jdkDate: java.util.Date =>
        jdkDate.toInstant.atZone(ZoneId.systemDefault())
      case another => another
    }
  }

  override def register() {
    log.debug("Hooking up java.time.ZonedDateTime deserializer")

    BSON.addDecodingHook(encodeType, transformer)
    super.register()
  }

  override def unregister() {
    log.debug("De-registering java.time.ZonedDateTime deserializer.")
    BSON.removeDecodingHooks(encodeType)
    super.unregister()
  }
}