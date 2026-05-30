# Contributing to Omni Browser

Thank you for your interest in contributing to Omni Browser! We are dedicated to building a high-performance, private, and premium mobile browser. Following these guidelines ensures that your contributions can be integrated quickly and smoothly.

---

## 📋 Code of Conduct

By participating in this project, you agree to abide by our standards of respectful, collaborative, and friendly community behavior.

---

## 🛠️ Development Guidelines

### Coding Style
* Follow the standard Kotlin style guides: [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
* Maintain all existing Compose structure rules, utilizing descriptive and reusable components.
* Ensure proper high-contrast colors and proper support for dark and light themes.

### GeckoView interop
* Scopes for navigation, location, and progress delegates should always be checked to verify they are running under the active session context.
* Never call `GeckoSession.open()` unconditionally on active tabs. Always check `!session.isOpen` beforehand.

### ELF and NDK packaging
* Ensure that any native dependencies or shared libraries conform to zip-alignment boundaries (16 KB pages) to maintain compatibility with modern Android 15/16 devices.

---

## 🔀 Pull Request Process

1. **Fork the Repository**: Create your own fork and branch from `main`.
2. **Implement Changes**: Add clean, descriptive commits outlining what changed and why.
3. **Compile and Verify**: Run compilation tests before submission:
   ```bash
   ./gradlew compileDebugKotlin
   ```
4. **Submit PR**: Open a Pull Request targeting our main branch. Provide a comprehensive summary of features added or bugs fixed.
