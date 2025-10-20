# Task API — Spring Boot + MongoDB + Kubernetes

A minimal REST API to manage shell-command tasks stored in MongoDB. Locally, it runs with a normal MongoDB. In Kubernetes, the app executes commands by creating a short‑lived BusyBox pod via the Kubernetes API and returns the logs as output.

## Features
- CRUD for `Task` documents in MongoDB
- Search by name (case-insensitive substring)
- Execute command with validation and 10s timeout
  - Local mode: runs via OS shell (dev only)
  - Kubernetes mode: creates a BusyBox pod and runs the command in‑cluster
- Output capture with stderr merged, truncated at 1MB

## Requirements
- Java 17+
- Maven 3.8+
- For local run: MongoDB on `mongodb://localhost:27017/tasksdb`
- For Kubernetes: Docker Desktop with Kubernetes enabled (or any K8s cluster)

---

## 1) Run Locally (without Kubernetes)

1. Make sure MongoDB is running (Windows examples):
```powershell
# If installed as a service
Get-Service *MongoDB* | Start-Service

# Or run mongod directly (adjust version/path as installed)
"C:\Program Files\MongoDB\Server\6.0\bin\mongod.exe" --dbpath C:\data\db
```

2. Build and run the app
```bash
mvn spring-boot:run
# or build a jar
mvn -DskipTests package
java -jar target/task-api-1.0.0.jar
```

3. Test endpoints
```bash
# Create
curl -X PUT http://localhost:8081/tasks \
  -H "Content-Type: application/json" \
  -d '{"id":"123","name":"Print Hello","owner":"John Smith","command":"echo Hello World!"}'

# List all
curl http://localhost:8081/tasks

# Get by id
curl "http://localhost:8081/tasks?id=123"

# Search
curl "http://localhost:8081/tasks/search?name=hello"

# Execute
curl -X PUT http://localhost:8081/tasks/123/execute

# Delete
curl -X DELETE http://localhost:8081/tasks/123
```

Notes
- Default server port is 8081 (see `src/main/resources/application.properties`).
- Default Mongo URI: `${MONGODB_URI:mongodb://localhost:27017/tasksdb}`.

---

## 2) Container Image (Docker)

Build the image (Docker Desktop shares images with its Kubernetes):
```bash
mvn -DskipTests package
docker build -t task-api:local .
```

Run it locally (optional):
```bash
docker run --rm -p 8081:8081 \
  -e MONGODB_URI="mongodb://host.docker.internal:27017/tasksdb" \
  task-api:local
```

---

## 3) Kubernetes (Docker Desktop Kubernetes)

This repo includes manifests under `k8s/`:
- `k8s/mongo.yaml`: MongoDB Deployment + Service + PVC + Secret (URI)
- `k8s/app-deployment.yaml`: App ServiceAccount + Role + RoleBinding + Deployment + Service (NodePort 30081)

### 3.1 Enable and target the cluster
```bash
kubectl config use-context docker-desktop
kubectl get nodes
```

### 3.2 Build image
```bash
mvn -DskipTests package
docker build -t task-api:local .
```

### 3.3 Deploy MongoDB
```bash
kubectl apply -f k8s/mongo.yaml
kubectl get pvc
kubectl get pods
# wait until the mongodb pod is Running
```
- MongoDB uses PVC `mongodb-pvc` mounted at `/data/db`, so data persists across pod restarts.

### 3.4 Deploy the application
```bash
kubectl apply -f k8s/app-deployment.yaml
kubectl get role,rolebinding,sa
kubectl get pods
kubectl get svc task-api
```
- The app reads `MONGODB_URI` from Secret `mongodb-conn`.
- The app reads `POD_NAMESPACE` via the downward API.
- Service type is NodePort at `30081`.

### 3.5 Test from your host
```bash
curl http://localhost:30081/tasks
```
(Empty DB returns `[]`).

Create and execute a task (runs inside a short‑lived BusyBox pod):
```bash
curl -X PUT http://localhost:30081/tasks \
  -H "Content-Type: application/json" \
  -d '{"id":"123","name":"Print Hello","owner":"John Smith","command":"echo Hello from K8s!"}'

curl -X PUT http://localhost:30081/tasks/123/execute
```
Expected execution result (snippet):
```json
{"output":"Hello from K8s!\n"}
```

### 3.6 Verify persistence
```bash
# Insert at least one task (as above), then restart mongodb pod
kubectl delete pod -l app=mongodb
kubectl get pods
# when mongodb is Running again:
curl http://localhost:30081/tasks
```
Data should still be present because it is stored on the PVC.

### 3.7 Common troubleshooting
- Port already in use: change `nodePort` in `k8s/app-deployment.yaml` or stop the conflicting process.
- Image not found: ensure you built `task-api:local` on Docker Desktop (which shares its image cache with K8s).
- RBAC errors: ensure `apiVersion: rbac.authorization.k8s.io/v1` is used (fixed in provided manifests).
- Command validation: dangerous tokens are rejected (400). Execution timeout is 10s (500 on timeout).

---

## 4) API Summary

- GET `/tasks` — list all tasks
- GET `/tasks?id={id}` — get task by id
- PUT `/tasks` — create/update task (validates `command`)
- DELETE `/tasks/{id}` — delete by id
- GET `/tasks/search?name={substring}` — name contains (case-insensitive)
- PUT `/tasks/{id}/execute` — execute the stored command
  - In Kubernetes: creates a BusyBox pod (`busybox:1.36`) with `sh -c <command>`
  - Captures logs as output; merges stderr into stdout; 10s timeout; 1MB limit

Validation rejects (case-insensitive): `rm`, `sudo`, `reboot`, `shutdown`, `mv`, `dd`, `: >`, `>`, `>>`, `|`, `;`, `&`, `&&`, `||`, `` ` ``, `$(`, `wget`, `curl`, `nc`, `netcat`, `ncat`, `chmod`, `chown`, `mkfs`, `dd if=`, `dd of=`. Newlines are not allowed. Max command length is 1024.

---

## 5) Proof checklist (what to screenshot)
1. `kubectl config use-context docker-desktop` and `kubectl get nodes`
2. `docker build -t task-api:local .` (success line)
3. `kubectl get pvc` (Bound) and `kubectl get pods` (mongodb Running)
4. `kubectl get role,rolebinding,sa` (task-api-role/task-api-rb/task-api-sa) and `kubectl get pods` (task-api Running)
5. `kubectl get svc task-api` (NodePort 30081)
6. `curl http://localhost:30081/tasks` (shows `[]` first)
7. `curl -X PUT .../execute` result showing `"Hello from K8s!\n"`
8. Optional persistence proof: delete mongodb pod and show tasks still present after restart

---

## 6) Project Structure
- `pom.xml`
- `Dockerfile`
- `k8s/mongo.yaml`
- `k8s/app-deployment.yaml`
- `src/main/java/...` Spring Boot app
- `src/main/resources/application.properties`

---

## 7) License
MIT

