# One-time bootstrap: creates the S3 bucket + DynamoDB lock table that
# deploy/aws/terraform/'s own state will live in. Deliberately separate
# from that config and kept on LOCAL state - Terraform can't manage the
# backend it stores its own state in, so this one small piece has to stay
# out-of-band. Run this once, note the outputs, then follow "Continuous
# deployment" in ../README.md to point the main config at them.

terraform {
  required_version = ">= 1.6"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "project_name" {
  type    = string
  default = "event-ticketing-api"
}

provider "aws" {
  region = var.aws_region
}

data "aws_caller_identity" "current" {}

# Bucket names are globally unique across ALL AWS accounts - the account id
# suffix makes this one collision-free without you having to pick a name.
resource "aws_s3_bucket" "tfstate" {
  bucket = "${var.project_name}-tfstate-${data.aws_caller_identity.current.account_id}"
}

resource "aws_s3_bucket_versioning" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tfstate" {
  bucket = aws_s3_bucket.tfstate.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tfstate" {
  bucket                  = aws_s3_bucket.tfstate.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_dynamodb_table" "tflock" {
  name         = "${var.project_name}-tflock"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }
}

output "bucket_name" {
  value = aws_s3_bucket.tfstate.bucket
}

output "dynamodb_table_name" {
  value = aws_dynamodb_table.tflock.name
}

output "aws_region" {
  value = var.aws_region
}
