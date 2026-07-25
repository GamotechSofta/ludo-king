/**
 * Indian-style online Ludo player names for computer opponents.
 * Male and female pools are kept separate so every match gets a gender mix.
 */

export const MALE_BOT_NAMES = [
  "Aarav",
  "Vihaan",
  "Aditya",
  "Arjun",
  "Kabir",
  "Krishna",
  "Atharva",
  "Om",
  "Vedant",
  "Rohan",
  "Rahul",
  "Siddharth",
  "Yash",
  "Parth",
  "Aniket",
  "Harsh",
  "Rudra",
  "Kunal",
  "Pranav",
  "Tanmay",
  "Shreyas",
  "Swapnil",
  "Saurabh",
  "Ritik",
  "Abhishek",
  "Aman",
  "Nikhil",
  "Akash",
  "Mohit",
  "Ayush",
  "Ritesh",
  "Nitin",
  "Piyush",
  "Varun",
  "Deepak",
  "Kartik",
  "Shubham",
  "Manav",
  "Darshan",
  "Tushar",
  "Ayaan",
  "Reyansh",
  "Vivaan",
  "Ishaan",
  "Shaurya",
  "Dhruv",
  "Aryan",
  "Karan",
  "Dev",
  "Laksh",
  "Samar",
  "Arnav",
  "Yuvraj",
  "Rian",
  "Viraj",
  "Advait",
  "Neil",
  "Ishan",
  "Hrithik",
  "Soham",
  "Tejas",
  "Anirudh",
  "Chirag",
  "Gaurav",
  "Himanshu",
  "Jatin",
  "Kushal",
  "Lokesh",
  "Mayank",
  "Neel",
  "Ojas",
  "Pratik",
  "Raj",
  "Sagar",
  "Tarun",
  "Utkarsh",
  "Vishal",
  "Wasim",
  "Yatin",
  "Zayn",
  "Bhavesh",
  "Chetan",
  "Dinesh",
  "Eshan",
  "Farhan",
  "Gagan",
  "Harshit",
  "Inder",
  "Jay",
  "Keshav",
  "Lalit",
  "Mihir",
  "Naman",
  "Omkar",
  "Pranay",
  "Rishi",
  "Sahil",
  "Taran",
  "Uday",
  "Vivek",
  // Gaming-style handles
  "AaravOP",
  "KabirX",
  "RahulYT",
  "RohanPro",
  "YashGaming",
  "AtharvaX",
  "KunalOP",
  "OmPlays",
  "ArjunPro",
  "VihaanX",
  "AdityaOP",
  "HarshYT",
  "VedantPro",
  "ParthOP",
  "RudraGaming",
  "AkashPro",
  "NikhilX",
  "ShubhamOP",
  "AyushPro",
  "KartikYT",
  "ManavPlays",
  "TusharX",
  "VarunLive",
  "DeepakOP",
  "PranavX",
  "SaurabhPro",
  "TanmayGaming",
  "AbhishekOP",
  "MohitLive",
  "RitikYT",
  "PiyushX",
  "AmanPro",
  "NitinGaming",
];

