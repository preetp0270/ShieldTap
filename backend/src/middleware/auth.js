import crypto from "crypto";
import { verifyToken } from "../utils/jwt.js";
import { Session } from "../models/Session.js";
import { User } from "../models/User.js";

export async function requireAuth(req, res, next) {
  try {
    const header = req.headers.authorization || "";
    const token = header.startsWith("Bearer ") ? header.slice(7) : null;
    if (!token) return res.status(401).json({ error: "Unauthorized" });

    const payload = verifyToken(token);
    const tokenHash = crypto.createHash("sha256").update(token).digest("hex");
    const session = await Session.findOne({
      userId: payload.sub,
      tokenHash,
      revoked: false,
      expiresAt: { $gt: new Date() },
    });
    if (!session) return res.status(401).json({ error: "Session expired" });

    const user = await User.findById(payload.sub).select("-passwordHash -otpHash -mpinHash");
    if (!user) return res.status(401).json({ error: "User not found" });

    req.user = user;
    req.session = session;
    req.token = token;
    next();
  } catch {
    return res.status(401).json({ error: "Invalid token" });
  }
}
