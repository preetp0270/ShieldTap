import mongoose from "mongoose";

/**
 * Metadata for a file stored on Cloudinary.
 * The binary lives on Cloudinary; Mongo only keeps the secure URL + ids.
 */
const vaultFileSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },
    // Optional local/Android vault identifiers (for sync)
    spaceId: { type: String, default: "", index: true },
    folderId: { type: String, default: "" },
    itemId: { type: String, default: "" },

    title: { type: String, default: "" },
    fileName: { type: String, required: true },
    mimeType: { type: String, default: "application/octet-stream" },
    size: { type: Number, default: 0 },

    // Cloudinary fields
    cloudinaryPublicId: { type: String, required: true },
    cloudinaryUrl: { type: String, required: true }, // secure_url
    resourceType: { type: String, default: "image" }, // image | video | raw
    format: { type: String, default: "" },
  },
  { timestamps: true }
);

vaultFileSchema.index({ userId: 1, spaceId: 1 });

export const VaultFile = mongoose.model("VaultFile", vaultFileSchema);
