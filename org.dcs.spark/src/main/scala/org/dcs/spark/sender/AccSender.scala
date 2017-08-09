package org.dcs.spark.sender

import org.apache.spark.util.AccumulatorV2
import org.dcs.spark.Sender

class ResultAccumulator extends AccumulatorV2[Array[Array[Byte]], Array[Array[Byte]]] {

  private var records: Array[Array[Byte]] = Array[Array[Byte]]()

  override def isZero: Boolean = records.length == 0

  override def copy(): AccumulatorV2[Array[Array[Byte]], Array[Array[Byte]]] = {
    val ta = new ResultAccumulator()
    records.foreach(r => ta.records = ta.records :+ r)
    ta
  }

  override def reset(): Unit = records = Array[Array[Byte]]()

  override def add(v: Array[Array[Byte]]): Unit = records = records ++ v

  override def merge(other: AccumulatorV2[Array[Array[Byte]], Array[Array[Byte]]]): Unit =
    records = records ++ other.asInstanceOf[ResultAccumulator].records

  override def value: Array[Array[Byte]] = records

}
object AccSender {
  def apply(resultAcc: ResultAccumulator): AccSender = new AccSender(resultAcc)
}

class AccSender(resultAcc: ResultAccumulator) extends Sender[Array[Array[Byte]]] with Serializable {

  override def createNewConnection(): Sender[Array[Array[Byte]]] = this

  override def send(record: Array[Array[Byte]]): Unit = {
    resultAcc.add(record)
  }

  override def close(): Unit = {

  }

  def result: Array[Array[Byte]] = resultAcc.value
}
