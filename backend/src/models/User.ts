import mongoose, { Document, Schema } from "mongoose";

export interface IUser extends Document {
  provider: string;
  providerId: string;
  name: string;
  email?: string;
  avatar?: string;
  createdAt: Date;
}

const UserSchema = new Schema<IUser>(
  {
    provider: { type: String, required: true },
    providerId: { type: String, required: true },
    name: { type: String, required: true },
    email: { type: String },
    avatar: { type: String },
  },
  { timestamps: { createdAt: true, updatedAt: false } }
);

UserSchema.index({ provider: 1, providerId: 1 }, { unique: true });

export const User = mongoose.model<IUser>("User", UserSchema);
