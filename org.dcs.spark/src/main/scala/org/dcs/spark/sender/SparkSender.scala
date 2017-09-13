package org.dcs.spark.sender

import java.util.{List => JavaList, Map => JavaMap}

import org.dcs.api.Constants
import org.dcs.api.processor.CoreProperties
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
    val sender = props.get(CoreProperties.SenderKey)
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
