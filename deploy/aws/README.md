# Deploying to AWS (ECS Fargate + RDS Postgres)

What this stands up: a VPC (2 public + 2 private subnets, 1 NAT gateway),
an Application Load Balancer, an ECS Fargate service running the app
container, an RDS Postgres 16 instance, an ECR repository for the image,
and Secrets Manager entries for the DB password and the app's three signing
secrets (`app.jwt.secret`, `app.ticket.credential.secret`,
`app.device.credential.secret` — see `application.properties`).

```
Internet
   │
   ▼
[ALB :80]  (public subnets)
   │
   ▼
[ECS Fargate task :8081]  (private subnets)  ──►  [RDS Postgres :5432]  (private subnets)
```

Everything is defined under `deploy/aws/terraform/`. Nothing here has been
applied to any AWS account — these are files for you to run.

## Prerequisites

- An AWS account + an IAM identity with permission to create the resources
  above (or just admin access for a first pass).
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
  v2, configured: `aws configure` (or `aws sso login` if your org uses SSO).
- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.6.
- Docker Desktop (or any Docker engine) to build the image.

## First deploy

**1. Bootstrap the infrastructure** (this first apply has no real image yet
— the task definition points at a harmless public placeholder just so the
cluster/service can come up):

```bash
cd deploy/aws/terraform
terraform init
terraform apply
```

Review the plan, type `yes`. Takes ~5–8 minutes (the NAT gateway and RDS
instance are the slow parts). Note the `ecr_repository_url` output when
it's done.

**2. Build and push the real image**, tagged with something unique per
build — a git SHA works well — **not** `:latest` (see "Deploying an
update" below for why):

```bash
cd ../../..    # back to the repo root, where the Dockerfile lives
aws ecr get-login-password --region us-east-1 \
  | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

IMAGE_TAG=$(git rev-parse --short HEAD)
docker build -t <ecr_repository_url>:$IMAGE_TAG .
docker push <ecr_repository_url>:$IMAGE_TAG
```

**3. Point the service at the real image:**

```bash
cd deploy/aws/terraform
terraform apply -var "container_image=<ecr_repository_url>:$IMAGE_TAG"
```

**4. Confirm it's up:**

```bash
curl http://$(terraform output -raw alb_dns_name)/api/v1/events
```

A `200` with a JSON page response means the app started, connected to RDS,
and Flyway ran its migrations against a fresh database.

## Deploying an update

Repeat steps 2–3 above with a new tag each time:

```bash
IMAGE_TAG=$(git rev-parse --short HEAD)
docker build -t <ecr_repository_url>:$IMAGE_TAG .
docker push <ecr_repository_url>:$IMAGE_TAG
terraform apply -var "container_image=<ecr_repository_url>:$IMAGE_TAG"
```

**Always use a unique tag, never `:latest`.** Terraform only registers a new
ECS task definition revision (and rolls the service to it) when
`container_image`'s *value* changes between applies — reusing `:latest`
means the string never changes, so `terraform apply` sees nothing to do
even though you pushed a new image underneath that tag.

ECS does a rolling deployment automatically — it starts the new task,
waits for it to pass the ALB health check (`GET /api/v1/events`), then
drains the old one. `desired_count` defaults to 1, so there's a brief gap
with only one healthy task during the swap; bump `desired_count` to 2+ in
`variables.tf` to avoid that if the app needs to stay up through deploys.

## Setting up CI to do steps 2–3 for you

If you want a GitHub Actions workflow that builds, pushes, and applies on
every push to `main`, say so and I'll add one — it needs an
`AWS_ROLE_ARN` (via OIDC, no long-lived AWS keys in GitHub) with the same
permissions your local `aws configure` identity has for this stack. Not
included here since it wasn't asked for.

## Adding HTTPS

Once you have a domain:

1. Request/validate a certificate in **ACM** for that domain (must be in
   the same region as the ALB).
2. Add an `aws_route53_record` (or point your DNS provider) at the ALB's
   `alb_dns_name` output.
3. In `alb.tf`, add an `aws_lb_listener "https"` on port 443 referencing the
   certificate, and change the existing port-80 listener's
   `default_action` to a `redirect` (type `"redirect"`, `status_code
   = "HTTP_301"`, port `"443"`, protocol `"HTTPS"`) instead of forwarding.
4. Open port 443 on `aws_security_group.alb` in `security_groups.tf`.

## Cost notes (this default config)

Roughly, in `us-east-1`: NAT gateway ~$32/mo + ~$0.045/GB processed, ALB
~$16-20/mo, `db.t3.micro` RDS ~$12-13/mo + storage, one Fargate task at
512 CPU/1024 MiB ~$15/mo. Call it **$75-90/month** idling with real traffic
pushing it higher via NAT data-processing and Fargate task-hours. The
biggest lever for a dev/staging environment you don't need running 24/7:
`terraform destroy` it when you're not using it, or drop `desired_count`
to `0` and stop the RDS instance (`aws rds stop-db-instance`) between uses
— stopped RDS instances still bill for storage but not compute.

## What's deliberately NOT set up here

- **Multi-AZ RDS / multiple NAT gateways** — this is a single-AZ-for-compute,
  cost-conscious default. Flip `multi_az = true` in `rds.tf` and add a NAT
  gateway per AZ in `vpc.tf` for production HA (roughly doubles NAT +
  RDS cost).
- **A remote Terraform state backend** — state defaults to a local
  `terraform.tfstate` file in this directory. Fine solo; switch to the S3
  backend commented in `versions.tf` the moment more than one person or
  machine runs `terraform apply` against this.
- **`spring.mail`/SMTP config** — this codebase's notification system uses
  a `MockEmailSender` (no real provider wired in yet, per
  `docs/event-ticketing-api-roadmap.md` Phase 11), so there's nothing to
  configure here for real email delivery until that's built.
- **Auto-scaling** — `desired_count` is a fixed number, not an
  `aws_appautoscaling_target`. Add one keyed on ECS service CPU/memory if
  load becomes unpredictable.
