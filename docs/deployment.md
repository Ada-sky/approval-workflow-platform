# Deployment Guide

This guide contains the detailed Docker and GHCR deployment procedures for the Approval Workflow Platform. For a project overview, features, architecture, screenshots, and testing, see the [main README](../README.md).

## Local Docker Deployment

**Prerequisites:** Docker Desktop with Docker Compose.

### First-Time Setup

> Important: Run database initialization only for a new, empty database. For subsequent starts, use the commands below without repeating initialization or demo seeding.

1. Copy `.env.example` to `.env` and configure `DOCKER_DB_PASSWORD` and `DOCKER_ROOT_PASSWORD`.

2. Build and initialize the application:

   ```
   docker compose build backend frontend
   docker compose up -d --wait db
   docker compose --profile setup run --rm initialize
   ```

3. Optionally, set `HPOA_DEMO_PASSWORD` (16–72 UTF-8 bytes) in `.env` and create demo accounts:

   ```
   docker compose --profile demo run --rm demo-seed
   ```

4. Start the application:

   ```
   docker compose up -d backend frontend
   ```

Open [**http://localhost:8081**](http://localhost:8081/).

### Subsequent Starts

```
docker compose up -d db backend frontend
```

### Stop

```
docker compose down
```

MySQL data is persisted in a Docker volume. Database initialization is required only for first-time setup. Do not use `docker compose down -v` unless you intend to delete the stored data.

---

## Deployment with Prebuilt GHCR Images

This separate deployment needs Docker Engine/Desktop with Linux containers and a current Docker Compose v2. It does not require Java, Node, Maven or a local MySQL installation. Use **only** `compose.ghcr.yaml` for these commands; do not combine it with `compose.yaml` or override its project name. It uses project `hpoa-ghcr`, network `hpoa-ghcr_application`, volume `hpoa-ghcr_mysql-data`, and localhost port **8083**, independently of the existing `hpoa-docker` deployment.

### Select a version and configure credentials

From the repository root:

```sh
cp .env.ghcr.example .env.ghcr
```

Edit the ignored `.env.ghcr` privately. Set `HPOA_IMAGE_SHA` to the full 40-character commit SHA from a **successful CI/GHCR publishing run**, without the `sha-` prefix. Both backend and frontend use that same published version. Set separate, strong `DOCKER_DB_PASSWORD` and `DOCKER_ROOT_PASSWORD` values; do not reuse local database credentials. For optional demo accounts, also set `HPOA_DEMO_PASSWORD` to at least 16 characters and at most 72 UTF-8 bytes. Do not commit this file or share resolved Compose configuration containing its secrets.

The version-specific tags are:

- `ghcr.io/ada-sky/approval-workflow-platform-backend:sha-<full-commit-sha>`
- `ghcr.io/ada-sky/approval-workflow-platform-frontend:sha-<full-commit-sha>`

GHCR packages must be public for anonymous pulls. SHA tags select a specific release but can technically be overwritten; record the pulled image digests for stronger reproducibility. Current CI builds target Linux AMD64; native ARM images are not yet provided.

### Fresh database startup

Pull the application, initialization and optional seed images, then start MySQL:

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml --profile setup --profile demo pull
docker compose --env-file .env.ghcr -f compose.ghcr.yaml up -d --wait db
```

Only for a **new, empty deployment volume**, run the initialization command below. Wait for it to complete successfully before proceeding to demo seeding or application startup. **Do not rerun initialization on an existing deployment.**

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml --profile setup run --rm --no-deps initialize
```

Initialization applies the existing Flyway application migrations and creates Activiti's own tables with the packaged MySQL compatibility override. It deploys `hr_employee_holiday`. Normal startup does not initialize/upgrade Activiti or automatically deploy BPMN. Never run fresh initialization on an existing deployment as an update shortcut.

### Optional Demo Accounts

After successfully initializing a new database, you can optionally create demo accounts and reference data.

Set `HPOA_DEMO_PASSWORD` in `.env.ghcr` before running:

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml --profile demo run --rm --no-deps demo-seed
```

Run demo seeding only once, before modifying any demo or reference data.

### Start the Application

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml up -d --wait backend frontend
```

Open **http://127.0.0.1:8083**.

Demo accounts include `employee.a@example.test`, `manager@example.test`, `hr@example.test`, and `admin@example.test`. Sign in using the configured demo password.

### Restart, update and shutdown

Restart an initialized deployment without repeating initialization or seeding:

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml up -d --wait db backend frontend
```

Before updating, back up the deployment database and review migration/workflow release notes. Change only `HPOA_IMAGE_SHA` to another successfully published commit, retain the existing database passwords, then:

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml pull backend frontend
docker compose --env-file .env.ghcr -f compose.ghcr.yaml up -d --no-deps --wait backend frontend
```

This updates only application containers and preserves the MySQL container/volume. Backend startup validates/applies application migrations; an image rollback does not reverse database changes. Activiti schema/BPMN changes require a separately reviewed upgrade procedure, not automatic initialization. Changing `.env.ghcr` passwords does not rotate credentials stored in an existing database volume.

Normal shutdown preserves data:

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml down
```

**Do not use `docker compose down -v` for normal shutdown: it deletes the deployment's database volume and stored data.**

### Troubleshooting

```sh
docker compose --env-file .env.ghcr -f compose.ghcr.yaml ps --all
docker compose --env-file .env.ghcr -f compose.ghcr.yaml logs --tail 100 db backend frontend
```

For a missing image or denied pull, check that both SHA tags were published and package visibility is public. Check Docker Hub connectivity/rate limits if MySQL cannot be pulled. For initialization failure, preserve its terminal output and stop before starting the backend; do not delete the volume or repeatedly initialize it to hide a failure. For unhealthy startup, inspect logs, retained credentials and migration compatibility. If port 8083 is occupied, free it safely before launch. Review logs for sensitive information before sharing them. This localhost HTTP setup is for evaluation; public hosting requires HTTPS, secure cookies and proper secret management.

---

## AWS EC2 Demo Deployment

The application was deployed and validated on an **Ubuntu 24.04 AWS EC2 `t3.medium`** instance using Docker Compose, prebuilt GHCR images, and `compose.ghcr.yaml`.

MySQL 8, Spring Boot, and React/nginx ran in separate containers. Database initialization, Activiti workflow setup, container health checks, and user authentication were successfully verified.

### Access via SSH Tunnel

The frontend is bound to `127.0.0.1:8083` **on the EC2 instance** and is not publicly accessible.

To access the application securely, establish an SSH tunnel from your local machine:

```
ssh -i /path/to/private-key.pem -L 8083:127.0.0.1:8083 ubuntu@YOUR_EC2_PUBLIC_IP
```

Then open [**http://127.0.0.1:8083**](http://127.0.0.1:8083/) in your local browser.

The backend and MySQL ports are not publicly exposed. This was a temporary evaluation deployment, not a publicly hosted production service. Public deployment would require HTTPS, secure session cookie settings, and appropriate secrets management.

### AWS Resource Cleanup

This deployment was created for demonstration and validation purposes.

- **Stop the EC2 instance** when it is not in use to stop compute charges. EBS storage and allocated public IPv4 addresses may still incur charges.
- **Terminate the instance** when the deployment is no longer needed. Check for remaining EBS volumes, snapshots, and Elastic IP addresses to avoid unnecessary costs.
- **Back up important data** before termination or resource deletion, as data stored only on deleted volumes cannot be recovered.