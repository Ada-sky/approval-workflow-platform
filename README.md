# Approval Workflow Platform

[![CI](https://github.com/Ada-sky/approval-workflow-platform/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/Ada-sky/approval-workflow-platform/actions/workflows/ci.yml)

A full-stack employee leave approval platform built with React, Spring Boot, MySQL, and Activiti BPMN. Employees can submit and track leave requests, while managers and HR review and process them through role-based, multi-stage approval workflows. Administrators manage employee accounts, organizational data, and access permissions.

---

## Screenshots

### Dashboard

![dashboard](Screenshots/dashboard.png)

### Workflow Progress

![Workflow Progress](Screenshots/workflow-progress.png)

### Approval Workbench

![Approval Workbench](Screenshots/approval-workbench.png)

### Role & Permission Management

![Role & Permission Management](Screenshots/role-permission-management.png)

---

## Key Features

**Employee**

- Submit and track leave requests
- View approval progress and decision history
- Withdraw eligible pending requests and archive completed requests

**Approver (Department Manager / General Manager / HR)**

- Review and approve or reject assigned leave requests
- Manage pending approval tasks
- View personal approval history
- Submit and manage their own leave requests

**Administrator**

- Manage employee records, departments, job titles, and leave types
- Manage roles and access permissions (RBAC)
- Provision employee accounts with securely generated temporary passwords

---

## Approval Workflow

Leave requests follow conditional, multi-stage approval workflows based on the applicant's role and leave duration.

| Applicant                 | Leave Duration | Approval Route                            |
| ------------------------- | -------------- | ----------------------------------------- |
| Employee                  | ≤ 3 days       | Department Manager → HR                   |
| Employee                  | > 3 days       | Department Manager → General Manager → HR |
| Department Manager        | Any duration   | General Manager → HR                      |
| General Manager           | Any duration   | HR                                        |
| HR                        | Any duration   | General Manager                           |
| System-only Administrator | —              | Cannot submit leave                       |

Leave duration is calculated using **inclusive calendar days**. Requests lasting exactly three days do not require General Manager approval.

### Workflow Rules

- **Authorization:** Only eligible approvers can act on assigned tasks, and applicants cannot approve their own requests.
- **Rejection & Withdrawal:** Both terminate the active workflow while preserving request and approval history.
- **Audit History:** Approval decisions remain accessible to authorized users, even after applicants archive completed requests.

---

## Tech Stack

| Area           | Technologies                                           |
| -------------- | ------------------------------------------------------ |
| Backend        | Java 21, Spring Boot, Spring Security, Spring Data JPA |
| Frontend       | React, JavaScript, Bootstrap                           |
| Workflow       | Activiti 8, BPMN                                       |
| Database       | MySQL, Flyway                                          |
| Infrastructure | Docker, Docker Compose, nginx                          |
| Testing        | JUnit 5, Mockito, Vitest, React Testing Library        |

------

## Engineering Highlights

1. **BPMN Workflow & Conditional Routing**
   Implemented multi-stage approval workflows using Activiti BPMN, with approval routes determined by applicant roles and leave duration.
2. **Task-Level Authorization**
   Enforced backend authorization for individual approval tasks, preventing unauthorized decisions and self-approval.
3. **Role-Based Access Control (RBAC)**
   Implemented role-based permissions using Spring Security, enforcing backend authorization for administrative operations and protected application features.
4. **Transactional Consistency**
   Used Spring transactions and pessimistic row locking to coordinate approval and withdrawal operations and reduce the risk of conflicting state changes.
5. **Database Schema Management**
   Used Spring Data JPA for application persistence and Flyway for versioned database migrations, while Activiti manages its own workflow tables.
6. **Audit History & Data Integrity**
   Preserved approval decisions and request history across rejection, withdrawal, and archiving, with ownership and authorization checks protecting historical access.
7. **Containerized Deployment**
   Containerized the React frontend, Spring Boot backend, and isolated MySQL database using Docker Compose, with nginx serving the frontend and proxying API requests.
8. **Session Authentication & CSRF Protection**
   Implemented session-based authentication using Spring Security, with BCrypt password hashing and CSRF protection for state-changing requests.

------

## Architecture

```text
React Frontend
      │
      ▼
Spring Boot REST API
      │
      ├── Spring Data JPA ────┐
      │                       ├── MySQL
      └── Activiti BPMN ──────┘
```

The React frontend communicates with the Spring Boot backend through REST APIs. Spring Data JPA manages application data, while Activiti executes BPMN workflows and manages approval tasks.
Flyway manages application database migrations, while Activiti maintains its own workflow engine tables.

------

## Project Structure

```
## Project Structure

​```text
.
├── backend/                 # Spring Boot REST API
│   └── src/
│       ├── main/
│       │   ├── java/        # Controllers, services, repositories, security
│       │   └── resources/
│       │       ├── bpmn/    # Activiti workflow definitions
│       │       └── db/      # Flyway database migrations
│       └── test/            # Backend tests
├── frontend/                # React application
│   ├── src/                 # Pages, components, API integration
│   └── ...
├── compose.yaml             # Docker Compose configuration
└── README.md
​```
```

------

## Running with Docker

**Prerequisites:** Docker Desktop with Docker Compose.

### First-Time Setup

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

------

## Local Development

**Prerequisites:** Java 21, Maven, Node.js 22.12+, npm, and an initialized MySQL database with the BPMN process definition.

**Backend**

Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as environment variables, then run:

```
mvn -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=dev
```

**Frontend**

```
cd frontend
npm ci
npm run dev
```

The backend runs on `127.0.0.1:8080`, while Vite runs on `localhost:5173` and proxies API requests to the backend. Use `BACKEND_URL` to override the proxy target.

**Note:** Spring Boot does not automatically load `.env` files.

------

## Testing

Automated tests cover workflow routing, approval decisions, authentication, authorization, business logic, and frontend interactions.

**Backend — JUnit 5, Mockito, MockMvc**

The backend test suite runs without a database.

```
mvn -B -ntp -f backend/pom.xml test
mvn -B -ntp -f backend/pom.xml package
```

**Frontend — Vitest, React Testing Library**

```
cd frontend
npm ci
npm test
npm run build
npm run format:check
```

The application was also verified in an isolated Docker environment, including database initialization, session authentication, CSRF protection, approval workflows, and data persistence.e.



## Continuous Integration and Container Publishing

GitHub Actions runs on pushes to `main` and pull requests targeting `main`. It runs the database-free Maven tests and Spring Boot packaging, frontend formatting checks and Vitest tests, and the Vite production build. Both existing Dockerfiles must also build successfully.

After all checks pass on a push to `main`, the exact CI-built images are published to GitHub Container Registry without rebuilding them:

- `ghcr.io/ada-sky/approval-workflow-platform-backend:sha-<full-commit-sha>`
- `ghcr.io/ada-sky/approval-workflow-platform-frontend:sha-<full-commit-sha>`

Pull requests never publish images. Publishing uses GitHub's built-in token with package-write permission limited to the publishing job; no registry password is stored in the repository. GHCR package visibility may need to be set to public after the first successful publication.

After image builds, a GitHub-hosted runner loads the exact backend image and verifies fresh MySQL initialization, successful Flyway migrations, Activiti schema/BPMN availability, backend readiness, the public CSRF endpoint and unauthenticated API protection. It uses a uniquely named disposable Compose project, generated temporary credentials and no published database port. Initialization must finish before normal backend startup; no demo seeding runs. Temporary containers, networks and volumes are cleaned up even on test failure. A failed smoke test prevents GHCR publishing. This focused check does not cover authenticated browser or approval workflows. Image publication does not deploy or update a running application.
