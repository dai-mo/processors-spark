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
