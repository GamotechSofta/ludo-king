import dotenv from "dotenv";
import path from "path";

dotenv.config({ path: path.join(__dirname, "..", ".env") });

const mongoUrl = process.env.MONGO_URL?.trim();

if (!mongoUrl) {
  throw new Error("MONGO_URL is required in backend/.env");
}

export const config = {
  port: Number(process.env.PORT) || 3000,
  mongoUrl,
  sessionSecret: process.env.SESSION_SECRET?.trim() || "ludo-dev-session-secret",
  redisUrl: process.env.REDIS_URL?.trim() || "",
  clientUrl: process.env.CLIENT_URL?.trim() || "http://localhost:3043",
  google: {
    key: process.env.GOOGLE_KEY?.trim() || "",
    secret: process.env.GOOGLE_SECRET?.trim() || "",
  },
  github: {
    key: process.env.GITHUB_KEY?.trim() || "",
    secret: process.env.GITHUB_SECRET?.trim() || "",
  },
};
