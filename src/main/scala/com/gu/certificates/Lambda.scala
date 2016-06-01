package com.gu.certificates

import com.amazonaws.services.elasticloadbalancing._
import com.amazonaws.services.elasticloadbalancing.model._
import com.amazonaws.auth._
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.identitymanagement._
import com.amazonaws.services.identitymanagement.model._
import com.amazonaws.regions.Regions

import scala.collection.JavaConverters._
import scala.util.Try
import java.time._

class Lambda(credsProvider: AWSCredentialsProvider = new DefaultAWSCredentialsProviderChain) {
  import Lambda._

  private val config = Config.load()
  private val elbClient: AmazonElasticLoadBalancing = new AmazonElasticLoadBalancingClient(credsProvider).withRegion(Regions.EU_WEST_1)
  private val iamClient: AmazonIdentityManagement = new AmazonIdentityManagementClient(credsProvider).withRegion(Regions.EU_WEST_1)

  def handle(event: Any) {
    val certificateUsages: Iterable[CertificateUsage] = flatMapLoadBalancers { elb =>
      val listeners = elb.getListenerDescriptions.asScala.map(_.getListener)
      for {
        listener <- listeners
        certId <- Option(listener.getSSLCertificateId)
        certName <- extractServerCertificateName(certId)
      } yield CertificateUsage(certName, elb.getLoadBalancerName)
    }

    val certificatesToCheck: Iterable[Certificate] = certificateUsages.groupBy(_.certName)
      .map { case (certName, usages) => Certificate(certName, usages.map(_.elbName)) }

    val nearExpiry: Iterable[Certificate] = certificatesToCheck.filter(cert => isCertificateNearExpiry(cert.certName))

    if (nearExpiry.nonEmpty) {
      println("Found the following server certs near expiry:")
      nearExpiry.foreach { cert =>
        println(s"${cert.certName} (used in these ELBs: ${cert.usedInElbs.mkString(", ")})")
      }
      sendPagerDutyAlert(nearExpiry)
    }

    println(s"Finished.")
  }

  /**
   * Runs the given function against all load balancers in the AWS account,
   * taking care of the AWS API's result pagination.
   */
  private def flatMapLoadBalancers[A](f: LoadBalancerDescription => Iterable[A]): Iterable[A] = {
    def rec(f: LoadBalancerDescription => Iterable[A], marker: Option[String], acc: Vector[A]): Vector[A] = {
      val request = new DescribeLoadBalancersRequest()
      marker.foreach(request.setMarker)
      val response = elbClient.describeLoadBalancers()
      val loadBalancers = response.getLoadBalancerDescriptions.asScala
      val resultsForThisPage = loadBalancers.flatMap(f)
      val updatedAcc = acc ++ resultsForThisPage

      val nextMarker = Option(response.getNextMarker).filter(_.nonEmpty)
      if (nextMarker.nonEmpty)
        rec(f, nextMarker, updatedAcc)
      else
        updatedAcc
    }

    rec(f, None, Vector.empty)
  }

  private def isCertificateNearExpiry(certName: String): Boolean = {
    val request = new GetServerCertificateRequest(certName)
    Try {
      val resp = iamClient.getServerCertificate(request)
      Option(resp.getServerCertificate.getServerCertificateMetadata.getExpiration())
        .map(javaUtilDate => isSoon(javaUtilDate, CloseToExpiry))
        .getOrElse(false)
    }.recover {
      case e: NoSuchEntityException =>
        println(s"WARN: Unknown certificate name [$certName]")
        false
      case other =>
        println(s"WARN: Exception encountered when looking up certificate [$certName], $other")
        false
    }.getOrElse(false)
  }

  private def sendPagerDutyAlert(nearExpiry: Iterable[Certificate]): Unit = {
    // TODO send API request to PagerDuty
  }

}

object Lambda {
  case class CertificateUsage(certName: String, elbName: String)
  case class Certificate(certName: String, usedInElbs: Iterable[String])

  val CloseToExpiry = Period.ofDays(15)

  // Note: This will NOT match ACM certs, because we don't need to check their expiry.
  val ServerCertificateId = """arn:aws:iam:.*:\d*:server-certificate/(.+)""".r.anchored

  def extractServerCertificateName(certId: String): Option[String] = certId match {
    case ServerCertificateId(certName) => Some(certName)
    case _ => None
  }

  def isSoon(javaUtilDate: java.util.Date, threshold: Period, now: LocalDateTime = LocalDateTime.now()): Boolean = {
    val targetDate = LocalDateTime.ofInstant(javaUtilDate.toInstant(), ZoneId.systemDefault())
    now.plus(threshold).isAfter(targetDate)
  }

  /**
   * Used for running the Lambda on a dev machine.
   *
   * Note: you need IAM credentials (i.e. not federated ones)
   * to make the API call to get a server certificate.
   */
  def main(args: Array[String]): Unit = {
    val credsProvider = new ProfileCredentialsProvider("default")
    new Lambda(credsProvider).handle("hello!")
  }

}
