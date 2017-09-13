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

