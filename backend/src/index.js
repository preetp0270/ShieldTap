import "dotenv/config";
import express from "express";
import cors from "cors";
import helmet from "helmet";
import rateLimit from "express-rate-limit";
import { connectDb } from "./utils/db.js";
import { configureCloudinary } from "./services/cloudinary.js";
import authRoutes from "./routes/auth.js";
import userRoutes from "./routes/user.js";
import fileRoutes from "./routes/files.js";
import { errorHandler } from "./middleware/errorHandler.js";

const app = express();
// Render injects PORT; bind all interfaces
const PORT = Number(process.env.PORT) || 4000;
const HOST = process.env.HOST || "0.0.0.0";

configureCloudinary();

app.use(helmet());
app.use(
  cors({
    origin: true,
    credentials: true,
  })
);
app.use(express.json({ limit: "1mb" }));

app.use(
  "/api/auth",
  rateLimit({ windowMs: 15 * 60 * 1000, max: 40, standardHeaders: true })
);
app.use(
  "/api",
  rateLimit({ windowMs: 60 * 1000, max: 120, standardHeaders: true })
);

// Render health check
app.get("/", (_req, res) => {
  res.json({
    ok: true,
    service: "shieldtap-api",
    env: process.env.NODE_ENV || "development",
  });
});

app.get("/health", (_req, res) =>
  res.json({
    ok: true,
    service: "shieldtap-api",
    cloudinary: Boolean(process.env.CLOUDINARY_CLOUD_NAME),
    mongo: Boolean(process.env.MONGODB_URI),
  })
);

app.use("/api/auth", authRoutes);
app.use("/api/user", userRoutes);
app.use("/api/files", fileRoutes);

app.use(errorHandler);

await connectDb();
app.listen(PORT, HOST, () => {
  console.log(`API listening on http://${HOST}:${PORT}`);
});
