# Lets GitHub Actions assume an AWS role without any long-lived AWS access
# key stored as a GitHub secret - the workflow exchanges a short-lived OIDC
# token (minted by GitHub, scoped to this one repo+branch) for temporary
# AWS credentials at run time. See .github/workflows/deploy.yml and
# "Continuous deployment" in README.md.
#
# GOTCHA: an AWS account can only have ONE OIDC provider per unique URL. If
# this account already has a github-actions OIDC provider (from another
# project), creating this resource will fail with EntityAlreadyExists -
# either `terraform import` the existing one
# (`aws_iam_openid_connect_provider.github`) instead of letting this create
# a duplicate, or delete this resource block and hardcode
# `aws_iam_role.github_actions`'s trust policy to reference the existing
# provider's ARN directly.

variable "github_repo" {
  description = "GitHub \"owner/repo\" allowed to assume the deploy role - scopes the OIDC trust policy."
  type        = string
  default     = "junaldadlawan/event-ticketing-api"
}

variable "github_deploy_branch" {
  description = "Branch the deploy role's trust policy is restricted to - pushes from any other branch/PR/fork cannot assume it."
  type        = string
  default     = "main"
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  # GitHub's current + previous root CA thumbprints (both accepted at
  # once is the standard pattern AWS/GitHub's own docs recommend, since
  # GitHub has rotated this before) - re-verify against AWS's GitHub OIDC
  # guide if this resource's `apply` ever fails token validation.
  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]

  tags = local.common_tags
}

data "aws_iam_policy_document" "github_actions_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]
    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repo}:ref:refs/heads/${var.github_deploy_branch}"]
    }
  }
}

resource "aws_iam_role" "github_actions" {
  name               = "${local.name}-github-actions-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_actions_assume.json
  tags               = local.common_tags
}

# Broad READ access - `terraform plan`/`apply` refreshes every resource in
# state (VPC, RDS, ALB, IAM, ...) even on a run that only changes the
# container image, so it needs to Describe/List all of it. No write access
# comes from this policy.
resource "aws_iam_role_policy_attachment" "github_actions_read" {
  role       = aws_iam_role.github_actions.name
  policy_arn = "arn:aws:iam::aws:policy/ReadOnlyAccess"
}

# Narrow WRITE access - only what a routine "new image -> redeploy" run
# actually changes. Deliberately does NOT grant permission to create/
# destroy the VPC/RDS/ALB/etc. - a compromised or buggy workflow run can
# push a bad image and roll the ECS service back and forth, not tear down
# or rebuild the rest of the stack.
data "aws_iam_policy_document" "github_actions_deploy" {
  statement {
    sid = "EcrPush"
    actions = [
      "ecr:GetAuthorizationToken",
    ]
    resources = ["*"] # GetAuthorizationToken is account-level, doesn't accept a resource ARN
  }
  statement {
    sid = "EcrPushToThisRepoOnly"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "ecr:PutImage",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
    ]
    resources = [aws_ecr_repository.app.arn]
  }
  statement {
    sid = "EcsRedeploy"
    actions = [
      "ecs:RegisterTaskDefinition",
      "ecs:DeregisterTaskDefinition",
      "ecs:UpdateService",
      "ecs:TagResource",
    ]
    resources = ["*"] # RegisterTaskDefinition doesn't accept a resource-level ARN; scoped in practice by the narrower PassRole statement below
  }
  statement {
    sid       = "PassOnlyThisStacksRoles"
    actions   = ["iam:PassRole"]
    resources = [aws_iam_role.ecs_execution.arn, aws_iam_role.ecs_task.arn]
  }
  statement {
    sid       = "TerraformStateBucket"
    actions   = ["s3:GetObject", "s3:PutObject", "s3:ListBucket"]
    resources = ["arn:aws:s3:::${var.tfstate_bucket}", "arn:aws:s3:::${var.tfstate_bucket}/*"]
  }
  statement {
    sid       = "TerraformStateLock"
    actions   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:DeleteItem"]
    resources = ["arn:aws:dynamodb:${var.aws_region}:*:table/${var.tfstate_dynamodb_table}"]
  }
}

resource "aws_iam_role_policy" "github_actions_deploy" {
  name   = "${local.name}-deploy"
  role   = aws_iam_role.github_actions.id
  policy = data.aws_iam_policy_document.github_actions_deploy.json
}

variable "tfstate_bucket" {
  description = "S3 bucket name from deploy/aws/terraform-bootstrap/'s `bucket_name` output - only needed once you've migrated to the S3 backend."
  type        = string
  default     = ""
}

variable "tfstate_dynamodb_table" {
  description = "DynamoDB table name from deploy/aws/terraform-bootstrap/'s `dynamodb_table_name` output."
  type        = string
  default     = ""
}

output "github_actions_role_arn" {
  description = "Copy this into the GitHub repo's Actions variable AWS_DEPLOY_ROLE_ARN - see README.md."
  value       = aws_iam_role.github_actions.arn
}
