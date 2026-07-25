# Ludo Spring Boot Backend

Replaces the Node/Express backend with the same endpoints and MongoDB setup.

## Requirements

- Java 17+ (your machine has Java 17 / 25)
- MongoDB connection string in `.env`
- Optional: Redis (`REDIS_URL`) for live match cache + pub/sub (smoother multi-instance / lower WS lag)

## Redis (optional)

Quick match works without Redis. When `REDIS_URL` is set:

1. Every roll/move is cached and published on a Redis channel
2. Other backend instances fan that out to WebSocket clients immediately
3. App still boots if `REDIS_URL` is blank (Redis auto-config is skipped)

Local:

```powershell
docker run -d --name ludo-redis -p 6379:6379 redis:7
# in .env:
REDIS_URL=redis://localhost:6379
```

On Render: create a Redis instance and set `REDIS_URL` to the internal Redis URL on the backend service.

## Setup

1. Copy env file:

```powershell
copy .env.sample .env
```

2. Set `MONGO_URL` in `.env`

3. Run from `backend-spring`:

```powershell
# One-time: Maven is under tools\ (already downloaded for this project)
$env:Path = "$PWD\tools\apache-maven-3.9.6\bin;$env:Path"

# Load .env into the process, then start
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*#' -or $_ -match '^\s*$') { return }
  $k,$v = $_.Split('=',2)
  [Environment]::SetEnvironmentVariable($k.Trim(), $v, 'Process')
}

mvn spring-boot:run
```

Or run the packaged jar:

```powershell
mvn -DskipTests package
java -jar target\ludo-backend-0.0.1-SNAPSHOT.jar
```

## Deploy on Render (Docker)

Render does **not** natively support Java — use **Docker**.

1. Push this repo to GitHub (include `backend-spring/Dockerfile`).
2. Render Dashboard → **New** → **Web Service** → connect the repo.
3. Settings:
   - **Language / Runtime:** `Docker`
   - **Root Directory:** `backend-spring`
   - Dockerfile path: `Dockerfile` (default)
4. Environment variables:

| Key | Example |
|-----|---------|
| `MONGO_URL` | Atlas connection string |
| `CLIENT_URL` | `https://your-frontend.onrender.com` (or Vercel/Netlify URL) |
| `SESSION_SECRET` | long random string |
| `OAUTH_ENABLED` | `false` |

`PORT` is set by Render automatically.

5. Atlas Network Access → allow `0.0.0.0/0`.
6. After deploy, open `https://YOUR-SERVICE.onrender.com/health`.
7. Frontend: set `REACT_APP_API_URL=https://YOUR-SERVICE.onrender.com`.

Free tier sleeps after idle; first request can take ~30–60s.

## Endpoints (compatible with Node backend)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health + mongo status |
| GET | `/api/me` | Current user (401 if logged out) |
| GET | `/api/logout` | Logout |
| GET | `/auth/options` | `{ google, github }` flags |
| WS | `/ws` | STOMP WebSocket endpoint |

## OAuth (optional)

Set keys and enable:

```
OAUTH_ENABLED=true
GOOGLE_KEY=...
GOOGLE_SECRET=...
GITHUB_KEY=...
GITHUB_SECRET=...
```

Login URLs (Spring Security defaults):

- Google: `/oauth2/authorization/google`
- GitHub: `/oauth2/authorization/github`

## Multiplayer (realtime)

Implemented:
- Guest login `POST /api/guest`
- Matchmaking / private rooms `/api/rooms/**`
- Authoritative `GameEngineService` (dice, moves, capture, home, 3-same bust)
- Bot fill after ~18s + `BotService` turns
- STOMP/SockJS at `/ws` → `/app/room/{id}/roll|move` → `/topic/room/{id}`

Test harness:
```
POST /api/game/test/create?players=2
POST /api/game/test/{roomId}/roll?seat=0
POST /api/game/test/{roomId}/move?seat=0&token=0&diceIndex=0
GET  /api/game/test/{roomId}
```

## Platform integration (Aakda WebView)

### Entry URL (put this in platform `launchBaseUrl`)

Prefer the **frontend** Render URL (playable UI):

```
https://YOUR-FRONTEND-ON-RENDER/?userId=USER_MONGO_ID&gameId=LUDO&sessionId=SESSION_ID&token=JWT_OR_SIGNED_TOKEN&returnUrl=https://www.aakda.in/games
```

If `launchBaseUrl` points at the **Spring** Render service instead, use the same query string on `/` or `/play` — Spring redirects to `CLIENT_URL` with params preserved.

`userId` is required. Without it the UI shows: **Open this game from Aakda app**.

Local play (no query params) is unchanged (home → modes → play).

### Health

- `GET /health` — mongo + engine
- `GET /api/health` — simple `{ ok: true, status: "UP" }`

### Wallet stubs (mock only)

- `GET /api/platform/balance?userId=...`
- `POST /api/platform/debit` `{ userId, sessionId, amount, reason }`
- `POST /api/platform/credit` `{ userId, sessionId, amount, reason }`

If `PLATFORM_SHARED_SECRET` is set, send header `X-Platform-Key: <secret>`. If unset, stubs are open (dev). Responses include `"mock": true`.  
TODO in code: wire to Aakda Node wallet APIs.

### Render env vars

| Var | Notes |
|-----|--------|
| `PORT` | Set by Render |
| `MONGO_URL` | Required |
| `CLIENT_URL` | Frontend origin(s), comma-separated (CORS + redirect target) |
| `CORS_ALLOWED_ORIGINS` | `https://www.aakda.in,https://aakda.in,http://localhost:5173` |
| `PLATFORM_SHARED_SECRET` | Optional; enables `X-Platform-Key` on wallet stubs |
| `SESSION_SECRET` | Session cookie |
| `REDIS_URL` | Optional |

Frontend also needs `REACT_APP_API_URL=https://YOUR-SPRING-ON-RENDER`.

### CORS / iframe

- Allowed patterns include Aakda origins + `http://localhost:*`
- `X-Frame-Options` disabled; CSP `frame-ancestors` allows Aakda + local parents
- API fetch uses `credentials: "include"` so launch binds to HTTP session

### Quick test

1. Open: `https://YOUR-FRONTEND/?userId=507f1f77bcf86cd799439011&gameId=LUDO&returnUrl=https://www.aakda.in/games`
2. Should skip home/login, queue online match, show lobby
3. Back/exit → `returnUrl`
4. Open frontend with no params → normal local home screen

