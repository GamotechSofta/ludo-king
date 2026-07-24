import { Router, Request, Response } from "express";
import passport from "passport";
import { config } from "../config";

const router = Router();

router.get("/api/me", (req: Request, res: Response) => {
  if (!req.user) {
    return res.status(401).json({ authenticated: false });
  }

  const user = req.user as {
    id: string;
    name: string;
    email?: string;
    avatar?: string;
    provider: string;
  };

  return res.json({
    authenticated: true,
    user: {
      id: user.id,
      name: user.name,
      email: user.email,
      avatar: user.avatar,
      provider: user.provider,
    },
  });
});

router.get("/api/logout", (req: Request, res: Response) => {
  // passport@0.4 types omit the callback overload
  (req.logout as (cb?: (err?: unknown) => void) => void)(() => {
    req.session.destroy(() => {
      res.clearCookie("connect.sid");
      res.json({ ok: true });
    });
  });
});

router.get("/auth/options", (_req: Request, res: Response) => {
  res.json({
    google: Boolean(config.google.key && config.google.secret),
    github: Boolean(config.github.key && config.github.secret),
  });
});

if (config.google.key && config.google.secret) {
  router.get("/auth/google", passport.authenticate("google", { scope: ["email", "profile"] }));
  router.get(
    "/auth/google/callback",
    passport.authenticate("google", { failureRedirect: config.clientUrl }),
    (_req, res) => res.redirect(config.clientUrl)
  );
}

if (config.github.key && config.github.secret) {
  router.get("/auth/github", passport.authenticate("github", { scope: ["user:email"] }));
  router.get(
    "/auth/github/callback",
    passport.authenticate("github", { failureRedirect: config.clientUrl }),
    (_req, res) => res.redirect(config.clientUrl)
  );
}

export default router;
