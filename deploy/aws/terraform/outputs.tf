output "alb_dns_name" {
  description = "Public URL for the API once the service is healthy - http://<this>/api/v1/events"
  value       = aws_lb.main.dns_name
}

output "ecr_repository_url" {
  description = "Push your built image here (see README.md)."
  value       = aws_ecr_repository.app.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}

output "db_endpoint" {
  description = "RDS endpoint (host:port) - private, only reachable from inside the VPC."
  value       = aws_db_instance.main.endpoint
}

output "cloudwatch_log_group" {
  value = aws_cloudwatch_log_group.app.name
}
