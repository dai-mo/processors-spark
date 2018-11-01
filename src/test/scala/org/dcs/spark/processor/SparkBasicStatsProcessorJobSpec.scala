/*
 * Copyright (c) 2017-2018 brewlabs SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dcs.spark.processor

import org.apache.avro.generic.GenericRecordBuilder
import org.apache.spark.streaming.Seconds
import org.dcs.api.Constants
import org.dcs.api.processor.RelationshipType
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.{AvroSchemaStore, DataGenerator}
import org.dcs.spark.RunSpark
import org.dcs.spark.processor.SparkBasicStatsProcessorJob.{AverageKey, CountKey}
import org.dcs.spark.receiver.{IncrementalReceiver, TestReceiver}
import org.dcs.spark.sender.{AccSender, SparkSender}
import org.scalatest.time.{Millis, Span}

import scala.collection.JavaConverters._

class SparkBasicStatsProcessorJobSpec extends SparkStreamingSpec {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(20000, Millis)))

  private val windowDuration = Seconds(4)
  private val slideDuration = Seconds(1)

  "Spark Basic Statistics Processor" should "generate valid statistics" in {

    Given("streaming context is initialized")

    val schemaId = "org.dcs.core.processor.SparkBasicStatsProcessor"

    SparkSender.add(Constants.AccSenderClassName, AccSender(Constants.AccSenderClassName, SparkStreamingSpec.TestAcc, schemaId))

    val receiver = IncrementalReceiver(slideDuration.milliseconds + 1000)

    val schema = AvroSchemaStore.get(schemaId)

    val result = Array(RelationshipType.Success.id.getBytes(), schema.map(s =>
      new GenericRecordBuilder(s)
        .set(CountKey, 1)
        .set(AverageKey, Map("$.age" -> 1.0).asJava)
        .build().serToBytes(Some(s))
    ).get)


    RunSpark.launch(settings,
      receiver,
      Constants.AccSenderClassName,
      SparkBasicStatsProcessorJob(),
      TestReceiver.props,
      false)

    val records = DataGenerator.personsSer(5)
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


    receiver.add(records.tail.tail.head)

    When("third data record is added to the input stream")
    Then("basic stats computed eventually")
    eventually {
      val gr = SparkStreamingSpec.TestAcc.value.apply(1).deSerToGenericRecord(schema, schema)
      gr.get(CountKey) should equal(3)
    }

  }
  
}