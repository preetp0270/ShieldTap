import { Router } from 'express';
import { z } from 'zod';
import rateLimit from 'express-rate-limit';
import User from '../models/User.js';
import Session from '../models/Session.js';
import { signToken, fiveDaysFromNow } from '../utils/jwt.js';
import { requireAuth } from '../middleware/auth.js';

const router = Router();

const loginLimiter = rateLimit({
  windowMs: 5 * 60 * 1000, // 5 min
  max: 10,
  message: { error: 'Too many login attempts. Try again in 5 minutes.' },
});

const registerSchema = z.object({
  username: z.string().min(3).max(30).regex(/^[a-zA-Z0-9_]+$/),
  email: z.string().email(),
  phone: z.string().min(8).max(15),
  password: z.string().min(6).max(100),
});

const loginSchema = z.object({
  username: z.string().min(1),
  password: z.string().min(1),
});

// POST /api/auth/register
router.post('/register', async (req, res, next) => {
  try {
    const data = registerSchema.parse(req.body);

    const exists = await User.findOne({
      $or: [{ username: data.username.toLowerCase() }, { email: data.email.toLowerCase() }],
    });
    if (exists) {
      return res.status(409).json({ error: 'Username or email already taken' });
    }

    const passwordHash = await User.hashPassword(data.password);
    const user = await User.create({
      username: data.username.toLowerCase(),
      email: data.email.toLowerCase(),
      phone: data.phone,
      passwordHash,
      displayName: data.username,
      notifications: [
        {
          type: 'register',
          message: 'Welcome to ShieldTap! Your vault is ready.',
        },
      ],
    });

    // After register → client should go to login page (no auto-login)
    res.status(201).json({
      message: 'Registered successfully. Please log in.',
      user: {
        id: user._id,
        username: user.username,
        email: user.email,
      },
    });
  } catch (err) {
    if (err.name === 'ZodError') {
      return res.status(400).json({ error: err.errors[0].message });
    }
    next(err);
  }
});

// POST /api/auth/login
router.post('/login', loginLimiter, async (req, res, next) => {
  try {
    const data = loginSchema.parse(req.body);
    const user = await User.findOne({ username: data.username.toLowerCase() });
    if (!user || !(await user.comparePassword(data.password))) {
      return res.status(401).json({ error: 'Invalid username or password' });
    }

    const token = signToken({ userId: user._id.toString() });
    const expiresAt = fiveDaysFromNow();

    await Session.create({
      userId: user._id,
      token,
      expiresAt,
      deviceInfo: req.headers['user-agent'] || 'android',
    });

    // Add login notification
    user.notifications.unshift({
      type: 'login',
      message: `Logged in at ${new Date().toLocaleString()}`,
    });
    if (user.notifications.length > 50) user.notifications = user.notifications.slice(0, 50);
    await user.save();

    res.json({
      token,
      expiresAt,
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
    if (err.name === 'ZodError') {
      return res.status(400).json({ error: err.errors[0].message });
    }
    next(err);
  }
});

// POST /api/auth/logout
router.post('/logout', requireAuth, async (req, res, next) => {
  try {
    await Session.deleteOne({ _id: req.session._id });
    res.json({ message: 'Logged out' });
  } catch (err) {
    next(err);
  }
});

// GET /api/auth/me  – also extends session
router.get('/me', requireAuth, async (req, res) => {
  res.json({
    user: {
      id: req.user._id,
      username: req.user.username,
      email: req.user.email,
      phone: req.user.phone,
      displayName: req.user.displayName,
      avatarUrl: req.user.avatarUrl,
      backgroundUrl: req.user.backgroundUrl,
      notifications: req.user.notifications?.slice(0, 20) || [],
    },
    expiresAt: req.session.expiresAt,
  });
});

export default router;
