package org.dcs.spark.processor

import org.scalatest._
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar

trait SparkUnitSpec extends Matchers
  with OptionValues
  with Inside
  with Inspectors
  with MockitoSugar
  with Eventually

// FIXME: Currently the only way to use the mockito
// inject mock mechanism to test the CDI
// part is to run the test as JUnit tests
// since there is no mechanism to run this
// as a scala test.
// ScalaMock could be an option once the 
// issue https://github.com/paulbutcher/ScalaMock/issues/100
// is resolved
abstract class JUnitSpec extends JUnitSuite
  with Matchers
  with OptionValues
  with Inside
  with Inspectors
  with MockitoSugar

abstract class SparkUnitFlatSpec extends FlatSpec
  with SparkUnitSpec
  with BeforeAndAfter
  with GivenWhenThen

abstract class SparkUnitWordSpec extends WordSpec
  with SparkUnitSpec


