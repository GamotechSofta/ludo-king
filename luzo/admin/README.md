# PotLudo Admin Panel

React admin console for PotLudo — login, live dashboard, per-platform (operator) breakdown, profit & loss, and platform fee settings.

Talks to **backend-spring** (`E:\ludo-king\backend-spring`, port **3000**) admin APIs over HTTP. In local dev, Vite proxies `/api` to that backend.

---

## Stack

| Piece | Detail |
|-------|--------|
| UI | React 19 |
| Bundler | Vite 8 |
| Styling | Tailwind CSS 4 |
| Auth token | `localStorage` key `potludo_admin_token` |
| Dev port | `5174` |
| API (default) | Proxied to `http://localhost:3000` |

---

## Prerequisites

1. **Node.js** 18+ (recommended)
2. **backend-spring** running on port **3000** (`mvn spring-boot:run` with `.env` loaded)
3. Admin login (defaults in `.env`):
   - Email: `admin@ludo.local`
   - Password: `admin123`

---

## Setup & run

```bash
cd luzo/admin
npm install
npm run dev
```

Open: **http://localhost:5174**

Other scripts:

```bash
npm run build    # production build → dist/
npm run preview  # preview production build
npm run lint     # ESLint
```

### Optional env vars

Copy `.env.example` to `.env` if you need custom API bases (defaults work with the Vite proxy):

```env
VITE_API_BASE_URL=/api/v1/admin/profit-loss
VITE_SETTINGS_API_BASE_URL=/api/v1/admin/settings
VITE_AUTH_API_BASE_URL=/api/v1/admin/auth
```

`vite.config.js` proxies any `/api/*` request to `http://localhost:3000`.

---

## How it works (end-to-end)

```
Browser (admin UI :5174)
    │
    │  fetch /api/v1/admin/...
    ▼
Vite proxy
    │
    ▼
backend-spring (:3000)
    ├── /api/v1/admin/auth
    ├── /api/v1/admin/profit-loss
    └── /api/v1/admin/settings
```

### Auth

1. App mounts → “Checking admin session…”
2. If `potludo_admin_token` exists → `GET /api/v1/admin/auth/me`
3. Valid session → load dashboard + P&L data
4. No / invalid token → **Login** page
5. Login → `POST /api/v1/admin/auth/login` with `{ email, password }`
6. Response `token` saved to `localStorage`; calls send `Authorization: Bearer <token>`
7. Logout → `POST /api/v1/admin/auth/logout` + clear token + clear in-memory data

401 on any data call logs the user out and returns them to Login.

### Navigation

No React Router. Sidebar switches `activePage`:

| Page ID | Screen |
|---------|--------|
| `dashboard` | Live overview |
| `platforms` | Per-operator cards |
| `profit-loss` | Overview / Games / Users + filters |
| `settings` | Platform fee editor |

---

## Project layout

```
luzo/admin/
├── index.html
├── package.json
├── vite.config.js
├── public/
└── src/
    ├── main.jsx
    ├── index.css
    ├── App.jsx
    ├── api/
    │   ├── client.js
    │   └── authStorage.js
    ├── pages/
    ├── components/
    └── utils/format.js
```

---

## Typical local workflow

1. Start **backend-spring** (port **3000**).
2. Start this app: `npm run dev` (port **5174**).
3. Sign in with `admin@ludo.local` / `admin123` (or your `ADMIN_*` env values).
4. Use Dashboard → Platforms → Profit & Loss filters → Settings as needed.
