package org.dcs.spark.sender

import org.dcs.spark.SparkStreamingBase.SenderKey
import org.dcs.spark.SparkUtils
import java.util.{List => JavaList, Map => JavaMap}

object Sender {
  // FIXME: Replace these with dynamic class names of sender types
  val TestSenderClassName = "org.dcs.spark.sender.TestSender"
  val TestFileSenderClassName = "org.dcs.spark.sender.TestFileSender"
  val AccSenderClassName = "org.dcs.spark.sender.AccSender"

  private var senders: Map[String, Sender[Array[Array[Byte]]]] = Map()


  def add(sender: Sender[Array[Array[Byte]]]): Sender[Array[Array[Byte]]] = {
    senders = senders + (sender.getClass.getName -> sender)
    sender
  }

  def get(senderClassName: Option[String]):Sender[Array[Array[Byte]]] =  {
    val scn = senderClassName.getOrElse(System.getProperty(SenderKey))

    senders
      .getOrElse(scn,
        scn match {
          case TestSenderClassName => add(TestSender())
          case TestFileSenderClassName => add(TestFileSender("log/test.out"))
          case _ => throw new IllegalArgumentException("No known sender has been set (org.dcs.spark.sender)")
        })
  }

  def get(props: JavaMap[String, String]):Sender[Array[Array[Byte]]] = {
    get(Option(props.get(SenderKey)))
  }
}

trait Sender[T] {
  def createNewConnection(): Sender[T]
  def send(record: T)
  def close()
}
