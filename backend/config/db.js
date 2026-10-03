import mongoose from 'mongoose';

export async function connectDB() {
  const uri = process.env.MONGODB_URI;
  if (!uri || uri.includes('USER:PASSWORD') || uri.includes('<password>')) {
    throw new Error(
      'MONGODB_URI is missing or still a placeholder. Copy .env.example to .env and set your real Atlas URI.'
    );
  }

  mongoose.set('strictQuery', true);

  try {
    await mongoose.connect(uri, {
      serverSelectionTimeoutMS: 10000,
      connectTimeoutMS: 10000,
      socketTimeoutMS: 20000,
    });
    console.log('✅ MongoDB connected');
  } catch (err) {
    const msg = err?.message || String(err);
    if (msg.includes('ENOTFOUND') || msg.includes('querySrv')) {
      console.error(`
❌ MongoDB DNS lookup failed (ENOTFOUND).

Your MONGODB_URI hostname could not be resolved.
Common causes:
  1. Typo in the cluster host (check Atlas → Connect → Drivers)
  2. Wrong / old cluster that was deleted
  3. Network / DNS blocking *.mongodb.net

Fix:
  • Open https://cloud.mongodb.com → your cluster → Connect → Drivers
  • Copy the full URI again
  • Replace <password> with the DB user password (URL-encode special chars)
  • Set database name to shieldtap
  • Paste into backend/.env as MONGODB_URI=...

Example shape:
  mongodb+srv://myuser:mypass@cluster0.abc12.mongodb.net/shieldtap?retryWrites=true&w=majority
`);
    }
    throw err;
  }
}
