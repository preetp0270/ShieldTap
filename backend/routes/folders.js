import { Router } from 'express';
import { z } from 'zod';
import Folder from '../models/Folder.js';
import VaultItem from '../models/VaultItem.js';
import { requireAuth } from '../middleware/auth.js';

const router = Router();
router.use(requireAuth);

const createSchema = z.object({
  name: z.string().min(1).max(100),
  parentId: z.string().nullable().optional(),
});

// GET /api/folders?parentId=xxx  (null = root)
router.get('/', async (req, res, next) => {
  try {
    const parentId = req.query.parentId === 'null' || !req.query.parentId ? null : req.query.parentId;
    const folders = await Folder.find({
      userId: req.user._id,
      parentId,
    }).sort({ name: 1 });

    res.json({ folders });
  } catch (err) {
    next(err);
  }
});

// POST /api/folders  – create folder
router.post('/', async (req, res, next) => {
  try {
    const data = createSchema.parse(req.body);
    const parentId = data.parentId || null;

    const folder = await Folder.create({
      userId: req.user._id,
      name: data.name.trim(),
      parentId,
    });

    res.status(201).json({ folder });
  } catch (err) {
    if (err.name === 'ZodError') {
      return res.status(400).json({ error: err.errors[0].message });
    }
    next(err);
  }
});

// PATCH /api/folders/:id  – rename / lock / unlock
router.patch('/:id', async (req, res, next) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, userId: req.user._id });
    if (!folder) return res.status(404).json({ error: 'Folder not found' });

    if (req.body.name !== undefined) {
      folder.name = String(req.body.name).trim().slice(0, 100);
    }

    // Accept boolean or string; only root folders (no parent) can be locked
    const isRoot = folder.parentId == null || folder.parentId === undefined;
    if (req.body.isLocked !== undefined) {
      if (!isRoot) {
        return res.status(400).json({ error: 'Only main (root) folders can be locked' });
      }
      const v = req.body.isLocked;
      folder.isLocked = v === true || v === 'true' || v === 1 || v === '1';
    }

    if (req.body.color) folder.color = req.body.color;

    await folder.save();
    res.json({ folder });
  } catch (err) {
    next(err);
  }
});

// DELETE /api/folders/:id  – cascade delete children + items
router.delete('/:id', async (req, res, next) => {
  try {
    const folder = await Folder.findOne({ _id: req.params.id, userId: req.user._id });
    if (!folder) return res.status(404).json({ error: 'Folder not found' });

    const toDelete = [folder._id];
    let queue = [folder._id];
    while (queue.length) {
      const children = await Folder.find({ parentId: { $in: queue }, userId: req.user._id }).select('_id');
      const ids = children.map((c) => c._id);
      toDelete.push(...ids);
      queue = ids;
    }

    await VaultItem.deleteMany({ folderId: { $in: toDelete }, userId: req.user._id });
    await Folder.deleteMany({ _id: { $in: toDelete }, userId: req.user._id });

    res.json({ message: 'Folder and contents deleted' });
  } catch (err) {
    next(err);
  }
});

export default router;
