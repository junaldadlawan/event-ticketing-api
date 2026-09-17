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

resource "aws_secretsmanager_secret" "device_credential_secret" {
  name                    = "${local.name}/device-credential-secret"
  recovery_window_in_days = 0
  tags                    = local.common_tags
}

resource "aws_secretsmanager_secret_version" "device_credential_secret" {
  secret_id     = aws_secretsmanager_secret.device_credential_secret.id
  secret_string = random_password.device_credential_secret.result
}
