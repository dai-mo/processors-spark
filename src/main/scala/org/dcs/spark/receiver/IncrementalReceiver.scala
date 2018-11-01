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

import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.receiver.Receiver

object IncrementalReceiver {
  var isNewRecord = false
  var record:Array[Byte] = Array()
  def apply(delay: Long) = new IncrementalReceiver(delay)
}

class IncrementalReceiver(delay: Long)
  extends Receiver[(Int, Array[Byte])](StorageLevel.MEMORY_AND_DISK_2)
    with SparkReceiver {

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

  override def stream(ssc: StreamingContext): DStream[(Int, Array[Byte])] = ssc.receiverStream(this)
}

