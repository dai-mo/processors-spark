package org.dcs.spark.receiver

import java.util.{List => JavaList, Map => JavaMap}

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.dcs.api.Constants
import org.dcs.api.processor.CoreProperties
import org.dcs.api.util.WithArgs

object SparkReceiver {

  def get(props: JavaMap[String, String]): SparkReceiver = {
    val receiver = props.get(CoreProperties.ReceiverKey)
    if(receiver == null)
      throw new IllegalArgumentException("No receiver has been set (org.dcs.spark.receiver)")

    val wa = WithArgs(receiver)
     wa.target match {
      case Constants.TestReceiverClassName =>
        TestReceiver(wa.get(TestReceiver.DelayKey).toLong, wa.get(TestReceiver.NbOfRecordsKey).toInt)
      case Constants.NifiSparkReceiverClassName =>
        NifiSparkReceiver(wa.get(NifiSpark.UrlKey).toString, wa.get(NifiSpark.PortName))
      case _ => throw new IllegalArgumentException("No known receiver has been set (org.dcs.spark.receiver)")
    }
  }
}

trait SparkReceiver extends Serializable {
  def stream(ssc: StreamingContext): DStream[(Int, Array[Byte])]
}