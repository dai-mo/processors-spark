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
