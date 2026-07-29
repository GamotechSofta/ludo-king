const SOUND_FILES = {
  diceRolling: "/sounds/DiceRolling.mp3",
  passingNext: "/sounds/passingNext.mp3",
  inside: "/sounds/inside.mp3",
  capture: "/sounds/fahhh_KcgAXfs.mp3",
  star: "/sounds/star%20sound.mp3",
  matchStart: "/sounds/match%20start.mp3",
  countdownDing: "/sounds/tunetank.com_scroll-ding-counting-slow.mp3",
} as const;

export type TGameSound = keyof typeof SOUND_FILES;

const BG_MUSIC_SRC = "/sounds/ludo_king.mp3";
const BG_MUSIC_VOLUME = 0.28;

/** Default volumes tuned for Classic-like clarity. */
const DEFAULT_VOLUME: Record<TGameSound, number> = {
  diceRolling: 0.72,
  passingNext: 0.38,
  inside: 0.62,
  capture: 0.85,
  star: 0.72,
  matchStart: 0.78,
  countdownDing: 0.62,
};

const cache = new Map<TGameSound, HTMLAudioElement>();
let bgMusic: HTMLAudioElement | null = null;
let matchSearchLoop: HTMLAudioElement | null = null;
let bgMusicStarted = false;

const getAudio = (name: TGameSound) => {
  let audio = cache.get(name);
  if (!audio) {
    audio = new Audio(SOUND_FILES[name]);
    audio.preload = "auto";
    cache.set(name, audio);
  }
  return audio;
};

const getBgMusic = () => {
  if (!bgMusic) {
    bgMusic = new Audio(BG_MUSIC_SRC);
    bgMusic.loop = true;
    bgMusic.preload = "auto";
    bgMusic.volume = BG_MUSIC_VOLUME;
  }
  return bgMusic;
};

/** Play a short SFX; clones overlapping plays for step / capture sounds. */
export const playSound = (name: TGameSound, volume?: number) => {
  try {
    const base = getAudio(name);
    const audio =
      name === "passingNext" ||
      name === "capture" ||
      name === "star" ||
      name === "matchStart" ||
      name === "countdownDing"
        ? (base.cloneNode(true) as HTMLAudioElement)
        : base;
    audio.volume = Math.min(
      1,
      volume ?? DEFAULT_VOLUME[name] ?? 0.55
    );
    audio.currentTime = 0;
    void audio.play().catch(() => {
      // Autoplay may be blocked until user gesture; ignore
    });
  } catch {
    // Missing file / unsupported — ignore
  }
};

/** Loop counting ding during online match search (10s window). */
export const startMatchSearchLoop = (volume = 0.55) => {
  try {
    if (!matchSearchLoop) {
      matchSearchLoop = new Audio(SOUND_FILES.countdownDing);
      matchSearchLoop.loop = true;
      matchSearchLoop.preload = "auto";
    }
    matchSearchLoop.volume = Math.min(1, volume);
    if (!matchSearchLoop.paused) return;
    matchSearchLoop.currentTime = 0;
    void matchSearchLoop.play().catch(() => {
      // Autoplay may be blocked until user gesture; ignore
    });
  } catch {
    // ignore
  }
};

export const stopMatchSearchLoop = () => {
  try {
    if (!matchSearchLoop) return;
    matchSearchLoop.pause();
    matchSearchLoop.currentTime = 0;
  } catch {
    // ignore
  }
};

/** Looping BGM — opt-in only; not started when a match/board loads. */
let matchMusicStarted = false;

export const beginMatchMusic = (volume = BG_MUSIC_VOLUME) => {
  if (matchMusicStarted) return;
  matchMusicStarted = true;
  startBackgroundMusic(volume);
};

/** Looping BGM for an active match. */
export const startBackgroundMusic = (volume = BG_MUSIC_VOLUME) => {
  try {
    const audio = getBgMusic();
    audio.volume = volume;
    audio.loop = true;
    if (audio.paused) {
      void audio.play().catch(() => {
        // Needs a prior user gesture (e.g. Play / Start)
      });
    }
  } catch {
    // ignore
  }
};

/** Start BGM once — on first dice roll / gameplay, not on game load. */
export const ensureBackgroundMusic = (volume = BG_MUSIC_VOLUME) => {
  if (bgMusicStarted) return;
  bgMusicStarted = true;
  startBackgroundMusic(volume);
};

export const stopBackgroundMusic = () => {
  matchMusicStarted = false;
  try {
    if (!bgMusic) return;
    bgMusic.pause();
    bgMusic.currentTime = 0;
    bgMusicStarted = false;
  } catch {
    // ignore
  }
};

export const preloadGameSounds = () => {
  (Object.keys(SOUND_FILES) as TGameSound[]).forEach((name) => {
    getAudio(name);
  });
};
