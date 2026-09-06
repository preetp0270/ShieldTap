# LockCard (working name)

NFC-gated secure vaults · local encryption · optional Cloudinary backup · MongoDB accounts.

```
LockCard/   (or NFCSecurity/)
  android/     ← Android Studio project
  backend/     ← Render-deployable API
```

## Suggested product names

| Name | Why |
|------|-----|
| **LockCard** | Clear: card + lock; short; app-store friendly |
| **TapVault** | NFC tap to open vault |
| **CardSafe** | Simple, security-focused |
| **NfcKeep** | Keep secrets behind NFC |
| **VaultTap** | Same idea, different order |
| **ShieldTap** | Stronger “protection” tone |

**Recommendation:** **LockCard** — easy to say, logo-friendly, fits “primary card + MPIN”.

## Backend (Render)

See `backend/README.md`. DNS for MongoDB Atlas SRV uses Google DNS in code.

## Android

Open the `android/` folder in Android Studio.
