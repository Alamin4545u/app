# হিসাব খাতা — Android Native Source

## Firebase কনফিগ কোথায় বসাবেন
1. Firebase Console-এ আগের **একই Firebase Project** খুলুন।
2. Project settings → Your apps → Android app যোগ করুন (না থাকলে)।
3. Android package name দিন: `com.example.hisabkhata`
4. `google-services.json` ডাউনলোড করুন।
5. JSON ফাইলটি এই নির্দিষ্ট স্থানে রাখুন:

   `HisabKhataNative/app/google-services.json`

   `app/src/main/`-এ নয়, প্রজেক্টের `app/` ফোল্ডারের সরাসরি ভেতরে।

6. Firebase Console → Firestore Database → Rules-এ আপনার অ্যাপের জন্য নিরাপদ Authentication/authorization Rules সেট করুন। Production-এ কখনো `allow read, write: if true;` ব্যবহার করবেন না।
7. Android Studio-তে প্রজেক্ট খুলে Gradle Sync করুন, তারপর Run চাপুন।

## পুরোনো ডাটা সংরক্ষণ
এই অ্যাপ আগের HTML অ্যাপের একই Firestore collections এবং field names ব্যবহার করে:
- `accounts`: `name`, `createdAt`
- `transactions`: `id`, `account`, `desc`, `amount`, `type`, `date`, `timestamp`
- `trash`: `kind`, `label`, `deletedAt`, `payload`

অ্যাপটি কোনো collection wipe/reset করে না। প্রথম লোডে বিদ্যমান ডাটা পড়ে। `accounts`/`transactions`-এর নাম ও document ID-র সম্পর্ক আগের অ্যাপের মতোই রাখা হয়েছে।

**গুরুত্বপূর্ণ:** `google-services.json` একই Firebase Project-এর হতে হবে। অন্য Project-এর ফাইল দিলে পুরোনো ডাটা দেখা যাবে না। কোনো ডাটা পরিবর্তনের আগে Firebase Console থেকে Firestore export বা অন্তত JSON backup নিন। এই সোর্সে Authentication চালু করা নেই; আপনার Firestore Rules যদি sign-in বাধ্যতামূলক করে, আগে Firebase Authentication যুক্ত করতে হবে।

## Build environment
- Android Studio (সাম্প্রতিক stable)
- JDK 17
- Gradle wrapper তৈরি করতে Android Studio-র Gradle sync ব্যবহার করুন (Gradle 8.11.1 / AGP 8.9.2)
- compileSdk 35, minSdk 23

## নোট
এটি একটি Native Kotlin starter project। Android Studio-তে প্রথমবার খুলে Gradle sync ও Firebase setup সম্পন্ন করুন। এখানে `google-services.json` ইচ্ছাকৃতভাবে অন্তর্ভুক্ত নেই, কারণ সেটি আপনার ব্যক্তিগত Firebase project-এর ফাইল।
