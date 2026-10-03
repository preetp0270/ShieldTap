import { verifyToken } from '../utils/jwt.js';
import Session from '../models/Session.js';
import User from '../models/User.js';
import { fiveDaysFromNow } from '../utils/jwt.js';

export async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization;
    if (!header?.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid token' });
    }
    const token = header.slice(7);

    let decoded;
    try {
      decoded = verifyToken(token);
    } catch {
      return res.status(401).json({ error: 'Token expired or invalid' });
    }

    const session = await Session.findOne({ token, userId: decoded.userId });
    if (!session) {
      return res.status(401).json({ error: 'Session not found' });
    }
    if (session.expiresAt < new Date()) {
      await Session.deleteOne({ _id: session._id });
      return res.status(401).json({ error: 'Session expired (5 days inactive)' });
    }

    // Extend session by 5 days on every successful request
    session.expiresAt = fiveDaysFromNow();
    session.lastActiveAt = new Date();
    await session.save();

    const user = await User.findById(decoded.userId).select('-passwordHash');
    if (!user) {
      return res.status(401).json({ error: 'User not found' });
    }

    req.user = user;
    req.session = session;
    next();
  } catch (err) {
    next(err);
  }
}
