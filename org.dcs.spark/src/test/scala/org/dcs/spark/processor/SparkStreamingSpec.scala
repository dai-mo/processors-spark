package org.dcs.spark.processor

import java.nio.file.Files

import org.apache.spark.streaming.{ClockWrapper, Duration, Seconds}
import org.dcs.spark.sender.ResultAccumulator
import org.dcs.spark.{SparkStreamingBase, SparkStreamingSettings}

object SparkStreamingSpec {
  val TestAcc = new ResultAccumulator
}

class SparkStreamingSpec extends SparkUnitFlatSpec {

  var settings: SparkStreamingSettings = _


  def batchDuration: Duration = Seconds(1)

  before {
    settings =
      SparkStreamingBase.localSettings(Map(), //Map("spark.streaming.clock" -> "org.apache.spark.streaming.util.ManualClock"),
        batchDuration)
    settings.ssc.sparkContext.register(SparkStreamingSpec.TestAcc, "ResultAccumulator")
  }

  def checkpointDir: String = Files.createTempDirectory(this.getClass.getSimpleName).toUri.toString

  def advanceClock(timeToAdd: Duration): Unit = {
    ClockWrapper.advance(settings.ssc, timeToAdd)
  }

  def advanceClockOneBatch(): Unit = {
    advanceClock(Duration(batchDuration.milliseconds))
  }

  after {
    if(settings.ssc != null)
      settings.ssc.stop(stopSparkContext = false, stopGracefully = false)
  }

  def stop() = {
    if(settings.ssc != null)
      settings.ssc.stop()
  }
}










