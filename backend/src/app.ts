import express from "express";
import http from "http";
import cors from "cors";
import helmet from "helmet";
import compression from "compression";
import cookieParser from "cookie-parser";
import path from "path";
import { config } from "./config";
import { connectDb } from "./db";
import { createSessionMiddleware } from "./session";
import { configurePassport } from "./passport";
import authRoutes from "./routes/auth";
import { initSocket } from "./socket";

async function bootstrap() {
  console.log("Starting backend...");
  await connectDb();

  const app = express();
  const server = http.createServer(app);
  console.log("Creating session middleware...");
  const sessionMiddleware = await createSessionMiddleware();
  console.log("Configuring passport...");
  const passport = configurePassport();

  app.set("trust proxy", 1);
  app.use(
    cors({
      origin: config.clientUrl,
      credentials: true,
    })
  );
  app.use(helmet({ contentSecurityPolicy: false }));
  app.use(compression());
  app.use(express.json());
  app.use(cookieParser());
  app.use(sessionMiddleware);
  app.use(passport.initialize());
  app.use(passport.session());
  app.use(authRoutes);

  app.get("/health", (_req, res) => {
    res.json({ ok: true, mongo: true, redis: Boolean(config.redisUrl) });
  });

  const publicDir = path.join(__dirname, "public");
  app.use(express.static(publicDir));

  initSocket(server);

  server.listen(config.port, () => {
    console.log(`Backend running on http://localhost:${config.port}`);
    console.log(`Frontend expected at ${config.clientUrl}`);
  });
}

bootstrap().catch((err) => {
  console.error("Failed to start backend:", err);
  process.exit(1);
});
