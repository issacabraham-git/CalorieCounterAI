# 🥗 AI Calorie Tracker

A modern, intelligent nutrition tracker built with **Kotlin** and **Jetpack Compose**. 

This app allows users to log meals using natural language (e.g., *"I ate 2 eggs and a slice of toast"*). It uses Generative AI to automatically parse food items, estimate calories, and calculate macros (Protein, Carbs, Fats) without manual data entry.

## ✨ Features

* **Natural Language Input:** Just type what you ate; the AI handles the math.
* **Macro Tracking:** Automatically calculates Protein, Carbs, and Fat grams.
* **Daily Totals:** Real-time dashboard showing total calories and macros for the day.
* **Data Persistence:** Your food log is saved locally and persists even after closing the app.
* **Excel Export:** Export your full daily log to a `.csv` file for external tracking.
* **Smart "Forgot Meal" Detection:** Automatically flags missing meals (e.g., "Lunch: Not Entered") in the export.
* **Dark Mode UI:** Sleek, high-contrast interface designed for readability.

## 📱 Screenshots

| Dashboard & Totals | Natural Language Input | CSV/Excel Export |
|:---:|:---:|:---:|
| *(Drag your screenshot here)* | *(Drag your screenshot here)* | *(Drag your screenshot here)* |

## 🛠️ Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material3)
* **Architecture:** MVVM pattern
* **Local Storage:** SharedPreferences + Gson (JSON serialization)
* **AI Integration:** Generative Language API
* **Build System:** Gradle (Kotlin DSL)

## 🚀 How to Run

Since this project relies on a secure API key, you will need to set up your own environment secrets.

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/issacabraham-git/CalorieCounterAI.git](https://github.com/issacabraham-git/CalorieCounterAI.git)
    ```
2.  **Open in Android Studio.**
3.  **Configure API Key:**
    * Create a file named `local.properties` in the root directory (if not present).
    * Add your AI API key:
        ```properties
        apiKey=YOUR_API_KEY_HERE
        ```
4.  **Build & Run** on an emulator or physical device.

## 🔮 Future Roadmap

* [ ] Add chart visualizations for weekly progress.
* [ ] Cloud sync implementation.
* [ ] Voice-to-text input support.

---
*Built with ❤️ on Fedora Linux.*
