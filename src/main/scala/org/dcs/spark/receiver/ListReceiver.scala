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

import org.apache.avro.Schema
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.receiver.Receiver
import org.dcs.commons.serde.AvroImplicits._
import org.dcs.commons.serde.AvroSchemaStore
import org.dcs.spark.SparkUtils


object ListReceiver {
  def apply(data: List[Array[Byte]], schemaId: String, delay: Long): ListReceiver =
    new ListReceiver(data, schemaId, delay)
}

class ListReceiver(data: List[Array[Byte]], schemaId: String, delay: Long)
  extends Receiver[(Int, Array[Byte])](StorageLevel.MEMORY_AND_DISK_2)
  with SparkReceiver {

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

  override def stream(ssc: StreamingContext): DStream[(Int, Array[Byte])] = ssc.receiverStream(this)
}

