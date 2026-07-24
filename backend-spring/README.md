# Ludo Spring Boot Backend

Replaces the Node/Express backend with the same endpoints and MongoDB setup.

## Requirements

- Java 17+ (your machine has Java 17 / 25)
- MongoDB connection string in `.env`

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
