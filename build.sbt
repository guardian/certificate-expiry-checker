name := "certificate-expiry-checker"
organization := "com.gu"
description := "Lambda to find server certificates that are close to expiry"
scalaVersion := "2.11.8"
scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked")

val AwsSdkVersion = "1.11.5"

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "com.amazonaws" % "aws-java-sdk-s3" % AwsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-elasticloadbalancing" % AwsSdkVersion,
  "com.amazonaws" % "aws-java-sdk-iam" % AwsSdkVersion,
  "org.scalatest" %% "scalatest" % "2.2.6" % Test
)
