package org.dcs.spark.processor

import java.util.{Map => JavaMap}

import org.apache.avro.generic.GenericRecord
import org.apache.spark.streaming.dstream.DStream
import org.dcs.api.processor.RelationshipType._
import org.dcs.api.processor._
import org.dcs.spark.SparkStreamingBase


object SparkBasicStatsProcessor {
  val AverageKey = "average"

  def apply(): SparkBasicStatsProcessor = {
    new SparkBasicStatsProcessor()
  }

  def main(args: Array[String]): Unit = {
    SparkStreamingBase.main(new SparkBasicStatsProcessor(), args)
  }
}

/**
  * Created by cmathew on 09.11.16.
  */
class SparkBasicStatsProcessor extends SparkStreamingBase
  with FieldsToMap
  with Ingestion {

  import SparkBasicStatsProcessor._

  override def initState(): Unit = {

  }

//  override def execute(record: Option[GenericRecord],
//                       propertyValues: util.Map[String, String]):
//  List[Either[ErrorResponse, (String, GenericRecord)]] = {
//    val m = record.mappings(propertyValues)
//
//    val fieldsToAverage = m.get(AverageKey).asList[Double]
//
//    List()
//  }

  def execute(record: DStream[GenericRecord]): DStream[GenericRecord] = {
    record
  }


  override def _relationships(): Set[RemoteRelationship] = {
    Set(Success)
  }


  override def metadata(): MetaData =
    MetaData(description =  "Spark Basic Statistics Processor",
      tags = List("Spark", "Statistics"))



  override def _properties(): List[RemoteProperty] = List()

  override def fields: Set[ProcessorSchemaField] = Set(ProcessorSchemaField(AverageKey, PropertyType.Double))

  override def schemaId = "org.dcs.test.person"

//  def main(args: Array[String]): Unit = {
//
//    val slideDuration = Seconds(2)
//
//    val PersonSchemaId = "org.dcs.test.person"
//    val personSchema: Option[Schema] = AvroSchemaStore.get(PersonSchemaId)
//
//    val person1 = new GenericData.Record(personSchema.get)
//    person1.put("name", "Grace Hopper")
//    person1.put("age", 85)
//    person1.put("gender", "female")
//
//    val person2 = new GenericData.Record(personSchema.get)
//    person2.put("name", "Margaret Heafield Hamilton")
//    person2.put("age", 80)
//    person2.put("gender", "female")
//
//    val props: JavaMap[String, String] = new util.HashMap()
//    props.put(CoreProperties.ReadSchemaIdKey, PersonSchemaId)
//
//    val inputList = List(person1.serToBytes(personSchema), person2.serToBytes(personSchema))
//    val receiver = ListReceiver(inputList, slideDuration.milliseconds + 1000)
//
//    val sender = PrintSender(personSchema)
//
//    RunSpark.launch(SparkStreamingBase.localSettings(),
//      receiver,
//      sender,
//      this,
//      props,
//      false)
//  }
}

