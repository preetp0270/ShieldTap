import { Router } from "express";
import crypto from "crypto";
import { z } from "zod";
import { User } from "../models/User.js";
import { Session } from "../models/Session.js";
import { hashPassword, verifyPassword, generateOtp, hashOtp } from "../utils/crypto.js";
import { signToken } from "../utils/jwt.js";
import { sendWelcomeEmail, sendOtpEmail } from "../services/mail.js";
import { requireAuth } from "../middleware/auth.js";

const router = Router();

const registerSchema = z.object({
  username: z.string().min(3).max(32).regex(/^[a-zA-Z0-9_]+$/),
  email: z.string().email(),
  password: z.string().min(8).max(128),
  displayName: z.string().max(64).optional(),
});

const loginSchema = z.object({
  usernameOrEmail: z.string().min(3),
  password: z.string().min(1),
  deviceLabel: z.string().max(64).optional(),
});

function sessionDays() {
  return Number(process.env.SESSION_DAYS || 30);
}

async function createSession(userId, deviceLabel = "") {
  const token = signToken({ sub: String(userId) });
  const tokenHash = crypto.createHash("sha256").update(token).digest("hex");
  const expiresAt = new Date(Date.now() + sessionDays() * 24 * 60 * 60 * 1000);
  await Session.create({ userId, tokenHash, deviceLabel, expiresAt });
  return { token, expiresAt };
}

router.post("/register", async (req, res, next) => {
  try {
    const body = registerSchema.parse(req.body);
    const username = body.username.toLowerCase();
    const email = body.email.toLowerCase();

    const exists = await User.findOne({
      $or: [{ username }, { email }],
    });
    if (exists) {
      return res.status(409).json({ error: "Username or email already registered" });
    }

    const passwordHash = await hashPassword(body.password);
    const user = await User.create({
      username,
      email,
      passwordHash,
      displayName: body.displayName || username,
    });

    // Welcome email (non-blocking)
    sendWelcomeEmail(email, username).catch(console.error);

    // Send verify OTP
    const otp = generateOtp();
    user.otpHash = hashOtp(otp);
    user.otpExpiresAt = new Date(Date.now() + 10 * 60 * 1000);
    user.otpPurpose = "verify";
    await user.save();
    sendOtpEmail(email, otp, "verify").catch(console.error);

    const { token, expiresAt } = await createSession(user._id, "registration");
    res.status(201).json({
      token,
      expiresAt,
      user: {
        id: user._id,
        username: user.username,
        email: user.email,
        displayName: user.displayName,
        emailVerified: user.emailVerified,
      },
    });
  } catch (e) {
    if (e.name === "ZodError") return res.status(400).json({ error: e.errors });
    next(e);
  }
});

router.post("/login", async (req, res, next) => {
  try {
    const body = loginSchema.parse(req.body);
    const q = body.usernameOrEmail.toLowerCase();
    const user = await User.findOne({
      $or: [{ username: q }, { email: q }],
    });
    if (!user || !(await verifyPassword(body.password, user.passwordHash))) {
      return res.status(401).json({ error: "Invalid credentials" });
    }

    const { token, expiresAt } = await createSession(user._id, body.deviceLabel || "");
    res.json({
      token,
      expiresAt,
      user: {
        id: user._id,
        username: user.username,
        email: user.email,
        displayName: user.displayName,
        emailVerified: user.emailVerified,
        cardsCount: user.cards?.length || 0,
      },
    });
  } catch (e) {
    if (e.name === "ZodError") return res.status(400).json({ error: e.errors });
    next(e);
  }
});

router.post("/logout", requireAuth, async (req, res, next) => {
  try {
    req.session.revoked = true;
    await req.session.save();
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

/** Request password-reset OTP (email). Primary card check is done client-side before calling complete. */
router.post("/forgot-password", async (req, res, next) => {
  try {
    const email = String(req.body.email || "").toLowerCase();
    if (!email) return res.status(400).json({ error: "Email required" });
    const user = await User.findOne({ email });
    // Always respond ok to avoid account enumeration
    if (user) {
      const otp = generateOtp();
      user.otpHash = hashOtp(otp);
      user.otpExpiresAt = new Date(Date.now() + 10 * 60 * 1000);
      user.otpPurpose = "reset";
      await user.save();
      sendOtpEmail(email, otp, "reset").catch(console.error);
    }
    res.json({ ok: true, message: "If that email exists, a code was sent" });
  } catch (e) {
    next(e);
  }
});

router.post("/reset-password", async (req, res, next) => {
  try {
    const { email, otp, newPassword, primaryCardUid } = req.body;
    if (!email || !otp || !newPassword) {
      return res.status(400).json({ error: "email, otp, newPassword required" });
    }
    if (String(newPassword).length < 8) {
      return res.status(400).json({ error: "Password too short" });
    }
    const user = await User.findOne({ email: String(email).toLowerCase() });
    if (!user || user.otpPurpose !== "reset" || !user.otpHash || !user.otpExpiresAt) {
      return res.status(400).json({ error: "Invalid or expired code" });
    }
    if (user.otpExpiresAt < new Date() || user.otpHash !== hashOtp(String(otp))) {
      return res.status(400).json({ error: "Invalid or expired code" });
    }
    // 2FA: if user has a primary card, require matching UID
    const primary = user.cards?.find((c) => c.isPrimary);
    if (primary) {
      if (!primaryCardUid || primary.uid.toUpperCase() !== String(primaryCardUid).toUpperCase()) {
        return res.status(403).json({
          error: "Primary NFC card required to reset password",
          requirePrimaryCard: true,
        });
      }
    }

    user.passwordHash = await hashPassword(String(newPassword));
    user.otpHash = null;
    user.otpExpiresAt = null;
    user.otpPurpose = null;
    await user.save();

    // Revoke all sessions
    await Session.updateMany({ userId: user._id }, { revoked: true });

    res.json({ ok: true, message: "Password updated. Please log in again." });
  } catch (e) {
    next(e);
  }
});

router.post("/verify-email", requireAuth, async (req, res, next) => {
  try {
    const { otp } = req.body;
    const user = await User.findById(req.user._id);
    if (!user || user.otpPurpose !== "verify" || !user.otpHash) {
      return res.status(400).json({ error: "No pending verification" });
    }
    if (user.otpExpiresAt < new Date() || user.otpHash !== hashOtp(String(otp))) {
      return res.status(400).json({ error: "Invalid or expired code" });
    }
    user.emailVerified = true;
    user.otpHash = null;
    user.otpExpiresAt = null;
    user.otpPurpose = null;
    await user.save();
    res.json({ ok: true, emailVerified: true });
  } catch (e) {
    next(e);
  }
});

router.get("/me", requireAuth, async (req, res) => {
  res.json({ user: req.user });
});

export default router;
