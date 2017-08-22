package org.dcs.spark.receiver

import java.util

import org.apache.avro.Schema
import org.apache.avro.generic.{GenericData, GenericRecordBuilder}
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver
import org.dcs.api.processor.{CoreProperties, ProcessorSchemaField, PropertyType}
import org.dcs.commons.serde.{AvroSchemaStore, JsonPath}
import java.util.{Map => JavaMap}

import org.dcs.commons.serde.AvroImplicits._
import org.dcs.spark.processor.SparkBasicStatsProcessor.AverageKey
import org.dcs.commons.serde.JsonSerializerImplicits._
import org.dcs.spark.SparkUtils



object TestReceiver {
  val PersonSchemaId = "org.dcs.test.person"

  val PersonSchema: Option[Schema] = AvroSchemaStore.get(PersonSchemaId)

  val Person1 = new GenericData.Record(PersonSchema.get)
  Person1.put("name", "Grace Hopper")
  Person1.put("age", 85)
  Person1.put("gender", "female")

  val Person2 = new GenericData.Record(PersonSchema.get)
  Person2.put("name", "Margaret Heafield Hamilton")
  Person2.put("age", 80)
  Person2.put("gender", "female")

  val FieldsToMap = Set(ProcessorSchemaField(AverageKey, PropertyType.Double, JsonPath.Root + JsonPath.Sep + "age"))

  val props: JavaMap[String, String] = new util.HashMap()
  props.put(CoreProperties.ReadSchemaIdKey, TestReceiver.PersonSchemaId)
  props.put(CoreProperties.FieldsToMapKey, FieldsToMap.toJson)


  def plist(noOfRecords: Int) = Range.inclusive(1,noOfRecords).map(i => new GenericRecordBuilder(PersonSchema.get)
    .set("name", "Person" + i)
    .set("age", i)
    .set("gender", if(i % 2 == 0) "female" else "male")
    .build()
    .serToBytes(PersonSchema)).toList

  def apply(delay: Long, noOfRecords: Int): ListReceiver = {
    val inputList = plist(noOfRecords)
    ListReceiver(inputList, PersonSchemaId, delay)
  }
}

object ListReceiver {
  def apply(data: List[Array[Byte]], schemaId: String, delay: Long): ListReceiver =
    new ListReceiver(data, schemaId, delay)
}

class ListReceiver(data: List[Array[Byte]], schemaId: String, delay: Long)
  extends Receiver[(Int, Array[Byte])](StorageLevel.MEMORY_AND_DISK_2)
    with Serializable {

  val pauseLock: String = "PauseLock"

  override def onStart(): Unit = {
    new Thread(new Runnable {
      override def run(): Unit = {
        var count = 0
        data.foreach(in => {
          store((count, in))
          count = count + 1
          val schema: Option[Schema] = AvroSchemaStore.get(schemaId)
          val gr = in.deSerToGenericRecord(schema, schema)
          SparkUtils.appLogger.warn("Receiver ===>" + gr)
          Thread.sleep(delay)
        })
      }
    }).start()
  }

  override def onStop(): Unit = {}
}

object IncrementalReceiver {
  var isNewRecord = false
  var record:Array[Byte] = Array()
  def apply(delay: Long) = new IncrementalReceiver(delay)
}

class IncrementalReceiver(delay: Long)
  extends Receiver[(Int, Array[Byte])](StorageLevel.MEMORY_AND_DISK_2)
    with Serializable {

  import IncrementalReceiver._

  var count = 0

  override def onStart(): Unit = {
    new Thread(new Runnable {
      override def run(): Unit = {
        while(!isStopped) {

          if(isNewRecord && record.nonEmpty) {
            store((count, record))
            count = count + 1
            record = Array()
            isNewRecord = false
          }

          Thread.sleep(delay)

        }
      }
    }).start()
  }

  def add(rec: Array[Byte]) = {
    record = rec
    isNewRecord = true
  }

  override def onStop(): Unit = {}
}
