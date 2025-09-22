# 📱 WanderPlan - Your Ultimate Travel Companion

**A comprehensive Android travel planning app built with Java, Firebase, and modern Android architecture**

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Java](https://img.shields.io/badge/Language-Java-orange.svg)](https://www.java.com)
[![Firebase](https://img.shields.io/badge/Backend-Firebase-yellow.svg)](https://firebase.google.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

## 🌟 App Description

**WanderPlan** is a feature-rich Android travel planning application designed to help travelers organize, track, and share their adventures. Whether you're planning a weekend getaway or a month-long expedition, WanderPlan provides all the tools you need to create memorable travel experiences.

### 🎯 Perfect For:
- **Solo Travelers** planning personal adventures
- **Families** organizing group trips with budget tracking
- **Travel Enthusiasts** who want to document their journeys
- **Budget-Conscious Travelers** needing expense management
- **Anyone** who loves organized, stress-free travel planning

---

## ✨ Key Features

### 🔐 **User Authentication & Profiles**
- **Firebase Authentication** with email verification
- **Guest Mode** for offline-only usage
- **Profile Management** with photo uploads
- **Password Reset** functionality

### 🗺️ **Trip Management**
- **Create & Edit Trips** with destinations and date ranges
- **Trip Validation** ensuring logical date sequences
- **Trip Deletion** with confirmation dialogs
- **Comprehensive Trip Details** storage

### 🎯 **Activity Planning**
- **Activity Timeline** with time-based organization
- **Photo Attachments** from device gallery
- **Location & Description** tracking
- **Activity Validation** within trip date ranges
- **Edit & Delete** activities with ease

### 💰 **Budget Tracking & Analytics**
- **6 Expense Categories**: Food, Transport, Hotel, Activities, Shopping, Other
- **Visual Budget Analytics** with pie charts
- **Real-time Budget Calculations** (remaining vs. spent)
- **Expense Management** (add, edit, delete)
- **Malaysian Ringgit (RM)** currency formatting

### 🎨 **Customizable Themes**
- **8 Color Themes**: Pastel Pink, Forest Green, Sunset Orange, Deep Purple, Modern Teal, Red, Blue, Light Blue
- **3 Display Modes**: Light, Dark, System Default
- **Persistent Theme Settings** across app sessions
- **Dynamic UI Adaptation** for all themes

### 📱 **Smart Data Management**
- **Offline-First Architecture** with Room SQLite database
- **Firebase Cloud Sync** for logged-in users
- **Intelligent Conflict Resolution** when syncing data
- **Local Storage** for guest users
- **Background Synchronization**

### 📤 **Trip Sharing**
- **Professional Formatting** with emojis and structure
- **Complete Trip Details** including activities and budget
- **Universal Sharing** via any installed app (WhatsApp, Email, SMS, etc.)
- **Smart Content Adaptation** based on sharing platform

### 🖼️ **Photo Management**
- **Gallery Integration** for photo selection
- **Automatic Image Compression** for optimal storage
- **Firebase Storage** for cloud users
- **Local Storage** for offline users
- **EXIF Data Handling** for correct photo orientation

---

## 🛠️ Technical Stack

### **Core Technologies**
- **Language**: Java 11
- **Platform**: Android SDK (Min API 24, Target API 34)
- **IDE**: Android Studio
- **Build System**: Gradle with Kotlin DSL

### **Architecture & Patterns**
- **MVVM Architecture** (Model-View-ViewModel)
- **Repository Pattern** for data abstraction
- **Observer Pattern** with LiveData
- **Singleton Pattern** for managers
- **Offline-First Strategy**

### **Key Libraries**
```gradle
// Core Android
implementation 'androidx.appcompat:appcompat:1.7.1'
implementation 'com.google.android.material:material:1.12.0'
implementation 'androidx.navigation:navigation-fragment:2.9.0'

// Database & Architecture
implementation 'androidx.room:room-runtime:2.7.2'
implementation 'androidx.lifecycle:lifecycle-viewmodel:2.9.1'
implementation 'androidx.lifecycle:lifecycle-livedata:2.9.1'

// Firebase
implementation platform('com.google.firebase:firebase-bom:33.15.0')
implementation 'com.google.firebase:firebase-auth'
implementation 'com.google.firebase:firebase-firestore'
implementation 'com.google.firebase:firebase-storage'

// UI & Media
implementation 'com.github.bumptech.glide:glide:4.16.0'
implementation 'com.airbnb.android:lottie:6.6.7'
implementation 'com.github.PhilJay:MPAndroidChart:v3.1.0'
```

---

## 📸 Screenshots

| Home Screen | Trip Planning | Budget Analytics | Theme Options |
|-------------|---------------|------------------|---------------|
| *Coming Soon* | *Coming Soon* | *Coming Soon* | *Coming Soon* |

---

## 🚀 Getting Started

### **Prerequisites**
- Android Studio (Latest Version)
- Android SDK (API 24+)
- Java 11 or higher
- Firebase project setup

### **Installation**

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/WanderPlan.git
   cd WanderPlan
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Firebase Setup**
   - Create a new Firebase project
   - Add your Android app to the Firebase project
   - Download `google-services.json` and place it in the `app/` directory
   - Enable Authentication, Firestore, and Storage in Firebase Console

4. **Build and Run**
   - Sync project with Gradle files
   - Build the project (`Build > Make Project`)
   - Run on device or emulator

### **Configuration**
- Ensure your device/emulator runs Android 7.0 (API 24) or higher
- Grant necessary permissions (Storage, Internet)
- For testing, you can use Guest Mode to explore features offline

---

## 🧪 Testing

The app has been thoroughly tested with **60 comprehensive test cases** covering:

- ✅ **Authentication Flow** (Registration, Login, Password Reset)
- ✅ **Trip Management** (CRUD operations, Validation)
- ✅ **Activity Management** (Timeline, Photos, Validation)
- ✅ **Budget System** (All 6 categories, Analytics, Calculations)
- ✅ **Theme System** (All 8 themes, 3 modes, Persistence)
- ✅ **Data Sync** (Offline/Online, Conflict Resolution)
- ✅ **Photo Management** (Upload, Compression, Storage)
- ✅ **Sharing Features** (Formatting, Universal Compatibility)
- ✅ **Performance** (Large datasets, Memory management)
- ✅ **Error Handling** (Network issues, Invalid input)

**Test Results**: 100% Pass Rate - All 60 tests passing

---

## 🏗️ Architecture Overview

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   View Layer    │    │  ViewModel      │    │  Repository     │
│                 │    │                 │    │                 │
│ • Activities    │◄──►│ • Business      │◄──►│ • Data          │
│ • Fragments     │    │   Logic         │    │   Abstraction   │
│ • Layouts       │    │ • LiveData      │    │ • Sync Logic    │
└─────────────────┘    └─────────────────┘    └─────────────────┘
                                                        │
                       ┌─────────────────┐    ┌─────────────────┐
                       │   Local DB      │    │   Firebase      │
                       │                 │    │                 │
                       │ • Room SQLite   │    │ • Firestore     │
                       │ • Offline Data  │    │ • Auth          │
                       │ • Primary Store │    │ • Storage       │
                       └─────────────────┘    └─────────────────┘
```

---

## 🌟 Features in Detail

### **Offline-First Design**
WanderPlan works seamlessly offline, storing all data locally and syncing to the cloud when connected. This ensures your travel plans are always accessible, regardless of internet connectivity.

### **Smart Budget Management**
Track expenses across 6 categories with visual analytics. The app automatically calculates remaining budget and provides insights into spending patterns.

### **Flexible Authentication**
Choose between creating an account for cloud sync or using Guest Mode for complete offline functionality. No forced registration required.

### **Professional Trip Sharing**
Share your travel itineraries with beautifully formatted content that includes day-by-day activities, budget breakdowns, and trip highlights.

### **Customizable Experience**
Personalize the app with 8 different color themes and automatic dark/light mode support that follows your device settings.

---

## 📝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details.

### **Development Setup**
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Authors

- **Your Name** - *Initial work* - [YourGitHub](https://github.com/yourusername)

---

## 🙏 Acknowledgments

- Firebase team for excellent backend services
- Android Jetpack team for robust architecture components
- MPAndroidChart for beautiful chart visualizations
- Glide team for efficient image loading
- Lottie team for smooth animations

---

## 📞 Support

If you encounter any issues or have questions:

1. Check the [Issues](https://github.com/yourusername/WanderPlan/issues) page
2. Create a new issue with detailed description
3. Contact: your.email@example.com

---

## 🔮 Future Enhancements

- 🗺️ **Map Integration** for location visualization
- 🌐 **Multi-language Support** for international travelers
- 📊 **Advanced Analytics** with spending trends
- 👥 **Collaborative Planning** for group trips
- 🔔 **Smart Notifications** for trip reminders
- 📱 **Widget Support** for quick trip access

---

<div align="center">

**Made with ❤️ for travelers worldwide**

[⭐ Star this repo](https://github.com/yourusername/WanderPlan) • [🐛 Report Bug](https://github.com/yourusername/WanderPlan/issues) • [✨ Request Feature](https://github.com/yourusername/WanderPlan/issues)

</div>
