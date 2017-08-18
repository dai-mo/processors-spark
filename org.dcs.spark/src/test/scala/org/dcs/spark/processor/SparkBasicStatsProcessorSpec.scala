package org.dcs.spark.processor

import java.util.{Map => JavaMap}

import org.apache.avro.generic.GenericRecordBuilder
import org.apache.spark.streaming.Seconds
import org.dcs.api.processor.RelationshipType
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.RunSpark
import org.dcs.spark.processor.SparkBasicStatsProcessor.{AverageKey, CountKey}
import org.dcs.spark.receiver.{IncrementalReceiver, TestReceiver}
import org.dcs.spark.sender.AccSender
import org.scalatest.time.{Millis, Span}

import scala.collection.JavaConverters._

class SparkBasicStatsProcessorSpec extends SparkStreamingSpec {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(20000, Millis)))

  private val windowDuration = Seconds(4)
  private val slideDuration = Seconds(1)

  "Spark Basic Statistics Processor" should "generate valid statistics" in {

    Given("streaming context is initialized")



    val receiver = IncrementalReceiver(slideDuration.milliseconds + 1000)

    val sender = AccSender(SparkStreamingSpec.TestAcc)

    val schema = AvroSchemaStore.get("org.dcs.spark.processor.SparkBasicStatsProcessor")
    val result = Array(RelationshipType.Success.id.getBytes(), schema.map(s =>
      new GenericRecordBuilder(s)
        .set(CountKey, 1)
        .set(AverageKey, Map("$.age" -> 1.0).asJava)
        .build().serToBytes(Some(s))
    ).get)

    RunSpark.launch(settings,
      receiver,
      sender,
      SparkBasicStatsProcessor(),
      TestReceiver.props,
      false)

    val records = TestReceiver.plist(5)
    receiver.add(records.head)

    When("first data record is added to the input stream")
    Then("basic stats computed eventually")
    eventually {
      val gr = SparkStreamingSpec.TestAcc.value.apply(1).deSerToGenericRecord(schema, schema)
      SparkStreamingSpec.TestAcc.value should equal(result)
    }

    receiver.add(records.tail.head)

    When("second data record is added to the input stream")
    Then("basic stats computed eventually")
    eventually {
      val gr = SparkStreamingSpec.TestAcc.value.apply(1).deSerToGenericRecord(schema, schema)
      gr.get(CountKey) should equal(2)
    }

  }
}