import passport from "passport";
import { Strategy as GoogleStrategy } from "passport-google-oauth2";
import { Strategy as GitHubStrategy } from "passport-github2";
import { config } from "./config";
import { User } from "./models/User";

type OAuthProfile = {
  id: string;
  displayName?: string;
  emails?: { value: string }[];
  photos?: { value: string }[];
};

async function upsertOAuthUser(
  provider: string,
  profile: OAuthProfile,
  done: (err: unknown, user?: Express.User) => void
) {
  try {
    const providerId = profile.id;
    const name = profile.displayName || `${provider}-user`;
    const email = profile.emails?.[0]?.value;
    const avatar = profile.photos?.[0]?.value;

    let user = await User.findOne({ provider, providerId });
    if (!user) {
      user = await User.create({ provider, providerId, name, email, avatar });
    }

    done(null, user);
  } catch (err) {
    done(err);
  }
}

export function configurePassport() {
  passport.serializeUser((user: any, done) => {
    done(null, user.id);
  });

  passport.deserializeUser(async (id: string, done) => {
    try {
      const user = await User.findById(id);
      done(null, user || false);
    } catch (err) {
      done(err);
    }
  });

  if (config.google.key && config.google.secret) {
    passport.use(
      new GoogleStrategy(
        {
          clientID: config.google.key,
          clientSecret: config.google.secret,
          callbackURL: "/auth/google/callback",
          passReqToCallback: true,
        },
        (_req: unknown, _accessToken: string, _refreshToken: string, profile: OAuthProfile, done: any) => {
          void upsertOAuthUser("google", profile, done);
        }
      )
    );
    console.log("Google OAuth enabled");
  } else {
    console.log("Google OAuth skipped (GOOGLE_KEY / GOOGLE_SECRET empty)");
  }

  if (config.github.key && config.github.secret) {
    passport.use(
      new GitHubStrategy(
        {
          clientID: config.github.key,
          clientSecret: config.github.secret,
          callbackURL: "/auth/github/callback",
        },
        (_accessToken: string, _refreshToken: string, profile: OAuthProfile, done: any) => {
          void upsertOAuthUser("github", profile, done);
        }
      )
    );
    console.log("GitHub OAuth enabled");
  } else {
    console.log("GitHub OAuth skipped (GITHUB_KEY / GITHUB_SECRET empty)");
  }

  return passport;
}
