package com.ludo.backend.room;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Indian-style display names for bot players, so online matches look like they
 * are filled with real users instead of "Bot 1", "Bot 2".
 */
public final class BotNames {

  private static final List<String> MALE = List.of(
      "Aarav", "Vihaan", "Aditya", "Arjun", "Kabir", "Krishna", "Atharva", "Om",
      "Vedant", "Rohan", "Rahul", "Siddharth", "Yash", "Parth", "Aniket", "Harsh",
      "Rudra", "Kunal", "Pranav", "Tanmay", "Shreyas", "Swapnil", "Saurabh", "Ritik",
      "Abhishek", "Aman", "Nikhil", "Akash", "Mohit", "Ayush", "Ritesh", "Nitin",
      "Piyush", "Varun", "Deepak", "Kartik", "Shubham", "Manav", "Darshan", "Tushar",
      "Ayaan", "Reyansh", "Vivaan", "Ishaan", "Shaurya", "Dhruv", "Aryan", "Karan",
      "Dev", "Laksh", "Samar", "Arnav", "Yuvraj", "Rian", "Viraj", "Advait", "Neil",
      "Ishan", "Hrithik", "Soham", "Tejas", "Anirudh", "Chirag", "Gaurav", "Himanshu",
      "Jatin", "Kushal", "Lokesh", "Mayank", "Neel", "Ojas", "Pratik", "Raj", "Sagar",
      "Tarun", "Utkarsh", "Vishal", "Wasim", "Yatin", "Zayn", "Bhavesh", "Chetan",
      "Dinesh", "Eshan", "Farhan", "Gagan", "Harshit", "Inder", "Jay", "Keshav",
      "Lalit", "Mihir", "Naman", "Omkar", "Pranay", "Rishi", "Sahil", "Taran",
      "Uday", "Vivek",
      "AaravOP", "KabirX", "RahulYT", "RohanPro", "YashGaming", "AtharvaX", "KunalOP",
      "OmPlays", "ArjunPro", "VihaanX", "AdityaOP", "HarshYT", "VedantPro", "ParthOP",
      "RudraGaming", "AkashPro", "NikhilX", "ShubhamOP", "AyushPro", "KartikYT",
      "ManavPlays", "TusharX", "VarunLive", "DeepakOP", "PranavX", "SaurabhPro",
      "TanmayGaming", "AbhishekOP", "MohitLive", "RitikYT", "PiyushX", "AmanPro",
      "NitinGaming");

  private static final List<String> FEMALE = List.of(
      "Ananya", "Aadhya", "Diya", "Kiara", "Kavya", "Siya", "Riya", "Sneha", "Pooja",
      "Neha", "Meera", "Anvi", "Ishita", "Nandini", "Khushi", "Saanvi", "Prachi",
      "Vaishnavi", "Shruti", "Aarti", "Komal", "Rutuja", "Sakshi", "Payal", "Simran",
      "Tanisha", "Mitali", "Isha", "Shreya", "Palak", "Bhavya", "Jiya", "Avni",
      "Riddhi", "Ankita", "Priya", "Muskan", "Nikita", "Pallavi", "Divya", "Aisha",
      "Myra", "Aarohi", "Pari", "Navya", "Ira", "Sara", "Tara", "Zara", "Mira",
      "Aditi", "Radhika", "Sanya", "Tanvi", "Vanya", "Esha", "Gauri", "Heena",
      "Jhanvi", "Kritika", "Lavanya", "Mahika", "Nisha", "Oorja", "Prisha", "Rhea",
      "Suhani", "Trisha", "Urvi", "Veda", "Yashika", "Zoya", "Amrita", "Bhavika",
      "Chitra", "Deepa", "Ekta", "Falguni", "Gargi", "Hiral", "Indira", "Jaya",
      "Kirti", "Lata", "Madhavi", "Naina", "Ojasvi", "Prerna", "Rashmi", "Swati",
      "Tanya", "Uma", "Vidya",
      "AnanyaGaming", "DiyaLive", "ShreyaYT", "PriyaGaming", "KavyaLive", "RiyaGaming",
      "SnehaX", "KhushiPlays", "MeeraLive", "SakshiYT", "AnviGaming", "PallaviLive",
      "SimranX", "NandiniOP", "IshaGaming", "AvniPro", "MuskanYT", "JiyaGaming",
      "RiddhiLive", "DivyaYT", "BhavyaX", "PalakPlays", "AnkitaPro", "SiyaGaming",
      "PayalOP", "TanishaLive", "KomalYT");

  private BotNames() {
  }

  /**
   * Returns a random unused name, alternating gender pools at random so a match
   * ends up with a mix of male and female players.
   */
  public static String randomName(Collection<String> taken) {
    Set<String> blocked = taken == null ? Set.of() : new HashSet<>(taken);
    boolean maleFirst = ThreadLocalRandom.current().nextBoolean();

    String name = pick(maleFirst ? MALE : FEMALE, blocked);
    if (name == null) {
      name = pick(maleFirst ? FEMALE : MALE, blocked);
    }
    return name != null ? name : "Player" + ThreadLocalRandom.current().nextInt(100, 1000);
  }

  private static String pick(List<String> source, Set<String> blocked) {
    List<String> available = new ArrayList<>(source);
    available.removeAll(blocked);
    if (available.isEmpty()) {
      return null;
    }
    Collections.shuffle(available, ThreadLocalRandom.current());
    return available.get(0);
  }
}
