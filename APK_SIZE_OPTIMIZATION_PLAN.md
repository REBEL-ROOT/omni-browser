# APK Size Optimization Plan (115MB+ → Target: 60-80MB)

## Current Size Analysis

### Major Contributors (Estimated):
| Component | Estimated Size | Impact | Details |
|-----------|-------------|--------|---------|
| GeckoView Engine | 45-55MB | **CRITICAL** | Heavy JNI bindings and native libraries. |
| WireGuard VPN SDK | 8-12MB | High | Bundles Go-based native WireGuard tunnels (`libwg.so`). |
| ML Kit Translation | 5-8MB | Medium | Standing native libraries for translation engine. |
| SQLCipher + Room | 3-5MB | Medium | Custom native binaries for database encryption (`libsqlcipher.so`). |
| Media3/ExoPlayer | 2-3MB | Low | Audio/video playback controls. |
| Other dependencies | 5-8MB | Low | Jetpack Compose, Haze, Coil, etc. |

**Total: ~115MB** (Base universal release APK before optimization)

---

## Optimization Strategies

### 1. GeckoView Engine Optimization (Potential: 20-30MB savings)

Currently, the project uses the universal `org.mozilla.geckoview:geckoview` coordinate:
```kotlin
implementation("org.mozilla.geckoview:geckoview:145.0.20251124145406")
```

#### Optimization A: Switch to ABI-Specific Dependency Coordinate
Mozilla publishes architecture-specific AAR files. Since the project uses `abiFilters.add("arm64-v8a")` in release, we can switch the dependency to the arm64-specific artifact. This reduces build-time cache download size by 80MB+ and ensures zero transitive native architectures are packaged:
```kotlin
implementation("org.mozilla.geckoview:geckoview-arm64-v8a:145.0.20251124145406")
```

#### Optimization B: Run-time GeckoView Engine Tuning
Disable unused features in `GeckoRuntime` to save operational memory and ensure unused Gecko codecs or capabilities aren't loaded:
- Disable WebRTC components if conferencing features are not needed.
- Disable WebGL if standard 2D rendering is sufficient.

---

### 2. Refined ProGuard/R8 Rules (Potential: 5-8MB savings)

The current `proguard-rules.pro` contains several overly broad `-keep` rules that force R8 to keep entire packages of libraries, disabling tree-shaking:
- `-keep class com.google.mlkit.** { *; }`
- `-keep class coil.** { *; }`
- `-keep class com.wireguard.android.** { *; }`

#### Optimization: Rely on Consumer ProGuard Rules
Modern Android libraries (including ML Kit, Coil, and Room) ship with embedded consumer ProGuard rules that automatically keep the required reflection/JNI entry points. 
- **Remove** `-keep class coil.** { *; }` entirely. R8 will tree-shake Coil perfectly.
- **Remove** `-keep class com.google.mlkit.** { *; }` entirely. ML Kit's internal AAR rules will keep target classes.
- **Narrow down** the keep rules for SQLCipher and WireGuard to only prevent shrinking of native boundaries, allowing R8 to shrink the rest of the libraries.

---

### 3. Dynamic Feature Modules (Potential: 15-20MB savings)

By modularizing the app and utilizing Google Play Feature Delivery, we can deliver optional features on-demand. This directly reduces the base APK download size.

#### VPN Module (`vpn`)
- Move [VpnManager.kt](file:///Users/moc/Desktop/Omni%20Browser/app/src/main/java/com/rebelroot/omni/privacy/VpnManager.kt) and its `com.wireguard.android:tunnel` dependency into a separate dynamic feature module.
- Saves: **8-12MB** from the base APK.

#### Translation Module (`translation`)
- Move [TranslationManager.kt](file:///Users/moc/Desktop/Omni%20Browser/app/src/main/java/com/rebelroot/omni/tools/TranslationManager.kt) and its `com.google.mlkit:translate` dependency into a separate dynamic feature module.
- Saves: **5-8MB** from the base APK.

---

### 4. Resource & Native Packaging Optimizations (Potential: 2-3MB savings)

#### A. Image Asset Re-compression
- The asset [omni_home_logo.webp](file:///Users/moc/Desktop/Omni%20Browser/app/src/main/res/drawable-nodpi/omni_home_logo.webp) is currently 439KB.
- Since it represents a clean logo icon, we can either re-compress it at a lower quality WebP threshold (saving ~300KB) or convert the original SVG vector [ic_omni_logo.svg](file:///Users/moc/Desktop/Omni%20Browser/app/src/main/assets/ic_omni_logo.svg) to a Vector Drawable XML (saving ~430KB).

#### B. Native Libraries Compression Options
In `app/build.gradle.kts`, keep `useLegacyPackaging = false` so that native libraries are stored uncompressed inside the APK. While this slightly increases the initial raw APK download size, it avoids duplicating native `.so` files during installation, keeping the on-device footprint minimal.

---

## Recommended Action Plan

### Phase 1: Quick Wins (R8 & Dependency tuning)
1. Update [build.gradle.kts](file:///Users/moc/Desktop/Omni%20Browser/app/build.gradle.kts) dependency coordinates to `geckoview-arm64-v8a`.
2. Refine [proguard-rules.pro](file:///Users/moc/Desktop/Omni%20Browser/app/proguard-rules.pro) to remove redundant, broad keep rules (Coil, ML Kit, etc.).
3. Compress or vectorize [omni_home_logo.webp](file:///Users/moc/Desktop/Omni%20Browser/app/src/main/res/drawable-nodpi/omni_home_logo.webp).

### Phase 2: Production Distribution Tuning
1. Generate Android App Bundle (`.aab`) for production release instead of universal APKs.
2. Build per-ABI APKs for local distribution testing to ensure only `arm64-v8a` assets are included.

### Phase 3: Modularization (Dynamic Features)
1. Split VPN into a standalone Dynamic Feature module.
2. Split Translation into a standalone Dynamic Feature module.

---

## Risk Assessment

| Action | Risk Level | Mitigation |
|--------|------------|------------|
| Removing generic ProGuard keep rules | Medium | Verify that ML Kit translate and Coil still work in a minified release build. |
| Switching to `geckoview-arm64-v8a` | Low | Standard coordinate supported by Mozilla. Ensure NDK filters align. |
| Dynamic Feature Module split | High | Requires publishing as an App Bundle (.aab) on Google Play. Sideloaded APKs will need to be compiled as fused. |