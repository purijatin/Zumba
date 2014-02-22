package io.zumba.config

import scala.concurrent.duration.FiniteDuration

trait Config {

}

trait HelperConfig <: {
  /**
   * Number of times it should retry to validate if response is achieved.
   */
  def retryTimes: Int

  /**
   * time after which a request is sent of itself to retry again
   */
  def retryAfter: FiniteDuration
}