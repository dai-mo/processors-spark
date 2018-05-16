package org.dcs.spark.processor

import com.holdenkarau.spark.testing.{SharedSparkContext, StreamingSuiteBase}
import org.apache.spark.streaming.dstream.DStream

class SparkExampleSpec extends SparkUnitFlatSpec
  with SharedSparkContext
  with StreamingSuiteBase{

  "Example RDD Test" should "work" in  {
    val input = List("hi", "hi cloudera", "bye")
    val expected = List(List("hi"), List("hi", "cloudera"), List("bye"))
    val rdd = sc.parallelize(input)

    assert(rdd.count === input.length)
  }

  "Example DStream Test" should "work" in {
    val input = List(List("hi"), List("hi holden"), List("bye"))
    val expected = List(List("hi"), List("hi", "holden"), List("bye"))
    testOperation[String, String](input, tokenize _, expected, ordered = false)
  }

  // This is the sample operation we are testing
  def tokenize(f: DStream[String]): DStream[String] = {
    f.flatMap(_.split(" "))
  }
}