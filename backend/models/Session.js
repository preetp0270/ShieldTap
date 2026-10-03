import mongoose from 'mongoose';

const sessionSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    token: {
      type: String,
      required: true,
      unique: true,
    },
    // Absolute expiry (5 days from last activity)
    // TTL index is declared once below — do not also set index: true here
    expiresAt: {
      type: Date,
      required: true,
    },
    deviceInfo: {
      type: String,
      default: 'android',
    },
    lastActiveAt: {
      type: Date,
      default: Date.now,
    },
  },
  { timestamps: true }
);

// Auto-remove expired sessions (single TTL index)
sessionSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });

export default mongoose.model('Session', sessionSchema);
