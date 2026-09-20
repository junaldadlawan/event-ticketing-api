terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # No backend block yet -> `terraform init` defaults to local state
  # (terraform.tfstate in this directory). Fine for a first solo `apply`.
  #
  # Before CI (or a second machine) ever runs `apply` against this same
  # stack, uncomment the block below (a deliberately EMPTY/"partial" S3
  # backend - the actual bucket/table are account-specific, so they're
  # supplied at init time via -backend-config flags, not hardcoded here)
  # and run, using the outputs from deploy/aws/terraform-bootstrap/:
  #
  #   terraform init -migrate-state \
  #     -backend-config="bucket=<bucket_name output>" \
  #     -backend-config="dynamodb_table=<dynamodb_table_name output>" \
  #     -backend-config="region=<aws_region output>" \
  #     -backend-config="key=event-ticketing-api/terraform.tfstate"
  #
  # See "Continuous deployment" in ../README.md - CI runs this same init
  # (with the same flags, no -migrate-state) on every workflow run.
  #
  # backend "s3" {}
}

provider "aws" {
  region = var.aws_region
}
