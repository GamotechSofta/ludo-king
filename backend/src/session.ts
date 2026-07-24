import session from "express-session";
import { createClient } from "redis";
import RedisStore from "connect-redis";
import { config } from "./config";

export async function createSessionMiddleware() {
  const base = {
    secret: config.sessionSecret,
    resave: false,
    saveUninitialized: false,
    cookie: {
      secure: false,
      httpOnly: true,
      maxAge: 1000 * 60 * 60 * 24 * 7,
    },
  };

  if (!config.redisUrl) {
    console.log("REDIS_URL not set — using in-memory session store");
    return session(base);
  }

  const redisClient = createClient({ url: config.redisUrl });
  redisClient.on("error", (err) => console.error("Redis error:", err));
  await redisClient.connect();
  console.log("Redis connected");

  return session({
    ...base,
    store: new RedisStore({ client: redisClient }),
  });
}
