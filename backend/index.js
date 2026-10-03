import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { connectDB } from './config/db.js';
import { initCloudinary } from './config/cloudinary.js';
import { errorHandler } from './middleware/errorHandler.js';
import authRoutes from './routes/auth.js';
import folderRoutes from './routes/folders.js';
import vaultRoutes from './routes/vault.js';
import userRoutes from './routes/user.js';
import dns from 'dns';
const app = express();
const PORT = process.env.PORT || 5000;
dns.setServers(['8.8.8.8', '8.8.4.4']);

app.use(helmet());
app.use(
  cors({
    origin: process.env.CORS_ORIGIN === '*' ? true : process.env.CORS_ORIGIN?.split(',') || true,
    credentials: true,
  })
);
app.use(express.json({ limit: '2mb' }));
app.use(express.urlencoded({ extended: true }));

app.get('/health', (req, res) => res.json({ status: 'ok', time: new Date().toISOString() }));

app.use('/api/auth', authRoutes);
app.use('/api/folders', folderRoutes);
app.use('/api/vault', vaultRoutes);
app.use('/api/user', userRoutes);

app.use(errorHandler);

async function start() {
  await connectDB();
  initCloudinary();
  app.listen(PORT, () => {
    console.log(`🚀 ShieldTap API running on http://localhost:${PORT}`);
  });
}

start().catch((err) => {
  console.error('Failed to start:', err);
  process.exit(1);
});
