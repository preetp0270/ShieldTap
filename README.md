# ShieldTap

NFC-gated secure vaults · on-device encryption · optional Cloudinary backup · MongoDB accounts.

```
ShieldTap/
  android/     ← Open in Android Studio
  backend/     ← Deploy on Render
```

## Quick check

| Piece | Works when… |
|-------|-------------|
| Android build | Open `android/`, sync Gradle, device has NFC |
| Local vaults / NFC | No backend needed |
| Cloud login + file backup | Backend running + `.env` filled + user logged in |
| MongoDB Atlas | `MONGODB_URI` set; Network Access allows your IP / `0.0.0.0/0` |
| Cloudinary uploads | All three `CLOUDINARY_*` keys set |
| Email OTP | `SMTP_*` set (optional until you use reset/verify) |

## Backend

```bash
cd backend
cp .env.example .env   # edit values
npm install
npm run dev
```

## Android display name

Change user-visible name in:
- `android/app/src/main/res/values/strings.xml` → `app_name`
- Optional later: `applicationId` in `android/app/build.gradle.kts`
