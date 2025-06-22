# CONKOT - Speed Drawer (Kotlin Native)

A high-performance native Android app drawer built with Kotlin, focused on speed, productivity, and minimal size (<10MB).

## ✨ Features

- **⚡ Lightning Fast Search**: Instant fuzzy search with real-time filtering
- **❤️ Smart Favorites**: Pin your most-used apps for quick access
- **📊 Usage Analytics**: Tracks and prioritizes frequently used apps
- **🎨 Customizable UI**: Material Design with dark/light themes
- **🚀 Optimized Performance**: Native Kotlin with MVVM architecture
- **📱 Minimal Size**: Optimized APK under 10MB
- **🔧 Advanced Settings**: Icon sizes, animations, search behavior
- **🏠 Launcher Support**: Can replace default Android launcher

## 🏗️ Architecture

- **MVVM Pattern**: ViewModels + LiveData + Repository pattern
- **Kotlin Coroutines**: Asynchronous operations and threading
- **Room Database**: Local storage for app data and preferences
- **Material Design**: Modern UI components and theming
- **Repository Pattern**: Clean data layer separation

## 📋 Requirements

- **Android**: 6.0+ (API level 23)
- **Target SDK**: 34
- **Min Size**: <10MB APK
- **Permissions**: Query all packages, Vibrate access

## 🚀 Installation

### Download APK
Download the latest release from [GitHub Releases](../../releases)

### Build from Source
```bash
git clone https://github.com/yamodiji/CONKOT.git
cd CONKOT
./gradlew assembleRelease
```

## 🛠️ Project Structure

```
app/
├── src/main/
│   ├── kotlin/com/speedrawer/conkot/
│   │   ├── ui/
│   │   │   ├── activities/     # Main activities
│   │   │   ├── fragments/      # UI fragments
│   │   │   ├── adapters/       # RecyclerView adapters
│   │   │   └── viewmodels/     # MVVM ViewModels
│   │   ├── data/
│   │   │   ├── models/         # Data models
│   │   │   ├── repository/     # Repository pattern
│   │   │   ├── database/       # Room database
│   │   │   └── preferences/    # SharedPreferences
│   │   ├── utils/              # Utility classes
│   │   ├── services/           # Background services
│   │   └── MainActivity.kt     # Entry point
│   ├── res/                    # Resources
│   └── AndroidManifest.xml
├── build.gradle                # App build configuration
└── proguard-rules.pro         # Code obfuscation rules
```

## 🔧 Performance Optimizations

- **ProGuard/R8**: Code shrinking and obfuscation
- **Resource Optimization**: Minimal resource usage
- **Lazy Loading**: Apps loaded on demand
- **Debounced Search**: Optimized search performance
- **Memory Management**: Efficient caching and cleanup

## 📱 Core Components

### MainActivity
- Main launcher activity with search functionality
- Handles app search, filtering, and launching
- Material Design UI with customizable themes

### AppRepository
- Manages installed apps discovery
- Handles favorites and usage tracking
- Provides search and filtering logic

### Database (Room)
- Stores app usage statistics
- Manages favorite apps list
- Caches app information for performance

### ViewModels
- `MainViewModel`: Main screen logic
- `SettingsViewModel`: Settings management
- `SearchViewModel`: Search functionality

## ⚙️ Build Configuration

### Gradle Optimization
- **MinifyEnabled**: true
- **ShrinkResources**: true
- **ProGuard**: Enabled for release
- **Target Size**: <10MB

### Dependencies
- Minimal external dependencies
- Core Android libraries only
- No heavy frameworks (unlike Flutter)

## 🎯 Performance Targets

- **App Size**: <10MB APK
- **Launch Time**: <500ms cold start
- **Search Response**: <50ms
- **Memory Usage**: <50MB RAM
- **Battery Impact**: Minimal background usage

## 🔒 Permissions

- `QUERY_ALL_PACKAGES`: Access installed apps list
- `VIBRATE`: Haptic feedback for interactions
- `RECEIVE_BOOT_COMPLETED`: Quick actions after restart

## 🧪 Testing

```bash
# Unit tests
./gradlew test

# Instrumented tests
./gradlew connectedAndroidTest

# UI tests
./gradlew connectedCheck
```

## 📦 Release Process

### GitHub Actions
- Automated builds on push
- APK generation and signing
- Release deployment
- No local builds required

### Manual Release
```bash
./gradlew assembleRelease
./gradlew bundleRelease  # For Play Store
```

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔗 Related Projects

- [Original Flutter Version](../): Full-featured Flutter implementation
- [Speed Drawer Design](../): UI/UX specifications

---

**Built with ❤️ in Kotlin for maximum performance and minimal size** 