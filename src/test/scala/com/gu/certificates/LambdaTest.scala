package com.gu.certificates

import org.scalatest._
import java.util._
import java.time._

class LambdaTest extends FlatSpec with Matchers {

  it should "extract a server certificate name from its ARN" in {
    val arn = "arn:aws:iam::12345:server-certificate/yolo-123"
    Lambda.extractServerCertificateName(arn) should be(Some("yolo-123"))
  }

  it should "recognise that a certificate is about to expire" in {
    // 2016-06-04T12:34:56Z
    val expiry = {
      val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
      cal.set(2016, 5, 4, 12, 34, 56)
      cal.getTime()
    }

    // 2016-06-01T17:15:00+01:00
    val now = LocalDateTime.of(2016, 6, 1, 17, 15, 0)
      .atOffset(ZoneOffset.ofHours(1))
      .toLocalDateTime()

    Lambda.isSoon(expiry, Period.ofDays(3), now) should be(true)
    Lambda.isSoon(expiry, Period.ofDays(2), now) should be(false)
  }

}
