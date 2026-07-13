# 🤝 Contributing to Omni Browser

Thank you for your interest in contributing to **Omni Browser**! We are a small, independent open-source team, and every contribution — big or small — makes a real difference.

Please take a few minutes to read this guide before submitting a Pull Request.

---

## 📋 Code of Conduct

By participating in this project you agree to maintain a welcoming, respectful, and inclusive environment for all contributors. Harassment, discrimination, and personal attacks of any kind will not be tolerated.

---

## 🛠️ Development Setup

### Prerequisites
- Android Studio **Ladybug** or newer
- JDK **17** (`JAVA_HOME` set correctly)
- Android SDK — Target **API 35** (Android 15)
- Android device or emulator running **API 26+**

### Getting Started

```bash
git clone https://github.com/REBEL-ROOT/omni-browser.git
cd omni-browser

# Verify it compiles cleanly (targets both CPU flavors)
./gradlew compileArmDebugKotlin compileAarch64DebugKotlin

# Build and install to a connected device (e.g. aarch64)
./gradlew installAarch64Debug
```

---

## 📐 Code Style Guidelines

### Kotlin
- Follow the official [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Use descriptive, self-documenting variable and function names.
- Avoid deeply nested logic — prefer early returns.
- Keep composable functions focused and small (single responsibility).

### Jetpack Compose
- Break large composables into smaller, reusable sub-components.
- Use `remember {}` and `derivedStateOf {}` appropriately to prevent unnecessary recompositions.
- Always support both **dark and light themes** — test both modes.
- Do not hardcode colors — use `MaterialTheme.colorScheme` tokens.

### GeckoView Interop
- Always check `!session.isOpen` before calling `GeckoSession.open()`.
- Use `GeckoResult.accept()` or `.then()` for all async callbacks — never fire-and-forget.
- Navigation, content, and progress delegates must be scoped to the **active tab session only**.

### NDK / Native Code
- Any native `.so` libraries must conform to **16 KB page alignment** for Android 15+ compatibility.
- Do not add native dependencies without discussing in an issue first.

---

## 🌿 Branch Naming

| Type | Format | Example |
|---|---|---|
| New Feature | `feature/short-name` | `feature/reading-mode` |
| Bug Fix | `fix/short-name` | `fix/tab-crash-on-back` |
| Documentation | `docs/short-name` | `docs/architecture-update` |
| Refactor | `refactor/short-name` | `refactor/extension-manager` |

---

## 🔀 Pull Request Process

1. **Fork** the repository and create your branch from `main`.
2. **Write clean commits** — use present tense and prefix your message:
   - `Add:` for new features
   - `Fix:` for bug fixes
   - `Refactor:` for code improvements
   - `Docs:` for documentation
   - `Style:` for formatting/UI tweaks
3. **Test your changes** — run a full debug build before submitting:
    ```bash
    ./gradlew compileArmDebugKotlin compileAarch64DebugKotlin
    ./gradlew assembleDebug
    ```
4. **Open a Pull Request** with:
   - A clear title describing what changed
   - A description of *why* it was needed
   - Screenshots or screen recordings for any UI changes
   - Reference to any related GitHub Issues (`Closes #123`)

---

## 🐛 Reporting Bugs

Please use the [GitHub Issues](https://github.com/REBEL-ROOT/omni-browser/issues) page to report bugs.

Include:
- Android version and device model
- App version (found in Settings → About)
- Steps to reproduce the bug
- Expected vs actual behavior
- Logcat output if available (use `adb logcat -s BrowserViewModel`)

---

## 💡 Suggesting Features

Open a [GitHub Issue](https://github.com/REBEL-ROOT/omni-browser/issues) with the label **`enhancement`** and describe:
- What the feature does
- Why it benefits Omni Browser users
- Any design or implementation ideas you have

---

## 🔒 Reporting Security Vulnerabilities

**Do not open a public issue for security vulnerabilities.**

Use GitHub's private [Security Advisory](https://github.com/REBEL-ROOT/omni-browser/security/advisories/new) system to report responsibly. See [docs/SECURITY.md](docs/SECURITY.md) for our full disclosure policy.

---

## 📄 License Agreement

By contributing to this project, you agree that your contributions will be licensed under the **GNU General Public License v3 (GPLv3)**, the same license as the project itself.

---

<div align="center">
  <sub>Thank you for helping make Omni Browser better for everyone! 🚀</sub>
</div>
