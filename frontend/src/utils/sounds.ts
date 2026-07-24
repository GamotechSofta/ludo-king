const SOUND_FILES = {
  diceRolling: "/sounds/DiceRolling.mp3",
  passingNext: "/sounds/passingNext.mp3",
  inside: "/sounds/inside.mp3",
  capture: "/sounds/fahhh_KcgAXfs.mp3",
} as const;

export type TGameSound = keyof typeof SOUND_FILES;

const BG_MUSIC_SRC = "/sounds/ludo_king.mp3";
const BG_MUSIC_VOLUME = 0.28;

const cache = new Map<TGameSound, HTMLAudioElement>();
let bgMusic: HTMLAudioElement | null = null;

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
export const playSound = (name: TGameSound, volume = 0.55) => {
  try {
    const base = getAudio(name);
    const audio =
      name === "passingNext" || name === "capture"
        ? (base.cloneNode(true) as HTMLAudioElement)
        : base;
    audio.volume = name === "capture" ? Math.min(1, volume + 0.15) : volume;
    audio.currentTime = 0;
    void audio.play().catch(() => {
      // Autoplay may be blocked until user gesture; ignore
    });
  } catch {
    // Missing file / unsupported — ignore
  }
};

/** Looping BGM for an active match (call on game start). */
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

export const stopBackgroundMusic = () => {
  try {
    if (!bgMusic) return;
    bgMusic.pause();
    bgMusic.currentTime = 0;
  } catch {
    // ignore
  }
};

export const preloadGameSounds = () => {
  (Object.keys(SOUND_FILES) as TGameSound[]).forEach((name) => {
    getAudio(name);
  });
  getBgMusic();
};
