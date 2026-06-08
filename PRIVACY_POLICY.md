# Privacy Policy & Data Safety Declaration

**Last Updated:** June 8, 2026  
**Publisher:** RebelRoot  
**App Name:** Omni Browser  
**Package Name:** `com.rebelroot.omni`

This document details the Privacy Policy and Data Security configurations for Omni Browser. Because Omni Browser is built on a strict privacy-first, on-device-first architecture, the application does not collect, monitor, store, or share any user personal information on remote servers.

---

## 1. Privacy Policy

### 1.1 Data Collection & Transmission
- **Zero Remote Storage:** RebelRoot does not host any backend databases, account servers, or analytics pipelines for Omni Browser. All browsing history, bookmarks, open tabs, cookies, and locker files remain entirely on your local device.
- **No Telemetry / Analytics:** All analytics and diagnostic telemetry inside secondary components have been deactivated:
  - **Google ML Kit Telemetry Opt-Out:** Omni Browser opts out of ML Kit telemetry tracking. No translation strings, scanner logs, or device details are sent to Google.
  - **No Third-Party SDKs:** The app contains no advertisement SDKs, crash reporters (like Firebase Crashlytics), or telemetry tracking code.

### 1.2 Permissions & Usage Disclosure
Omni Browser requests permissions only when necessary to perform core browser and utility features. All data processed via these permissions remains on-device:

| Permission | Purpose | Data Safety Context |
|------------|---------|---------------------|
| **INTERNET** | Core web browsing, loading websites, and downloading web resources. | No URL requests or browsing traffic are logged or shared by RebelRoot. |
| **USE_BIOMETRIC** | Authenticating user access to the secure Locker feature. | Handled via Android's Secure Biometric API. Biometric credentials are stored securely in the device's hardware (TEE/StrongBox) and are never accessible to the app. |
| **CAMERA** | Allowing websites to access the camera for in-browser video calls (WebRTC), and scanning QR codes or document scanning. | Processing is local. Video frames are only streamed to web destinations explicitly approved by the user. |
| **RECORD_AUDIO** | Allowing websites to capture audio for voice calls (WebRTC) and audio recordings. | Streams are peer-to-peer or sent directly to websites explicitly authorized by the user. |
| **ACCESS_FINE_LOCATION** & **ACCESS_COARSE_LOCATION** | Sharing your device location with websites that request it (e.g., maps, local delivery). | Geolocation coordinates are only accessed after explicit user prompt approvals and are sent directly to the requesting site. RebelRoot never collects location history. |
| **READ / WRITE STORAGE** & **READ_MEDIA_** | Accessing the device storage to save file downloads or import/export files from/into the secure Locker. | Uses MediaStore and Scoped Storage APIs to restrict permission scopes to media folders only. |
| **POST_NOTIFICATIONS** | Displaying progress bars and completion statuses for active file downloads. | Local system notifications only. |

### 1.3 Third-Party Services & Libraries
Omni Browser incorporates open-source and Google libraries for local utilities:
- **Mozilla GeckoView Engine:** Renders web pages. It operates locally and adheres to standard Web API protocols.
- **WireGuard VPN:** Operates locally to encrypt external web traffic. Omni Browser does not monitor, route, or decrypt network traffic passing through the tunnel.
- **Google Play Services (Code & Document Scanners):** Utilizes zero-permission clients where Google Play Services handles OCR and scanning operations locally on-device.

### 1.4 Data Deletion
All local data can be incinerated instantly using the built-in **Fire Button** (data incinerator), which executes clean database and memory wipes. Uninstalling the application completely deletes all browser records, encrypted lockers, and database files.

---

## 2. Google Play Console Data Safety Form Guide

When publishing Omni Browser on the Google Play Console, use the following guide to fill out the **Data Safety Form** based on the app's technical implementation:

### Section 1: Data Collection and Sharing
- **Does your app collect or share any of the required user data types?**  
  Select **No**. (Omni Browser does not collect or share any user data. All data resides locally).

### Section 2: Security Practices
- **Is all of the user data collected by your app encrypted in transit?**  
  Select **Yes** (or N/A, since no data is collected. If Yes, disclose that all web traffic uses industry-standard HTTPS protocols, and the app enforces TLS for WebExtension APIs).
- **Do you provide a way for users to request that their data be deleted?**  
  Select **Yes**. Disclose that users can delete all data instantly in-app using the "Fire Button" or by uninstalling the application.

### Section 3: Device or Other IDs
- **Does your app collect or share Device or other IDs?**  
  Select **No**. (We do not collect Android ID, IMEI, MAC address, or advertising IDs).

---

## 3. Data Security Implementation Details (Technical)

To back up the Data Safety declarations, Omni Browser incorporates the following security controls at the code level:

1. **SQLCipher Database Encryption:**
   All SQLite databases (history, settings, bookmarks) are encrypted using **SQLCipher (AES-256)** via the Android Room library. The encryption key is derived securely and stored using Android Keystore.
2. **EncryptedFile Storage:**
   Imported locker files are encrypted on-disk using AndroidX Security-Crypto wrapper around AES-256 GCM encryption.
3. **No Cleartext Traffic:**
   Cleartext HTTP requests from the Android Application layers are blocked (HTTPS enforcement).
4. **Log Stripping:**
   Production release builds run aggressive R8 optimizations to strip out all debug and informative log outputs, preventing sensitive browser variables from being dumped into the system logcat.
