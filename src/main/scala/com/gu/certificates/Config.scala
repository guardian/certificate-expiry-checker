package com.gu.certificates

import java.util.Properties
import com.amazonaws.services.s3.AmazonS3Client
import scala.util.Try

case class Config(pagerDutyApiKey: String)

object Config {

  val s3 = new AmazonS3Client()

  def load(): Config = {
    println("Loading config...")
    // TODO set up S3 bucket. It will need to be readable by any account that wants to run this Lambda.
    //val properties = loadProperties("ssl-expiry-checker-config", "ssl-expiry-checker.properties") getOrElse sys.error("Could not load config file from s3. This lambda will not run.")

    val pagerDutyApiKey = "foo" //getMandatoryConfig(properties, "pagerDuty.apiKey")
    println(s"PagerDuty API key = ${pagerDutyApiKey.take(3)}...")

    Config(pagerDutyApiKey)
  }

  private def loadProperties(bucket: String, key: String): Try[Properties] = {
    val inputStream = s3.getObject(bucket, key).getObjectContent()
    val properties: Properties = new Properties()
    val result = Try(properties.load(inputStream)).map(_ => properties)
    inputStream.close()
    result
  }

  private def getMandatoryConfig(config: Properties, key: String) =
    Option(config.getProperty(key)) getOrElse sys.error(s"''$key' property missing.")

}

