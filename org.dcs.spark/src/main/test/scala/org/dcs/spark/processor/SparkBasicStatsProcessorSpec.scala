package org.dcs.spark.processor

import java.util
import java.util.{Map => JavaMap}

import com.holdenkarau.spark.testing.{SharedSparkContext, StreamingSuiteBase}
import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecord}
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.Seconds
import org.dcs.api.processor.{CoreProperties, RelationshipType}
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.receiver.{ListReceiver, TestReceiver}
import org.dcs.spark.sender.AccSender
import org.dcs.spark.{RunSpark, SparkStreamingBase}
import org.scalatest.time.{Millis, Span}

import scala.collection.mutable

class SparkBasicStatsProcessorSpec extends SparkStreamingSpec {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(20000, Millis)))

  private val windowDuration = Seconds(4)
  private val slideDuration = Seconds(2)

  "Spark Basic Statistics Processor" should "generate valid statistics" in {

    Given("streaming context is initialized")

    val props: JavaMap[String, String] = new util.HashMap()
    props.put(CoreProperties.ReadSchemaIdKey, TestReceiver.PersonSchemaId)

    val receiver = TestReceiver(props, slideDuration.milliseconds + 1000)

    val expected1 = Array(RelationshipType.Success.id.getBytes, TestReceiver.Person1.serToBytes(TestReceiver.PersonSchema))
    val expected2 = Array(RelationshipType.Success.id.getBytes, TestReceiver.Person2.serToBytes(TestReceiver.PersonSchema))

    val sender = AccSender(SparkStreamingSpec.TestAcc)

    RunSpark.launch(settings,
      receiver,
      sender,
      SparkBasicStatsProcessor(),
      props,
      false)

    When("first data record is added to the input stream")
    Then("basic stats computed eventually")
    eventually {
      sender.result should equal(expected1)
    }


    When("second data record is added to the input stream")
    Then("basic stats computed eventually")
    eventually {
      sender.result should equal(expected1 ++ expected2)
    }
  }
}