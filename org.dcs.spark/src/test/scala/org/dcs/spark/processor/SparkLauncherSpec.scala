package org.dcs.spark.processor

import java.util.{Map => JavaMap}

import org.dcs.api.processor.{CoreProperties, RemoteProperty}
import org.dcs.commons.serde.JsonSerializerImplicits._
import org.dcs.spark.SparkStreamingBase
import org.dcs.spark.receiver.TestReceiver
import org.dcs.spark.receiver.TestReceiver.PersonSchemaId
import org.scalatest.Ignore

import scala.collection.JavaConverters._

//@Ignore
class SparkLauncherSpec extends SparkUnitFlatSpec {

  // NOTE: The SPARK_HOME environmental variable must be set here for this to work
  "Spark Basic Statistics Processor" should "launch correctly" in {

    val processor = SparkBasicStatsProcessor()
    processor.initState()

    val receiverProperty = RemoteProperty(SparkStreamingBase.ReceiverKey, SparkStreamingBase.ReceiverKey, "")
    val senderProperty = RemoteProperty(SparkStreamingBase.SenderKey, SparkStreamingBase.SenderKey, "")
    val fieldsToMapProperty = CoreProperties.fieldsToMapProperty(SparkBasicStatsProcessor().fields)
    val readSchemaIdProperty = CoreProperties.readSchemaIdProperty()

    val propertyValues = Map(
      receiverProperty -> "org.dcs.spark.receiver.TestReceiver",
      senderProperty -> "org.dcs.spark.sender.TestFileSender",
      readSchemaIdProperty -> PersonSchemaId,
      fieldsToMapProperty -> TestReceiver.FieldsToMap.toJson
    ).asJava

    processor.onSchedule(propertyValues)

    Thread.sleep(20000)
    processor.onRemove(propertyValues)

  }
}
