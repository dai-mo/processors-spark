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

package org.dcs.spark.sender

import java.util.{List => JavaList, Map => JavaMap}

import org.dcs.api.Constants
import org.dcs.api.processor.ExternalProcessorProperties
import org.dcs.api.util.WithArgs
import org.dcs.spark.SparkUtils
import org.dcs.spark.receiver.NifiSpark

object SparkSender {


  private var senders: Map[String, SparkSender[Array[Array[Byte]]]] = Map()


  def add(key: String, sender: SparkSender[Array[Array[Byte]]]): SparkSender[Array[Array[Byte]]] = {
    senders = senders + (key -> sender)
    sender
  }

  def get(sender: String):SparkSender[Array[Array[Byte]]] =  {
    senders.getOrElse(sender, {
      val wa = WithArgs(sender)
      SparkUtils.appLogger.warn("SparkSender ===>" + sender)
      SparkUtils.appLogger.warn("SparkSenderTarget ===>" + wa.target)
      wa.target match {
        case Constants.TestSenderClassName => add(sender, TestSender(sender))
        case Constants.TestFileSenderClassName => add(sender, TestFileSender(sender, "log/test.out"))
        case Constants.NifiSparkSenderClassName => add(sender, NifiSparkSender(sender, wa.get(NifiSpark.UrlKey).toString, wa.get(NifiSpark.PortName)))
        case _ => throw new IllegalArgumentException("No known sender has been set (org.dcs.spark.sender)")
      }
    })
  }

  def get(props: JavaMap[String, String]): SparkSender[Array[Array[Byte]]] = {
    val sender = props.get(ExternalProcessorProperties.SenderKey)
    if(sender == null)
      throw new IllegalArgumentException("No sender has been set (org.dcs.spark.receiver)")
    get(sender)
  }
}

trait SparkSender[T] {
  def createNewConnection(): SparkSender[T]
  def send(record: T)
  def close()
  def key(): String
}
