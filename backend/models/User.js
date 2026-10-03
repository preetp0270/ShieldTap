import mongoose from 'mongoose';
import bcrypt from 'bcryptjs';

const userSchema = new mongoose.Schema(
  {
    username: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      lowercase: true,
      minlength: 3,
      maxlength: 30,
    },
    email: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      lowercase: true,
    },
    phone: {
      type: String,
      required: true,
      trim: true,
    },
    passwordHash: {
      type: String,
      required: true,
    },
    // Profile
    displayName: { type: String, default: '' },
    avatarUrl: { type: String, default: '' },
    backgroundUrl: { type: String, default: '' },
    // Notifications log (simple)
    notifications: [
      {
        type: { type: String }, // login | register | profile_update | lock
        message: String,
        createdAt: { type: Date, default: Date.now },
        read: { type: Boolean, default: false },
      },
    ],
  },
  { timestamps: true }
);

userSchema.methods.comparePassword = async function (plain) {
  return bcrypt.compare(plain, this.passwordHash);
};

userSchema.statics.hashPassword = async function (plain) {
  return bcrypt.hash(plain, 12);
};

export default mongoose.model('User', userSchema);
