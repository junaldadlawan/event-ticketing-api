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

  # No backend configured - defaults to local state (terraform.tfstate in
  # this directory). For anything beyond a solo experiment, switch to a
  # remote backend before the first `apply`, e.g.:
  #
  # backend "s3" {
  #   bucket         = "<your-unique-bucket-name>"
  #   key            = "event-ticketing-api/terraform.tfstate"
  #   region         = "us-east-1"
  #   dynamodb_table = "<your-lock-table>"
  #   encrypt        = true
  # }
}

provider "aws" {
  region = var.aws_region
}
