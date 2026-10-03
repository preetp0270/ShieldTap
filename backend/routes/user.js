import { Router } from 'express';
import multer from 'multer';
import User from '../models/User.js';
import { requireAuth } from '../middleware/auth.js';
import cloudinary from '../config/cloudinary.js';

const router = Router();
router.use(requireAuth);

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 5 * 1024 * 1024 },
});

// PATCH /api/user/profile
router.patch('/profile', async (req, res, next) => {
  try {
    const { displayName, phone } = req.body;
    const user = await User.findById(req.user._id);
    if (displayName !== undefined) user.displayName = String(displayName).slice(0, 50);
    if (phone !== undefined) user.phone = String(phone).slice(0, 15);

    user.notifications.unshift({
      type: 'profile_update',
      message: 'Profile updated',
    });
    await user.save();

    res.json({
      user: {
        id: user._id,
        username: user.username,
        email: user.email,
        phone: user.phone,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
        backgroundUrl: user.backgroundUrl,
      },
    });
  } catch (err) {
    next(err);
  }
});

// POST /api/user/avatar
router.post('/avatar', upload.single('avatar'), async (req, res, next) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'No file' });

    const result = await new Promise((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        { folder: `shieldtap/${req.user._id}/avatar`, transformation: [{ width: 400, height: 400, crop: 'fill' }] },
        (err, r) => (err ? reject(err) : resolve(r))
      );
      stream.end(req.file.buffer);
    });

    const user = await User.findById(req.user._id);
    user.avatarUrl = result.secure_url;
    user.notifications.unshift({ type: 'profile_update', message: 'Profile photo updated' });
    await user.save();

    res.json({ avatarUrl: user.avatarUrl });
  } catch (err) {
    next(err);
  }
});

// POST /api/user/background
router.post('/background', upload.single('background'), async (req, res, next) => {
  try {
    if (!req.file) return res.status(400).json({ error: 'No file' });

    const result = await new Promise((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        { folder: `shieldtap/${req.user._id}/bg` },
        (err, r) => (err ? reject(err) : resolve(r))
      );
      stream.end(req.file.buffer);
    });

    const user = await User.findById(req.user._id);
    user.backgroundUrl = result.secure_url;
    await user.save();

    res.json({ backgroundUrl: user.backgroundUrl });
  } catch (err) {
    next(err);
  }
});

// GET /api/user/notifications
router.get('/notifications', async (req, res) => {
  res.json({ notifications: req.user.notifications?.slice(0, 30) || [] });
});

// POST /api/user/notifications/read
router.post('/notifications/read', async (req, res, next) => {
  try {
    await User.updateOne(
      { _id: req.user._id },
      { $set: { 'notifications.$[].read': true } }
    );
    res.json({ message: 'Marked as read' });
  } catch (err) {
    next(err);
  }
});

export default router;
