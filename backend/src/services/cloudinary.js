import { v2 as cloudinary } from "cloudinary";
import { Readable } from "stream";

export function configureCloudinary() {
  const { CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET } = process.env;
  if (!CLOUDINARY_CLOUD_NAME || !CLOUDINARY_API_KEY || !CLOUDINARY_API_SECRET) {
    console.warn("Cloudinary not configured – file uploads will fail until .env is set");
    return false;
  }
  cloudinary.config({
    cloud_name: CLOUDINARY_CLOUD_NAME,
    api_key: CLOUDINARY_API_KEY,
    api_secret: CLOUDINARY_API_SECRET,
    secure: true,
  });
  return true;
}

/**
 * Upload a Buffer to Cloudinary.
 * resource_type "auto" handles image / video / raw (pdf, txt, …).
 */
export function uploadBuffer(buffer, { folder, publicId, originalName, mimeType }) {
  return new Promise((resolve, reject) => {
    const stream = cloudinary.uploader.upload_stream(
      {
        folder: folder || process.env.CLOUDINARY_FOLDER || "nfcsecurity",
        public_id: publicId,
        resource_type: "auto",
        use_filename: true,
        unique_filename: true,
        overwrite: false,
        context: originalName ? `original=${originalName}` : undefined,
      },
      (err, result) => {
        if (err) return reject(err);
        resolve(result);
      }
    );
    Readable.from(buffer).pipe(stream);
  });
}

export async function destroyCloudinaryAsset(publicId, resourceType = "image") {
  // resource_type may be image | video | raw
  return cloudinary.uploader.destroy(publicId, {
    resource_type: resourceType || "image",
    invalidate: true,
  });
}

export { cloudinary };