export const FEMALE_BOT_NAMES = [
  "Ananya",
  "Aadhya",
  "Diya",
  "Kiara",
  "Kavya",
  "Siya",
  "Riya",
  "Sneha",
  "Pooja",
  "Neha",
  "Meera",
  "Anvi",
  "Ishita",
  "Nandini",
  "Khushi",
  "Saanvi",
  "Prachi",
  "Vaishnavi",
  "Shruti",
  "Aarti",
  "Komal",
  "Rutuja",
  "Sakshi",
  "Payal",
  "Simran",
  "Tanisha",
  "Mitali",
  "Isha",
  "Shreya",
  "Palak",
  "Bhavya",
  "Jiya",
  "Avni",
  "Riddhi",
  "Ankita",
  "Priya",
  "Muskan",
  "Nikita",
  "Pallavi",
  "Divya",
  "Aisha",
  "Myra",
  "Aarohi",
  "Pari",
  "Navya",
  "Ira",
  "Sara",
  "Tara",
  "Zara",
  "Mira",
  "Aditi",
  "Radhika",
  "Sanya",
  "Tanvi",
  "Vanya",
  "Esha",
  "Gauri",
  "Heena",
  "Jhanvi",
  "Kritika",
  "Lavanya",
  "Mahika",
  "Nisha",
  "Oorja",
  "Prisha",
  "Rhea",
  "Suhani",
  "Trisha",
  "Urvi",
  "Veda",
  "Yashika",
  "Zoya",
  "Amrita",
  "Bhavika",
  "Chitra",
  "Deepa",
  "Ekta",
  "Falguni",
  "Gargi",
  "Hiral",
  "Indira",
  "Jaya",
  "Kirti",
  "Lata",
  "Madhavi",
  "Naina",
  "Ojasvi",
  "Prerna",
  "Rashmi",
  "Swati",
  "Tanya",
  "Uma",
  "Vidya",
  // Gaming-style handles
  "AnanyaGaming",
  "DiyaLive",
  "ShreyaYT",
  "PriyaGaming",
  "KavyaLive",
  "RiyaGaming",
  "SnehaX",
  "KhushiPlays",
  "MeeraLive",
  "SakshiYT",
  "AnviGaming",
  "PallaviLive",
  "SimranX",
  "NandiniOP",
  "IshaGaming",
  "AvniPro",
  "MuskanYT",
  "JiyaGaming",
  "RiddhiLive",
  "DivyaYT",
  "BhavyaX",
  "PalakPlays",
  "AnkitaPro",
  "SiyaGaming",
  "PayalOP",
  "TanishaLive",
  "KomalYT",
];

export const BOT_NAMES = [...MALE_BOT_NAMES, ...FEMALE_BOT_NAMES];

function shuffle(list) {
  const arr = [...list];
  for (let i = arr.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

/** Unused names per gender for the current cycle; reshuffled when empty. */
const pools = {
  male: [],
  female: [],
};

function takeFromPool(gender, blocked) {
  const source = gender === "male" ? MALE_BOT_NAMES : FEMALE_BOT_NAMES;

  for (let attempt = 0; attempt < source.length * 2; attempt += 1) {
    if (pools[gender].length === 0) {
      pools[gender] = shuffle(source);
    }
    const name = pools[gender].pop();
    if (!blocked.has(name)) {
      return name;
    }
  }
  return null;
}

/**
 * Builds a randomized male/female sequence for the requested bot count.
 * With 2+ bots at least one of each gender is guaranteed.
 */
function genderSequence(botCount) {
  if (botCount <= 0) return [];
  if (botCount === 1) {
    return [Math.random() < 0.5 ? "male" : "female"];
  }
  const seq = ["male", "female"];
  for (let i = 2; i < botCount; i += 1) {
    seq.push(Math.random() < 0.5 ? "male" : "female");
  }
  return shuffle(seq);
}

/**
 * Returns unique bot display names for a match.
 * playerCount: 2 → 1 bot, 3 → 2 bots, 4 → 3 bots.
 */
export function getRandomBotNames(playerCount) {
  const botCount = Math.max(0, Number(playerCount) - 1);
  if (botCount === 0) return [];

  const blocked = new Set();
  const picked = [];

  for (const gender of genderSequence(botCount)) {
    const name =
      takeFromPool(gender, blocked) ??
      takeFromPool(gender === "male" ? "female" : "male", blocked);
    if (name) {
      blocked.add(name);
      picked.push(name);
    }
  }
  return picked;
}

/**
 * Picks one unused bot name of a random gender, avoiding names already in use.
 */
export function getOneBotName(exclude = []) {
  const blocked = new Set(exclude);
  const gender = Math.random() < 0.5 ? "male" : "female";
  const name =
    takeFromPool(gender, blocked) ??
    takeFromPool(gender === "male" ? "female" : "male", blocked);

  return name ?? `Player${Math.floor(Math.random() * 900) + 100}`;
}
