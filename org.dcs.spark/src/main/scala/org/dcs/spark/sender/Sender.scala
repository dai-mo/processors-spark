package org.dcs.spark.sender

import java.util.{List => JavaList, Map => JavaMap}

import org.dcs.api.Constants
import org.dcs.api.processor.CoreProperties

object Sender {


  private var senders: Map[String, Sender[Array[Array[Byte]]]] = Map()


  def add(sender: Sender[Array[Array[Byte]]]): Sender[Array[Array[Byte]]] = {
    senders = senders + (sender.getClass.getName -> sender)
    sender
  }

  def get(senderClassName: Option[String]):Sender[Array[Array[Byte]]] =  {
    val scn = senderClassName.getOrElse(System.getProperty(CoreProperties.SenderKey))

    senders
      .getOrElse(scn,
        scn match {
          case Constants.TestSenderClassName => add(TestSender())
          case Constants.TestFileSenderClassName => add(TestFileSender("log/test.out"))
          case _ => throw new IllegalArgumentException("No known sender has been set (org.dcs.spark.sender)")
        })
  }

  def get(props: JavaMap[String, String]):Sender[Array[Array[Byte]]] = {
    get(Option(props.get(CoreProperties.SenderKey)))
  }
}

trait Sender[T] {
  def createNewConnection(): Sender[T]
  def send(record: T)
  def close()
}
