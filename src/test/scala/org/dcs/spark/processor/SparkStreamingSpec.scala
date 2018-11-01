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
      SparkStreamingBase.settings(Map(), //Map("spark.streaming.clock" -> "org.apache.spark.streaming.util.ManualClock"),
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










