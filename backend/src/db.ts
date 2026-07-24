import mongoose from "mongoose";
import { config } from "./config";

export async function connectDb(): Promise<void> {
  mongoose.set("strictQuery", true);
  console.log("Connecting to MongoDB...");
  await mongoose.connect(config.mongoUrl, {
    serverSelectionTimeoutMS: 15000,
  });
  console.log("MongoDB connected");
}
