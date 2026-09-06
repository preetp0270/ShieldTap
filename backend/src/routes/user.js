import { Router } from "express";
import { requireAuth } from "../middleware/auth.js";
import { User } from "../models/User.js";
import { hashPassword, verifyPassword } from "../utils/crypto.js";

const router = Router();

router.use(requireAuth);

router.get("/profile", async (req, res) => {
  const u = req.user;
  res.json({
    id: u._id,
    username: u.username,
    email: u.email,
    displayName: u.displayName,
    photoUrl: u.photoUrl,
    emailVerified: u.emailVerified,
    cards: u.cards || [],
    cardsCount: (u.cards || []).length,
  });
});

router.patch("/profile", async (req, res, next) => {
  try {
    const user = await User.findById(req.user._id);
    if (req.body.displayName != null) user.displayName = String(req.body.displayName).slice(0, 64);
    if (req.body.photoUrl != null) user.photoUrl = String(req.body.photoUrl).slice(0, 512);
    await user.save();
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

/** Register / update NFC cards for this cloud account */
router.post("/cards", async (req, res, next) => {
  try {
    const { uid, label, isPrimary } = req.body;
    if (!uid) return res.status(400).json({ error: "uid required" });
    const user = await User.findById(req.user._id);
    const upper = String(uid).toUpperCase();
    const existing = user.cards.find((c) => c.uid.toUpperCase() === upper);
    if (existing) {
      if (label != null) existing.label = label;
      if (isPrimary) {
        user.cards.forEach((c) => (c.isPrimary = false));
        existing.isPrimary = true;
      }
    } else {
      if (isPrimary || user.cards.length === 0) {
        user.cards.forEach((c) => (c.isPrimary = false));
      }
      user.cards.push({
        uid: upper,
        label: label || "",
        isPrimary: Boolean(isPrimary) || user.cards.length === 0,
      });
    }
    await user.save();
    res.json({ cards: user.cards });
  } catch (e) {
    next(e);
  }
});

router.delete("/cards/:uid", async (req, res, next) => {
  try {
    const user = await User.findById(req.user._id);
    const upper = String(req.params.uid).toUpperCase();
    user.cards = user.cards.filter((c) => c.uid.toUpperCase() !== upper);
    if (user.cards.length && !user.cards.some((c) => c.isPrimary)) {
      user.cards[0].isPrimary = true;
    }
    await user.save();
    res.json({ cards: user.cards });
  } catch (e) {
    next(e);
  }
});

router.post("/change-password", async (req, res, next) => {
  try {
    const { currentPassword, newPassword } = req.body;
    if (!currentPassword || !newPassword || String(newPassword).length < 8) {
      return res.status(400).json({ error: "Invalid password" });
    }
    const user = await User.findById(req.user._id);
    if (!(await verifyPassword(currentPassword, user.passwordHash))) {
      return res.status(401).json({ error: "Current password incorrect" });
    }
    user.passwordHash = await hashPassword(newPassword);
    await user.save();
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

export default router;
