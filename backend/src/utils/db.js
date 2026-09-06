import dns from "dns";
import mongoose from "mongoose";

/**
 * Atlas mongodb+srv:// lookups sometimes fail on local/dev networks
 * or certain hosts when the default DNS can't resolve SRV records.
 * Google DNS is a reliable workaround you've used before.
 */
dns.setServers(["8.8.8.8", "8.8.4.4"]);

export async function connectDb() {
  const uri = process.env.MONGODB_URI;
  if (!uri) {
    console.error("Missing MONGODB_URI in environment");
    process.exit(1);
  }

  mongoose.set("strictQuery", true);

  try {
    await mongoose.connect(uri, {
      serverSelectionTimeoutMS: 15000,
    });
    console.log("MongoDB connected");
  } catch (err) {
    console.error("MongoDB connection failed:", err.message);
    console.error(
      "If this is an SRV/DNS error, dns.setServers([8.8.8.8, 8.8.4.4]) is already applied. " +
        "Also check Atlas Network Access (allow 0.0.0.0/0 on Render) and the URI."
    );
    process.exit(1);
  }
}
