package com.ludo.backend.room;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Indian male first names for bot players.
 * Format: {@code <MaleName><10-99>} e.g. Saurabh63, Rohit27.
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
      "Tarun", "Utkarsh", "Vishal", "Wasim", "Yatin", "Bhavesh", "Chetan", "Dinesh",
      "Eshan", "Farhan", "Gagan", "Harshit", "Inder", "Jay", "Keshav", "Lalit",
      "Mihir", "Naman", "Omkar", "Pranay", "Rishi", "Sahil", "Taran", "Uday", "Vivek",
      "Rohit", "Rushi", "Prathamesh", "Aakash", "Virat", "Suresh", "Nilesh",
      "Sanket", "Amol", "Ajay", "Vijay", "Sandeep", "Rajesh", "Mahesh", "Ganesh",
      "Prashant", "Saurav", "Hitesh", "Ketan", "Paresh", "Rupesh", "Yogesh", "Mandar",
      "Siddhesh", "Onkar", "Shreyash", "Adarsh", "Harshad", "Vinay", "Suraj", "Kiran");

  private BotNames() {
  }

  /**
   * Unique male bot display name for this match: {@code Name + 10..99}.
   */
  public static String randomName(Collection<String> taken) {
    Set<String> blocked = taken == null ? Set.of() : new HashSet<>(taken);
    ThreadLocalRandom rng = ThreadLocalRandom.current();

    for (int attempt = 0; attempt < 400; attempt++) {
      String base = MALE.get(rng.nextInt(MALE.size()));
      int suffix = rng.nextInt(10, 100);
      String candidate = base + suffix;
      if (!blocked.contains(candidate)) {
        return candidate;
      }
    }

    // Extremely unlikely fallback if every Name+suffix combo is taken.
    return "Player" + rng.nextInt(10, 100);
  }
}
