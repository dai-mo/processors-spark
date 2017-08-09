package org.dcs.spark.receiver

import java.util

import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.receiver.Receiver
import org.dcs.api.processor.CoreProperties
import org.dcs.commons.serde.AvroSchemaStore
import java.util.{Map => JavaMap}
import org.dcs.commons.serde.AvroImplicits._

object ListReceiver {
  def apply(data: List[Array[Byte]], delay: Long): ListReceiver = new ListReceiver(data, delay)
}

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

  def apply(props: JavaMap[String, String], delay: Long): ListReceiver = {
    val inputList = List(Person1.serToBytes(PersonSchema), Person2.serToBytes(PersonSchema))
    ListReceiver(inputList, delay)
  }
}

class ListReceiver(data: List[Array[Byte]], delay: Long)
  extends Receiver[Array[Byte]](StorageLevel.MEMORY_AND_DISK_2)
    with Serializable {

  override def onStart(): Unit = {
    new Thread(new Runnable {
      override def run(): Unit = {
        //while(!isStopped) {
          data.foreach(in => {
            store(in)
            Thread.sleep(delay)
          })
        //}
      }
    }).start()
  }

  override def onStop(): Unit = {}
}
