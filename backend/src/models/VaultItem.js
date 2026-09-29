import mongoose from 'mongoose';

/**
 * VaultItem can be:
 *  - password  → key + value (encrypted on client ideally)
 *  - file      → Cloudinary / storage URL
 *  - image     → Cloudinary URL
 */
const vaultItemSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    folderId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Folder',
      required: true,
      index: true,
    },
    type: {
      type: String,
      enum: ['password', 'file', 'image'],
      required: true,
    },
    // Password
    key: { type: String },      // e.g. "Gmail"
    value: { type: String },    // the actual password (store encrypted if possible)

    // File / Image
    title: { type: String },
    url: { type: String },      // Cloudinary secure URL
    publicId: { type: String }, // for deletion
    mimeType: { type: String },
    size: { type: Number },
  },
  { timestamps: true }
);

vaultItemSchema.index({ userId: 1, folderId: 1, type: 1 });

export default mongoose.model('VaultItem', vaultItemSchema);
