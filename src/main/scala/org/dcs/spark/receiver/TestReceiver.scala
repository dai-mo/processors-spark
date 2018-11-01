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

package org.dcs.spark.receiver

import java.util.{HashMap, Map => JavaMap}

import org.dcs.api.processor.{CoreProperties, ProcessorSchemaField, PropertyType}
import org.dcs.commons.serde.JsonSerializerImplicits._
import org.dcs.commons.serde.{DataGenerator, JsonPath}
import org.dcs.spark.processor.SparkBasicStatsProcessorJob.AverageKey
import org.dcs.commons.serde.AvroImplicits._

import scala.collection.JavaConverters._

object TestReceiver {
  val DelayKey = "delay"
  val NbOfRecordsKey = "nbOfRecords"

  val SparkBasicStatsProcessorClassName = "org.dcs.core.processor.SparkBasicStatsProcessor"

  val FieldsToMap = Set(ProcessorSchemaField(AverageKey, PropertyType.Double, JsonPath.Root + JsonPath.Sep + "age"))

  val props: JavaMap[String, String] = new HashMap[String, String]().asScala.asJava
  props.put(CoreProperties.ReadSchemaIdKey, DataGenerator.PersonSchemaId)
  props.put(CoreProperties.FieldsToMapKey, FieldsToMap.toJson)
  props.put(CoreProperties.ProcessorClassKey, SparkBasicStatsProcessorClassName)
  props.put(CoreProperties.SchemaIdKey, SparkBasicStatsProcessorClassName)


  def apply(delay: Long, noOfRecords: Int): ListReceiver = {
    val inputList = DataGenerator.personsSer(noOfRecords)
    ListReceiver(inputList, DataGenerator.PersonSchemaId, delay)
  }
}
