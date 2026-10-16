locals {
  # First `apply` (no image pushed yet) points the task at a harmless public
  # placeholder so the cluster/service can come up - see README.md "First
  # deploy". Re-apply with -var container_image=<your ECR URI> once you've
  # built and pushed the real image.
  image = var.container_image != "" ? var.container_image : "public.ecr.aws/docker/library/httpd:latest"
}

resource "aws_ecs_cluster" "main" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.common_tags
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.name}"
  retention_in_days = var.log_retention_days
  tags              = local.common_tags
}

resource "aws_ecs_task_definition" "app" {
  family                   = local.name
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.ecs_execution.arn
  task_role_arn            = aws_iam_role.ecs_task.arn

  container_definitions = jsonencode([
    {
      name      = "app"
      image     = local.image
      essential = true

      portMappings = [{
        containerPort = var.container_port
        protocol      = "tcp"
      }]

      environment = [
        # Activates application-prod.properties, which is what actually
        # reads every other env var below via ${...} placeholders - without
        # this, the app runs the default (dev-labeled) profile in
        # production regardless of what's injected here.
        { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
        { name = "SERVER_PORT", value = tostring(var.container_port) },
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}" },
        { name = "SPRING_DATASOURCE_USERNAME", value = var.db_username },
        # Overrides application.properties' dev-only `update` - in
        # production, Flyway (already enabled) should be the only thing
        # that ever changes the schema. See CLAUDE.md "Database &
        # migrations" for why both being on at once is flagged as a gap.
        { name = "SPRING_JPA_HIBERNATE_DDL_AUTO", value = "validate" },
        # mock (no real payments) or paymongo. See variables.tf.
        { name = "PAYMENTS_GATEWAY", value = var.payments_gateway },
        # Demo only: loads db/seed/demo-data.sql at startup when true. See variables.tf.
        { name = "APP_SEED_DEMO_DATA", value = tostring(var.seed_demo_data) },
        # Browser origins allowed to call the API cross-origin (CORS); empty = none. See variables.tf.
        { name = "APP_CORS_ALLOWED_ORIGINS", value = join(",", var.cors_allowed_origins) },
      ]

      secrets = [
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn },
        { name = "APP_JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_secret.arn },
        { name = "APP_TICKET_CREDENTIAL_SECRET", valueFrom = aws_secretsmanager_secret.ticket_credential_secret.arn },
        { name = "APP_DEVICE_CREDENTIAL_SECRET", valueFrom = aws_secretsmanager_secret.device_credential_secret.arn },
        # PayMongo keys from the dashboard: placeholders until you set them in Secrets Manager (see secrets.tf). Only used when PAYMENTS_GATEWAY=paymongo.
        { name = "PAYMONGO_SECRET_KEY", valueFrom = aws_secretsmanager_secret.paymongo_secret_key.arn },
        { name = "PAYMONGO_WEBHOOK_SECRET", valueFrom = aws_secretsmanager_secret.paymongo_webhook_secret.arn },
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.app.name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "app"
        }
      }
    }
  ])

  tags = local.common_tags
}

resource "aws_ecs_service" "app" {
  name            = local.name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.ecs_service.id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "app"
    container_port   = var.container_port
  }

  # Give Flyway/JPA time to run migrations on a cold start before ECS gives
  # up on the container. The app takes about 80s to start on 0.5 vCPU, so 60s was too tight.
  health_check_grace_period_seconds = 180

  # If new tasks keep failing to start or to pass the health check, stop retrying and go back to the last working task definition
  # instead of looping forever. The old task keeps serving until a new one is healthy. It does not undo database migrations.
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  depends_on = [aws_lb_listener.http]

  tags = local.common_tags
}
