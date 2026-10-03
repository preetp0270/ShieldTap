# ShieldTap – Secure Personal Vault

Kotlin (Jetpack Compose) Android app + Node/Express/MongoDB backend.  
Stores **passwords**, **files**, and **photos** with device MPIN + account login (session lasts 5 days of activity).

```
ShieldTap/
├── android/          ← Open this folder in Android Studio
└── backend/          ← Node API (MongoDB + Cloudinary)
```

---

## What is already built

### Auth & session
- Register: Email + Phone + Username + Password  
- Login: Username + Password → JWT  
- Session expires after **5 days of inactivity**; every API call extends it by 5 days  
- After register → user must login (no auto-login)

### Device MPIN + App Lock
- Asked once after first login (stored **only on device**, encrypted)  
- **App unlock**: required every time the app is opened (or after you tap Lock)  
- Also required for: create / rename / delete / lock main folders  
- Nested folders do **not** need MPIN  
- 3 wrong password **or** MPIN attempts → lock for 5 minutes  
- UI: monochrome black / white / grey with glass cards and pill controls

### Home
- Pill-shaped bottom navigation: **Home | Soon | Profile**  
- Floating **+** creates a new root folder (asks MPIN)  
- Folders can be renamed, locked, deleted (all need MPIN)  
- Lock icon on top-right of home

### Inside a folder
- Create sub-folders (no MPIN)  
- Add password (key + value)  
- Upload photo or any file (Cloudinary)  
- View / delete items

### Profile
- Username, email, phone  
- Avatar + background image (Cloudinary)  
- Notification list (register / login / profile updates)  
- Logout  
- Settings → theme: System / Light / Dark (applies everywhere)

---

## 1. Backend setup (do this first)

### MongoDB Atlas (free)
1. https://cloud.mongodb.com → Create free M0 cluster  
2. **Database Access** → Add user (user + password)  
3. **Network Access** → Add IP `0.0.0.0/0`  
4. **Connect** → Drivers → copy URI, replace `<password>`, set database name to `shieldtap`

### Cloudinary (free)
1. https://cloudinary.com → Sign up  
2. Dashboard → copy **Cloud name**, **API Key**, **API Secret**

### Run backend
```bash
cd backend
cp .env.example .env
# Edit .env with MongoDB URI + Cloudinary keys + a long JWT_SECRET
npm install
npm run dev
```
You should see:
```
✅ MongoDB connected
✅ Cloudinary configured
🚀 ShieldTap API running on http://localhost:5000
```

Test: `curl http://localhost:5000/health`

---

## 2. Android setup

1. Open **Android Studio** → **Open** → select the `android/` folder  
2. Let Gradle sync (first time may download SDK / dependencies)  
3. Open `app/src/main/java/com/shieldtap/vault/data/ApiClient.kt`  
4. Change `BASE_URL`:
   - Emulator: `http://10.0.2.2:5000/api/`  (already set)
   - Real phone: `http://YOUR_PC_IP:5000/api/` (same Wi-Fi)
   - Production: your Render / Railway URL + `/api/`
5. Run on emulator or device

---

## 3. Firebase (you asked – beginner guide)

Right now the app does **not** require Firebase.  
You can add it later for:

| Feature | Firebase product |
|---------|------------------|
| Push notifications | Cloud Messaging (FCM) |
| Crash reports | Crashlytics |
| Analytics | Analytics |
| Optional Auth | Firebase Auth (if you want phone OTP later) |

### Quick Firebase setup (when you want it)
1. Go to https://console.firebase.google.com → Create project  
2. Add Android app → package name = `com.shieldtap.vault`  
3. Download `google-services.json` → put it in `android/app/`  
4. In Android Studio add the Google Services plugin (I can help when you are ready)  
5. For FCM: enable Cloud Messaging in console

**You do not need Firebase for the current features** (auth is custom JWT + MongoDB, images are Cloudinary).

---

## 4. Environment variables summary

| Variable | Where | Purpose |
|----------|-------|---------|
| `MONGODB_URI` | backend/.env | Database |
| `JWT_SECRET` | backend/.env | Sign login tokens |
| `CLOUDINARY_*` | backend/.env | Image & file storage |
| `BASE_URL` | ApiClient.kt | Point Android to your API |

---

## Next features you can request
- Encrypt password values before sending to server  
- Biometric (fingerprint / face) unlock  
- Share folder via link  
- Search across vaults  
- Firebase push notifications  
- Export / import vault  

Just tell me the next feature and we continue.
