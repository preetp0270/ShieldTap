import { Router } from "express";
import multer from "multer";
import { requireAuth } from "../middleware/auth.js";
import { VaultFile } from "../models/VaultFile.js";
import { uploadBuffer, destroyCloudinaryAsset } from "../services/cloudinary.js";

const router = Router();
router.use(requireAuth);

// Memory storage – stream straight to Cloudinary (max 25 MB)
const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 25 * 1024 * 1024 },
});

/**
 * POST /api/files/upload
 * multipart: file (required), title, spaceId, folderId, itemId (optional)
 * → uploads to Cloudinary, saves secure URL in MongoDB
 */
router.post("/upload", upload.single("file"), async (req, res, next) => {
  try {
    if (!req.file) {
      return res.status(400).json({ error: "file field is required (multipart/form-data)" });
    }

    const { title, spaceId, folderId, itemId } = req.body;
    const originalName = req.file.originalname || "file";
    const mimeType = req.file.mimetype || "application/octet-stream";

    const result = await uploadBuffer(req.file.buffer, {
      folder: `${process.env.CLOUDINARY_FOLDER || "nfcsecurity"}/${req.user._id}`,
      originalName,
      mimeType,
    });

    const doc = await VaultFile.create({
      userId: req.user._id,
      spaceId: spaceId || "",
      folderId: folderId || "",
      itemId: itemId || "",
      title: title || originalName,
      fileName: originalName,
      mimeType,
      size: req.file.size || result.bytes || 0,
      cloudinaryPublicId: result.public_id,
      cloudinaryUrl: result.secure_url,
      resourceType: result.resource_type || "image",
      format: result.format || "",
    });

    res.status(201).json({
      id: doc._id,
      title: doc.title,
      fileName: doc.fileName,
      mimeType: doc.mimeType,
      size: doc.size,
      url: doc.cloudinaryUrl,
      publicId: doc.cloudinaryPublicId,
      resourceType: doc.resourceType,
      spaceId: doc.spaceId,
      folderId: doc.folderId,
      itemId: doc.itemId,
      createdAt: doc.createdAt,
    });
  } catch (e) {
    next(e);
  }
});

/**
 * GET /api/files
 * query: spaceId? – list user's files (links from Mongo)
 */
router.get("/", async (req, res, next) => {
  try {
    const filter = { userId: req.user._id };
    if (req.query.spaceId) filter.spaceId = String(req.query.spaceId);
    const files = await VaultFile.find(filter).sort({ createdAt: -1 }).lean();
    res.json({
      files: files.map((f) => ({
        id: f._id,
        title: f.title,
        fileName: f.fileName,
        mimeType: f.mimeType,
        size: f.size,
        url: f.cloudinaryUrl,
        publicId: f.cloudinaryPublicId,
        resourceType: f.resourceType,
        spaceId: f.spaceId,
        folderId: f.folderId,
        itemId: f.itemId,
        createdAt: f.createdAt,
      })),
    });
  } catch (e) {
    next(e);
  }
});

/**
 * GET /api/files/:id – single file metadata + Cloudinary link
 */
router.get("/:id", async (req, res, next) => {
  try {
    const file = await VaultFile.findOne({
      _id: req.params.id,
      userId: req.user._id,
    }).lean();
    if (!file) return res.status(404).json({ error: "File not found" });
    res.json({
      id: file._id,
      title: file.title,
      fileName: file.fileName,
      mimeType: file.mimeType,
      size: file.size,
      url: file.cloudinaryUrl,
      publicId: file.cloudinaryPublicId,
      resourceType: file.resourceType,
      spaceId: file.spaceId,
      folderId: file.folderId,
      itemId: file.itemId,
      createdAt: file.createdAt,
    });
  } catch (e) {
    next(e);
  }
});

/**
 * DELETE /api/files/:id – remove from Cloudinary + Mongo
 */
router.delete("/:id", async (req, res, next) => {
  try {
    const file = await VaultFile.findOne({
      _id: req.params.id,
      userId: req.user._id,
    });
    if (!file) return res.status(404).json({ error: "File not found" });

    try {
      await destroyCloudinaryAsset(file.cloudinaryPublicId, file.resourceType);
    } catch (err) {
      console.warn("Cloudinary destroy failed (continuing Mongo delete):", err.message);
    }

    await file.deleteOne();
    res.json({ ok: true });
  } catch (e) {
    next(e);
  }
});

export default router;
