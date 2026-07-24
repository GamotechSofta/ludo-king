const SOUND_FILES = {
  diceRolling: "/sounds/DiceRolling.mp3",
  passingNext: "/sounds/passingNext.mp3",
  inside: "/sounds/inside.mp3",
} as const;

export type TGameSound = keyof typeof SOUND_FILES;

const cache = new Map<TGameSound, HTMLAudioElement>();

const getAudio = (name: TGameSound) => {
  let audio = cache.get(name);
  if (!audio) {
    audio = new Audio(SOUND_FILES[name]);
    audio.preload = "auto";
    cache.set(name, audio);
  }
  return audio;
};

/** Play a short SFX; clones overlapping plays for step sounds. */
export const playSound = (name: TGameSound, volume = 0.55) => {
  try {
    const base = getAudio(name);
    // Allow overlapping step sounds without cutting previous
    const audio = name === "passingNext" ? (base.cloneNode(true) as HTMLAudioElement) : base;
    audio.volume = volume;
    audio.currentTime = 0;
    void audio.play().catch(() => {
      // Autoplay may be blocked until user gesture; ignore
    });
  } catch {
    // Missing file / unsupported — ignore
  }
};

export const preloadGameSounds = () => {
  (Object.keys(SOUND_FILES) as TGameSound[]).forEach((name) => {
    getAudio(name);
  });
};
