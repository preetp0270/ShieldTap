import { Router } from 'express';
import multer from 'multer';
import { z } from 'zod';
import VaultItem from '../models/VaultItem.js';
import Folder from '../models/Folder.js';
import { requireAuth } from '../middleware/auth.js';
import cloudinary from '../config/cloudinary.js';

const router = Router();
router.use(requireAuth);

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: 20 * 1024 * 1024 }, // 20 MB
});

// GET /api/vault?folderId=xxx
router.get('/', async (req, res, next) => {
  try {
    const { folderId } = req.query;
    if (!folderId) return res.status(400).json({ error: 'folderId required' });

    const folder = await Folder.findOne({ _id: folderId, userId: req.user._id });
    if (!folder) return res.status(404).json({ error: 'Folder not found' });

    const items = await VaultItem.find({
      userId: req.user._id,
      folderId,
    }).sort({ createdAt: -1 });

    res.json({ items });
  } catch (err) {
    next(err);
  }
});

// POST /api/vault/password
router.post('/password', async (req, res, next) => {
  try {
    const schema = z.object({
      folderId: z.string(),
      key: z.string().min(1).max(100),
      value: z.string().min(1).max(500),
    });
    const data = schema.parse(req.body);

    const folder = await Folder.findOne({ _id: data.folderId, userId: req.user._id });
    if (!folder) return res.status(404).json({ error: 'Folder not found' });

    const item = await VaultItem.create({
      userId: req.user._id,
      folderId: data.folderId,
      type: 'password',
      key: data.key,
      value: data.value,
    });

    res.status(201).json({ item });
  } catch (err) {
    if (err.name === 'ZodError') {
      return res.status(400).json({ error: err.errors[0].message });
    }
    next(err);
  }
});

// POST /api/vault/upload  (image or file)
router.post('/upload', upload.single('file'), async (req, res, next) => {
  try {
    const { folderId, type, title } = req.body; // type = image | file
    if (!folderId || !req.file) {
      return res.status(400).json({ error: 'folderId and file required' });
    }
    if (!['image', 'file'].includes(type)) {
      return res.status(400).json({ error: 'type must be image or file' });
    }

    const folder = await Folder.findOne({ _id: folderId, userId: req.user._id });
    if (!folder) return res.status(404).json({ error: 'Folder not found' });

    // Upload to Cloudinary
    const result = await new Promise((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        {
          folder: `shieldtap/${req.user._id}`,
          resource_type: type === 'image' ? 'image' : 'auto',
        },
        (err, result) => (err ? reject(err) : resolve(result))
      );
      stream.end(req.file.buffer);
    });

    const item = await VaultItem.create({
      userId: req.user._id,
      folderId,
      type,
      title: title || req.file.originalname,
      url: result.secure_url,
      publicId: result.public_id,
      mimeType: req.file.mimetype,
      size: req.file.size,
    });

    res.status(201).json({ item });
  } catch (err) {
    next(err);
  }
});

// DELETE /api/vault/:id
router.delete('/:id', async (req, res, next) => {
  try {
    const item = await VaultItem.findOne({ _id: req.params.id, userId: req.user._id });
    if (!item) return res.status(404).json({ error: 'Item not found' });

    if (item.publicId) {
      try {
        await cloudinary.uploader.destroy(item.publicId, {
          resource_type: item.type === 'image' ? 'image' : 'raw',
        });
      } catch (e) {
        console.warn('Cloudinary delete failed', e.message);
      }
    }

    await item.deleteOne();
    res.json({ message: 'Deleted' });
  } catch (err) {
    next(err);
  }
});

// PATCH /api/vault/:id  (update password key/value or title)
router.patch('/:id', async (req, res, next) => {
  try {
    const item = await VaultItem.findOne({ _id: req.params.id, userId: req.user._id });
    if (!item) return res.status(404).json({ error: 'Item not found' });

    if (item.type === 'password') {
      if (req.body.key !== undefined) item.key = String(req.body.key).slice(0, 100);
      if (req.body.value !== undefined) item.value = String(req.body.value).slice(0, 500);
    } else {
      if (req.body.title !== undefined) item.title = String(req.body.title).slice(0, 200);
    }
    await item.save();
    res.json({ item });
  } catch (err) {
    next(err);
  }
});

export default router;
