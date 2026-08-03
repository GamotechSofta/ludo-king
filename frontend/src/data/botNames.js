/**
 * Indian male first names for computer / bot opponents.
 * Format: <MaleName><10-99> e.g. Saurabh63, Rohit27.
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
  "Rohit",
  "Rushi",
  "Prathamesh",
  "Aakash",
  "Virat",
  "Suresh",
  "Nilesh",
  "Sanket",
  "Amol",
  "Ajay",
  "Vijay",
  "Sandeep",
  "Rajesh",
  "Mahesh",
  "Ganesh",
  "Prashant",
  "Saurav",
  "Hitesh",
  "Ketan",
  "Paresh",
  "Rupesh",
  "Yogesh",
  "Mandar",
  "Siddhesh",
  "Onkar",
  "Shreyash",
  "Adarsh",
  "Harshad",
  "Vinay",
  "Suraj",
  "Kiran",
];

/** @deprecated Female bots are no longer used; kept empty for any legacy imports. */
export const FEMALE_BOT_NAMES = [];

export const BOT_NAMES = [...MALE_BOT_NAMES];

function randomSuffix() {
  return Math.floor(Math.random() * 90) + 10; // 10–99
}

function buildName(base) {
  return `${base}${randomSuffix()}`;
}

/**
 * Picks a unique male bot name not present in {@code blocked}.
 */
function pickUniqueMaleName(blocked) {
  for (let attempt = 0; attempt < 400; attempt += 1) {
    const base =
      MALE_BOT_NAMES[Math.floor(Math.random() * MALE_BOT_NAMES.length)];
    const candidate = buildName(base);
    if (!blocked.has(candidate)) {
      return candidate;
    }
  }
  return `Player${randomSuffix()}`;
}

/**
 * Returns unique male bot display names for a match.
 * playerCount: 2 → 1 bot, 3 → 2 bots, 4 → 3 bots.
 */
export function getRandomBotNames(playerCount) {
  const botCount = Math.max(0, Number(playerCount) - 1);
  if (botCount === 0) return [];

  const blocked = new Set();
  const picked = [];

  for (let i = 0; i < botCount; i += 1) {
    const name = pickUniqueMaleName(blocked);
    blocked.add(name);
    picked.push(name);
  }
  return picked;
}

/**
 * Picks one unused male bot name, avoiding names already in use.
 */
export function getOneBotName(exclude = []) {
  return pickUniqueMaleName(new Set(exclude));
}
