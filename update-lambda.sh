#!/usr/bin/env bash
#
# Builds and deploys the Lambda

set -e

my_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

sbt assembly

jar_file=$(echo $my_dir/target/scala-2.11/certificate-expiry-checker-assembly*.jar)

aws lambda update-function-code \
  --function-name certificate-expiry-checker \
  --region eu-west-1 \
  --profile capi \
  --zip-file fileb://$jar_file
