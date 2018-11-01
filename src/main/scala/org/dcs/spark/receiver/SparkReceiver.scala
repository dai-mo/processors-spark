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

import java.util.{List => JavaList, Map => JavaMap}

import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import org.dcs.api.Constants
import org.dcs.api.processor.ExternalProcessorProperties
import org.dcs.api.util.WithArgs

object SparkReceiver {

  def get(props: JavaMap[String, String]): SparkReceiver = {
    val receiver = props.get(ExternalProcessorProperties.ReceiverKey)
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