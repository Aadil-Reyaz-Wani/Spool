# SPOOL - Measure. Manage. Print.

![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple) ![Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-green) ![Android](https://img.shields.io/badge/Platform-Android-blue)

**Spool** is a modern Android utility application designed for 3D printing enthusiasts. It helps makers track their filament inventory, calculate remaining weight, and monitor material usage to ensure they never run out of filament mid-print.

##  Features

* **Inventory Management:** Add and track multiple spools with details like Color, Material (PLA, PETG, ABS), and Brand.
* **Visual Tracking:** Real-time progress bars showing remaining filament percentage.
* **Smart Alerts:** "Traffic Light" system with **Low Stock** warnings when filament drops below critical levels.
* **Weight Calculation:** Input used weight to automatically update remaining capacity.
* **Dark Mode Support:** Fully optimized UI for both light and dark themes.

## Tech Stack

Built with modern Android development practices:

* **Language:** [Kotlin](https://kotlinlang.org/)
* **UI Framework:** [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3 Design)
* **Architecture:** MVVM (Model-View-ViewModel)
* **Local Database:** [Room Database](https://developer.android.com/training/data-storage/room) (SQLite abstraction)
* **Navigation:** Jetpack Navigation Compose (Navigation 3)
* **Dependency Injection:** Hilt

## Getting Started

To run this project locally:

1.  **Clone the repository**
    ```bash
    git clone [https://github.com/Aadil-Reyaz-Wani/Spool.git](https://github.com/Aadil-Reyaz-Wani/Spool.git)
    ```
2.  **Open in Android Studio**
    * File > Open > Select the `Spool` directory.
3.  **Sync Gradle**
    * Wait for the project to download dependencies.
4.  **Run**
    * Select your emulator or physical device and click the "Run" (▶️) button.

## Download

The app is currently in **Closed Testing** on the Google Play Store.
[View on Google Play](https://play.google.com/store/apps/details?id=com.aadil.spool)

## Contributing

Contributions are welcome! If you have suggestions for new features (like Barcode scanning or Cloud Backup), feel free to open an issue or submit a pull request.

1.  Fork the Project
2.  Create your Feature Branch (`git checkout -b feature/NewFeature`)
3.  Commit your Changes (`git commit -m 'Add some NewFeature'`)
4.  Push to the Branch (`git push origin feature/NewFeature`)
5.  Open a Pull Request

## 📄 License

This project is licensed under the MIT License.

---
*Built with ❤️ by **Aadil Reyaz Wani***
