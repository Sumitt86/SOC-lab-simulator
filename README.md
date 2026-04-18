# Mini SOC Lab Fullstack

Production-style fullstack implementation of the Red Team vs Blue Team SOC dashboard.

## Stack

- Frontend: React + Vite
- Backend: Java 17 + Spring Boot
- Deployment: Docker Compose (Nginx + API proxy)

## Project Layout

- `frontend/`: React dashboard (interactive UI)
- `backend/`: Spring Boot simulation API

## Backend API

- `GET /api/health`
- `GET /api/dashboard/summary`
- `GET /api/dashboard/logs`
- `GET /api/dashboard/alerts`
- `GET /api/todos`
- `PATCH /api/todos/{id}/toggle`
- `POST /api/simulation/reset`

## Local Dev Sequence

From project root, frontend can now be started directly:

- `npm run dev`

1. Start backend (requires Java 17 and Maven):
   - `cd backend`
   - `mvn spring-boot:run`
2. Start frontend:
   - `cd frontend`
   - `npm install`
   - `npm run dev`
3. Open:
   - `http://localhost:5173`

## Docker Deploy Sequence

1. From project root:
   - `npm run docker:up`
   - or `docker compose up --build`
2. Open:
   - `http://localhost:3000`
3. API is reachable at:
   - `http://localhost:8080/api/health`

### Docker Attack Simulation

The full stack runs 4 containers:
- **frontend** — React dashboard (port 3000)
- **backend** — Spring Boot API (port 8080), orchestrates attacks via `docker exec`
- **victim** — Ubuntu container with monitoring agent, target of attacks
- **attacker** — Ubuntu container with attack tools (nc, nmap), acts as C2

The backend mounts the Docker socket to execute commands on victim/attacker containers.
The monitoring agent inside the victim container detects attacks and reports events to the backend.

## Root Scripts

- `npm run dev` -> runs frontend Vite dev server
- `npm run build` -> builds frontend production bundle
- `npm run backend:run` -> runs Spring Boot backend (requires local Maven)
- `npm run docker:up` -> builds and starts full stack in Docker
- `npm run docker:down` -> stops Docker stack

## Backend Build Note

If `mvn` is unavailable on your system, use Docker deployment. Backend also builds inside the backend Dockerfile using Maven.

## Notes

- Frontend polls backend every 4 seconds for summary/log/alert/todo updates.
- Todo state is persisted in backend memory during runtime.
- Simulation continuously updates metrics, logs, and alerts via scheduler.
