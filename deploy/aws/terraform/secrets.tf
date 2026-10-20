# DB master password + the app's three signing secrets (app.jwt.secret,
# app.ticket.credential.secret, app.device.credential.secret - see
# application.properties) are generated here and stored in Secrets Manager,
# so a real value never has to be typed into a .tfvars file or committed
# anywhere. The ECS task definition (ecs.tf) references these ARNs directly
# via the container's `secrets` block - Fargate injects them as env vars at
# container start, they're never baked into the image or logged.

resource "random_password" "db" {
  length  = 32
  special = false # simplifies the JDBC URL - only db_username/db_password themselves need no special escaping
}

resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

resource "random_password" "ticket_credential_secret" {
  length  = 48
  special = false
}

resource "random_password" "device_credential_secret" {
  length  = 48
  special = false
}

resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${local.name}/db-password"
  recovery_window_in_days = 0 # instant delete on `destroy` - raise this for production
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_secretsmanager_secret" "jwt_secret" {
  name                    = "${local.name}/jwt-secret"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "jwt_secret" {
  secret_id     = aws_secretsmanager_secret.jwt_secret.id
  secret_string = random_password.jwt_secret.result
}

resource "aws_secretsmanager_secret" "ticket_credential_secret" {
  name                    = "${local.name}/ticket-credential-secret"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "ticket_credential_secret" {
  secret_id     = aws_secretsmanager_secret.ticket_credential_secret.id
  secret_string = random_password.ticket_credential_secret.result
}

# PayMongo keys (PAYMONGO_SECRET_KEY, PAYMONGO_WEBHOOK_SECRET). Unlike the secrets above they cannot be generated: they come from the PayMongo
# dashboard. Terraform creates each secret with a placeholder so the task can start, and then never touches the value again
# (ignore_changes), so a key you type into Secrets Manager is not overwritten. Fill both in the AWS console (or with
# "aws secretsmanager put-secret-value"), then force a new deployment: a running task only reads its secrets when it starts.
resource "aws_secretsmanager_secret" "paymongo_secret_key" {
  name                    = "${local.name}/paymongo-secret-key"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "paymongo_secret_key" {
  secret_id     = aws_secretsmanager_secret.paymongo_secret_key.id
  secret_string = "CHANGE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "paymongo_webhook_secret" {
  name                    = "${local.name}/paymongo-webhook-secret"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "paymongo_webhook_secret" {
  secret_id     = aws_secretsmanager_secret.paymongo_webhook_secret.id
  secret_string = "CHANGE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}

resource "aws_secretsmanager_secret" "device_credential_secret" {
  name                    = "${local.name}/device-credential-secret"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "device_credential_secret" {
  secret_id     = aws_secretsmanager_secret.device_credential_secret.id
  secret_string = random_password.device_credential_secret.result
}

# Cloudflare R2 (uploaded files) - R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY, required by application-prod.properties.
# Same as PayMongo: an R2 API token's keys come from the Cloudflare dashboard, so Terraform only creates placeholders and ignores the value
# afterwards. Fill all three in Secrets Manager, then force a new deployment. The account id is not secret, but keeping it here means
# terraform.tfvars and the CI apply need no extra input.
resource "aws_secretsmanager_secret" "r2" {
  for_each                = toset(["r2-account-id", "r2-access-key-id", "r2-secret-access-key"])
  name                    = "${local.name}/${each.key}"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "r2" {
  for_each      = aws_secretsmanager_secret.r2
  secret_id     = each.value.id
  secret_string = "CHANGE_ME"

  lifecycle {
    ignore_changes = [secret_string]
  }
}
