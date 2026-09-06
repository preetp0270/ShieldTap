import nodemailer from "nodemailer";

let transporter;

function getTransporter() {
  if (transporter) return transporter;
  if (!process.env.SMTP_HOST || !process.env.SMTP_USER) {
    console.warn("SMTP not configured – emails will be logged to console only");
    return null;
  }
  transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT || 587),
    secure: false,
    auth: {
      user: process.env.SMTP_USER,
      pass: process.env.SMTP_PASS,
    },
  });
  return transporter;
}

export async function sendMail({ to, subject, text, html }) {
  const from = process.env.MAIL_FROM || process.env.SMTP_USER;
  const tx = getTransporter();
  if (!tx) {
    console.log("[mail:dev]", { to, subject, text });
    return { dev: true };
  }
  return tx.sendMail({ from, to, subject, text, html });
}

export async function sendWelcomeEmail(email, username) {
  return sendMail({
    to: email,
    subject: "Welcome to NFC Security",
    text: `Hi ${username},\n\nYour account was created successfully. Protect your vaults with your NFC cards and keep your MPIN private.\n\n— NFC Security`,
    html: `<p>Hi <b>${username}</b>,</p><p>Your account was created successfully. Protect your vaults with your NFC cards and keep your MPIN private.</p><p>— NFC Security</p>`,
  });
}

export async function sendOtpEmail(email, otp, purpose) {
  const action = purpose === "reset" ? "password reset" : "email verification";
  return sendMail({
    to: email,
    subject: `Your NFC Security code: ${otp}`,
    text: `Your one-time code for ${action} is ${otp}. It expires in 10 minutes. If you did not request this, ignore this email.`,
    html: `<p>Your one-time code for <b>${action}</b> is:</p><p style="font-size:24px;letter-spacing:4px"><b>${otp}</b></p><p>It expires in 10 minutes.</p>`,
  });
}
