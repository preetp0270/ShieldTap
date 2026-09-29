# ShieldTap Backend (Node + Express + MongoDB + Cloudinary)

## 1. Create MongoDB Atlas (free)

1. Go to https://cloud.mongodb.com → Sign up / Log in
2. Create a free cluster (M0)
3. Database Access → Add user (username + password) → remember them
4. Network Access → Add IP Address → `0.0.0.0/0` (allow from anywhere for testing)
5. Database → Connect → Drivers → copy the connection string  
   Replace `<password>` and change database name to `shieldtap`

## 2. Cloudinary (free)

1. https://cloudinary.com → Sign up
2. Dashboard → copy **Cloud name**, **API Key**, **API Secret**

## 3. Local setup

```bash
cd backend
cp .env.example .env
# Edit .env with your real values
npm install
npm run dev
```

Server starts at `http://localhost:5000`

## 4. Test

```bash
curl http://localhost:5000/health
```

## API Overview

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /api/auth/register | – | Register (email, phone, username, password) |
| POST | /api/auth/login | – | Login → returns JWT (5-day session) |
| POST | /api/auth/logout | ✓ | Logout |
| GET | /api/auth/me | ✓ | Current user + extend session |
| GET | /api/folders | ✓ | List folders (query parentId) |
| POST | /api/folders | ✓ | Create folder |
| PATCH | /api/folders/:id | ✓ | Rename / lock |
| DELETE | /api/folders/:id | ✓ | Delete folder + children |
| GET | /api/vault?folderId= | ✓ | List items in folder |
| POST | /api/vault/password | ✓ | Add password (key + value) |
| POST | /api/vault/upload | ✓ | Upload image/file (multipart) |
| DELETE | /api/vault/:id | ✓ | Delete item |
| PATCH | /api/user/profile | ✓ | Update displayName / phone |
| POST | /api/user/avatar | ✓ | Upload profile photo |
| POST | /api/user/background | ✓ | Upload background |

## Session rule

- JWT + Session document expire after **5 days of inactivity**
- Every authenticated request extends the session by another 5 days

## Important notes

- **MPIN is device-only** – never sent to server. Store it encrypted on the phone (DataStore + EncryptedSharedPreferences).
- Password values are currently stored as plain text in MongoDB for simplicity. In production you should encrypt them client-side before sending.
- Failed login attempts are rate-limited (10 per 5 min). Client should also lock after 3 wrong password/MPIN tries for 5 minutes.
