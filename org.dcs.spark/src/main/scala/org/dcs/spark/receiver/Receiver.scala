package org.dcs.spark.receiver

import java.util.{List => JavaList, Map => JavaMap}

import org.dcs.api.Constants
import org.dcs.api.processor.CoreProperties

object Receiver {

  def get(props: JavaMap[String, String]) = props.get(CoreProperties.ReceiverKey) match {
    case Constants.TestReceiverClassName => {
      TestReceiver(1000, 100)
    }
    case _ => throw new IllegalArgumentException("No known receiver has been set (org.dcs.spark.receiver)")
  }

}