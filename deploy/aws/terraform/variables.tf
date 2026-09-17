variable "aws_region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "project_name" {
  description = "Used as a prefix for every resource name/tag."
  type        = string
  default     = "event-ticketing-api"
}

variable "environment" {
  description = "Deployment environment name (e.g. staging, production) - tagged onto every resource."
  type        = string
  default     = "staging"
}

variable "vpc_cidr" {
  description = "CIDR block for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "container_port" {
  description = "Port the app listens on inside the container - matches server.port in application.properties."
  type        = number
  default     = 8081
}

variable "container_image" {
  description = <<-EOT
    Full ECR image URI + tag to deploy, e.g.
    123456789012.dkr.ecr.us-east-1.amazonaws.com/event-ticketing-api:latest
    Leave empty on the FIRST apply (bootstraps the ECR repo only, task
    definition points at a placeholder public image) - build/push your
    image, then re-apply with the real URI. See deploy/aws/README.md.
  EOT
  type        = string
  default     = ""
}

variable "task_cpu" {
  description = "Fargate task CPU units (256 = .25 vCPU, 512 = .5 vCPU, 1024 = 1 vCPU, ...)."
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "Fargate task memory in MiB - must be a valid pairing for task_cpu, see AWS Fargate task size table."
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "Number of running ECS tasks (app instances) behind the load balancer."
  type        = number
  default     = 1
}

variable "db_instance_class" {
  description = "RDS instance class. db.t3.micro/small are Free-Tier-eligible sizes suitable for staging, not production load."
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "RDS allocated storage in GB."
  type        = number
  default     = 20
}

variable "db_name" {
  description = "Database name - matches spring.datasource.url's path segment."
  type        = string
  default     = "event_ticketing"
}

variable "db_username" {
  description = "Master DB username. The password is auto-generated and stored in Secrets Manager - never set it here."
  type        = string
  default     = "app_user"
}

variable "log_retention_days" {
  description = "CloudWatch Logs retention for the app's container logs."
  type        = number
  default     = 30
}
