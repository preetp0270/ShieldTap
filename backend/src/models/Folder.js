import mongoose from 'mongoose';

const folderSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'User',
      required: true,
      index: true,
    },
    name: {
      type: String,
      required: true,
      trim: true,
      maxlength: 100,
    },
    // null = root / main folder
    parentId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: 'Folder',
      default: null,
      index: true,
    },
    // Main folders (parentId == null) can be locked
    isLocked: {
      type: Boolean,
      default: false,
    },
    // Color / icon optional later
    color: { type: String, default: '#6366F1' },
  },
  { timestamps: true }
);

// Compound index for fast listing
folderSchema.index({ userId: 1, parentId: 1 });

export default mongoose.model('Folder', folderSchema);
