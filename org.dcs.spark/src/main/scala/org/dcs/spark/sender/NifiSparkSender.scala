package org.dcs.spark.sender

import java.io.ByteArrayInputStream
import java.util

import org.apache.nifi.remote.{Transaction, TransferDirection}
import org.apache.nifi.remote.client.{SiteToSiteClient, SiteToSiteClientConfig}
import org.apache.nifi.remote.protocol.{DataPacket, SiteToSiteTransportProtocol}
import org.apache.nifi.remote.util.StandardDataPacket
import org.dcs.spark.SparkUtils

object NifiSparkSender {

  def apply(wa: String, url: String, portName: String): NifiSparkSender = {
    new NifiSparkSender(wa, url, portName)
  }
}

class NifiSparkSender(wa: String, url: String, portName: String) extends SparkSender[Array[Array[Byte]]]  {

  var client: SiteToSiteClient = _

  override def key(): String = wa

  override def createNewConnection(): SparkSender[Array[Array[Byte]]] = {
    client = new SiteToSiteClient.Builder()
      .url(url)
      .portName(portName)
      .requestBatchCount(5)
      .build

    this
  }

  override def send(record: Array[Array[Byte]]): Unit = {
    val transaction = client.createTransaction(TransferDirection.SEND)
    if (transaction == null) throw new IllegalStateException("Unable to create a NiFi Transaction to send data")
    val data = record(1)
    SparkUtils.appLogger.warn("SparkSenderDataStream ===>" + data)
    SparkUtils.appLogger.warn("SparkSenderDataStreamSize ===>" + data.length)
    val bais = new ByteArrayInputStream(data)
    val packet = new StandardDataPacket(new util.HashMap(), bais, data.length)
    transaction.send(data, new util.HashMap())
    transaction.confirm()
    transaction.complete()
  }

  override def close(): Unit = {
    client.close()
  }
}
