package org.dcs.spark.receiver

import org.apache.nifi.remote.client.{SiteToSiteClient, SiteToSiteClientConfig}
import org.apache.nifi.spark.NiFiReceiver
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream

object NifiSpark {
  val UrlKey = "nifiUrl"
  val PortName = "portName"
}

object NifiSparkReceiver {


  def apply(url: String, portName: String): NifiSparkReceiver = {
    val config: SiteToSiteClientConfig = new SiteToSiteClient.Builder()
      .url(url)
      .portName(portName)
      .buildConfig()
    new NifiSparkReceiver(config)
  }
}


class NifiSparkReceiver(config: SiteToSiteClientConfig)
  extends NiFiReceiver(config, StorageLevel.MEMORY_ONLY )
  with SparkReceiver {

  var count = 0

  override def stream(ssc: StreamingContext): DStream[(Int, Array[Byte])] = {
    ssc.receiverStream(this).map(dp => {
      count = count + 1
      (count, dp.getContent)
    })
  }
}
