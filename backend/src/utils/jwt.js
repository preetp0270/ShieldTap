import jwt from 'jsonwebtoken';

const SECRET = () => process.env.JWT_SECRET || 'dev-secret-change-me';
const EXPIRES = () => process.env.JWT_EXPIRES_IN || '5d';

export function signToken(payload) {
  return jwt.sign(payload, SECRET(), { expiresIn: EXPIRES() });
}

export function verifyToken(token) {
  return jwt.verify(token, SECRET());
}

/** Returns Date = now + 5 days */
export function fiveDaysFromNow() {
  return new Date(Date.now() + 5 * 24 * 60 * 60 * 1000);
}
