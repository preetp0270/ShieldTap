import mongoose from "mongoose";

const cardSchema = new mongoose.Schema(
  {
    uid: { type: String, required: true },
    label: { type: String, default: "" },
    isPrimary: { type: Boolean, default: false },
  },
  { _id: false }
);

const userSchema = new mongoose.Schema(
  {
    username: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      lowercase: true,
      minlength: 3,
      maxlength: 32,
    },
    email: {
      type: String,
      required: true,
      unique: true,
      trim: true,
      lowercase: true,
    },
    passwordHash: { type: String, required: true },
    displayName: { type: String, default: "" },
    photoUrl: { type: String, default: "" },
    cards: { type: [cardSchema], default: [] },
    mpinHash: { type: String, default: null },
    emailVerified: { type: Boolean, default: false },
    // OTP for reset / verify
    otpHash: { type: String, default: null },
    otpExpiresAt: { type: Date, default: null },
    otpPurpose: { type: String, enum: ["verify", "reset", null], default: null },
  },
  { timestamps: true }
);

export const User = mongoose.model("User", userSchema);
