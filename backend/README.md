# ShieldTap API

Node.js + Express + MongoDB Atlas + Cloudinary.  
Ready for **Render** free web service.

## Local

```bash
cd backend
cp .env.example .env
npm install
npm run dev
```

DNS for Atlas SRV is set in code:

```js
dns.setServers(["8.8.8.8", "8.8.4.4"]);
```

## Deploy on Render

1. Push repo to GitHub.
2. [Render Dashboard](https://dashboard.render.com) → **New** → **Web Service**.
3. Connect repo. Settings:
   - **Root Directory:** `backend`
   - **Runtime:** Node
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Health Check Path:** `/health`
4. Environment variables (same as `.env.example`):

| Key | Notes |
|-----|--------|
| `MONGODB_URI` | Atlas connection string |
| `JWT_SECRET` | long random string |
| `CLOUDINARY_CLOUD_NAME` | |
| `CLOUDINARY_API_KEY` | |
| `CLOUDINARY_API_SECRET` | |
| `CLOUDINARY_FOLDER` | e.g. `shieldtap` |
| `SMTP_*` / `MAIL_FROM` | optional until email is needed |
| `NODE_ENV` | `production` |

Or use **Blueprint**: `render.yaml` in this folder.

### Atlas Network Access

On Render, outbound IPs change. In Atlas → **Network Access** → add **`0.0.0.0/0`** (allow from anywhere) for the free tier, or lock down later with a static IP plan.

### After deploy

API base URL looks like:

```
https://shieldtap-api.onrender.com
```

Android `API_BASE_URL` (release) should be that URL (HTTPS, no trailing slash).

Free Render services **sleep** after ~15 min idle; first request may take 30–60s.
