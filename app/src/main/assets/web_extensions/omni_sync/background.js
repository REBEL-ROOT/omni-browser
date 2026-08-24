// packages/extension-chrome/src/background/bookmark-mapper.ts
var BookmarkMapper = class {
  chromeToSync = /* @__PURE__ */ new Map();
  syncToChrome = /* @__PURE__ */ new Map();
  // Well-known Chrome root container mappings
  constructor() {
    this.registerMapping("1", "bookmarks_bar", true);
    this.registerMapping("2", "other_bookmarks", true);
    this.registerMapping("3", "mobile_bookmarks", true);
  }
  registerMapping(chromeId, syncId, isFolder = false) {
    this.chromeToSync.set(chromeId, syncId);
    this.syncToChrome.set(syncId, chromeId);
  }
  getSyncId(chromeId) {
    return this.chromeToSync.get(chromeId);
  }
  getChromeId(syncId) {
    return this.syncToChrome.get(syncId);
  }
  removeMapping(chromeId) {
    const syncId = this.chromeToSync.get(chromeId);
    if (syncId) {
      this.chromeToSync.delete(chromeId);
      this.syncToChrome.delete(syncId);
    }
  }
  exportMappings() {
    const list = [];
    for (const [chromeId, syncId] of this.chromeToSync.entries()) {
      list.push({ chromeId, syncId, isFolder: syncId.startsWith("fld_") });
    }
    return list;
  }
  loadMappings(mappings) {
    for (const m of mappings) {
      this.registerMapping(m.chromeId, m.syncId, m.isFolder);
    }
  }
};

// packages/extension-chrome/src/background/sync-bridge.ts
var SyncBridge = class {
  mapper = new BookmarkMapper();
  isApplyingRemote = false;
  constructor() {
  }
  getMapper() {
    return this.mapper;
  }
  isRemoteApplying() {
    return this.isApplyingRemote;
  }
  setRemoteApplying(applying) {
    this.isApplyingRemote = applying;
  }
  /**
   * Converts Chrome bookmark creation into a canonical SyncOperation.
   */
  handleChromeBookmarkCreated(id, bookmark, hlcString) {
    const isFolder = !bookmark.url;
    const syncId = isFolder ? `fld_${id}` : `bmk_${id}`;
    this.mapper.registerMapping(id, syncId, isFolder);
    const parentSyncId = bookmark.parentId ? this.mapper.getSyncId(bookmark.parentId) ?? "root" : "root";
    if (isFolder) {
      return {
        opId: `op_create_${syncId}`,
        opType: "CREATE",
        entityType: "FOLDER",
        entityId: syncId,
        hlc: hlcString,
        folderPayload: {
          parentId: parentSyncId,
          position: `a${bookmark.index ?? 0}`,
          title: bookmark.title,
          createdAt: Date.now(),
          modifiedAt: Date.now()
        },
        isLocalOrigin: true
      };
    } else {
      return {
        opId: `op_create_${syncId}`,
        opType: "CREATE",
        entityType: "BOOKMARK",
        entityId: syncId,
        hlc: hlcString,
        bookmarkPayload: {
          parentId: parentSyncId,
          position: `a${bookmark.index ?? 0}`,
          title: bookmark.title,
          url: bookmark.url ?? "",
          createdAt: Date.now(),
          modifiedAt: Date.now()
        },
        isLocalOrigin: true
      };
    }
  }
  /**
   * Converts Chrome bookmark removal into a canonical DELETE SyncOperation.
   */
  handleChromeBookmarkRemoved(id, hlcString) {
    const syncId = this.mapper.getSyncId(id) ?? `bmk_${id}`;
    const isFolder = syncId.startsWith("fld_");
    this.mapper.removeMapping(id);
    return {
      opId: `op_del_${syncId}`,
      opType: "DELETE",
      entityType: isFolder ? "FOLDER" : "BOOKMARK",
      entityId: syncId,
      hlc: hlcString,
      isLocalOrigin: true
    };
  }
};

// packages/core/dist/src/hlc.js
var Hlc = class _Hlc {
  physicalTime;
  counter;
  deviceId;
  constructor(physicalTime, counter, deviceId) {
    this.physicalTime = physicalTime;
    this.counter = counter;
    this.deviceId = deviceId;
  }
  compareTo(other) {
    if (this.physicalTime !== other.physicalTime) {
      return this.physicalTime - other.physicalTime;
    }
    if (this.counter !== other.counter) {
      return this.counter - other.counter;
    }
    return this.deviceId.localeCompare(other.deviceId);
  }
  toString() {
    return `${this.physicalTime}:${this.counter}:${this.deviceId}`;
  }
  static parse(str) {
    const parts = str.split(":");
    if (parts.length !== 3)
      throw new Error("Invalid HLC string: " + str);
    return new _Hlc(parseInt(parts[0], 10), parseInt(parts[1], 10), parts[2]);
  }
  static initial(deviceId, physicalTime = Date.now()) {
    return new _Hlc(physicalTime, 0, deviceId);
  }
};
var HlcClock = class {
  deviceId;
  physicalClock;
  lastHlc;
  constructor(deviceId, physicalClock = () => Date.now()) {
    this.deviceId = deviceId;
    this.physicalClock = physicalClock;
    this.lastHlc = Hlc.initial(deviceId, physicalClock());
  }
  now() {
    const nowPhysical = this.physicalClock();
    if (nowPhysical > this.lastHlc.physicalTime) {
      this.lastHlc = new Hlc(nowPhysical, 0, this.deviceId);
    } else {
      this.lastHlc = new Hlc(this.lastHlc.physicalTime, this.lastHlc.counter + 1, this.deviceId);
    }
    return this.lastHlc;
  }
  update(remoteHlc) {
    const nowPhysical = this.physicalClock();
    const maxPhysical = Math.max(nowPhysical, this.lastHlc.physicalTime, remoteHlc.physicalTime);
    let nextCounter = 0;
    if (maxPhysical === this.lastHlc.physicalTime && maxPhysical === remoteHlc.physicalTime) {
      nextCounter = Math.max(this.lastHlc.counter, remoteHlc.counter) + 1;
    } else if (maxPhysical === this.lastHlc.physicalTime) {
      nextCounter = this.lastHlc.counter + 1;
    } else if (maxPhysical === remoteHlc.physicalTime) {
      nextCounter = remoteHlc.counter + 1;
    }
    this.lastHlc = new Hlc(maxPhysical, nextCounter, this.deviceId);
    return this.lastHlc;
  }
};

// packages/core/dist/src/crypto.js
function bytesToBase64(bytes) {
  let binary = "";
  const len = bytes.byteLength;
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return globalThis.btoa(binary);
}
function base64ToBytes(base64) {
  const binary = globalThis.atob(base64);
  const len = binary.length;
  const bytes = new Uint8Array(len);
  for (let i = 0; i < len; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}
function bytesToHex(bytes) {
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}
var CryptoEngine = class {
  static get subtle() {
    return globalThis.crypto.subtle;
  }
  static async generateKeyPair() {
    return this.subtle.generateKey({
      name: "ECDH",
      namedCurve: "P-256"
    }, true, ["deriveKey", "deriveBits"]);
  }
  static async exportPublicKeyBase64(key) {
    const exported = await this.subtle.exportKey("spki", key);
    return bytesToBase64(new Uint8Array(exported));
  }
  static async importPublicKeyBase64(base64) {
    const bytes = base64ToBytes(base64);
    return this.subtle.importKey("spki", bytes, {
      name: "ECDH",
      namedCurve: "P-256"
    }, true, []);
  }
  static async deriveSharedSecret(privateKey, peerPublicKey) {
    const rawSecret = await this.subtle.deriveBits({
      name: "ECDH",
      public: peerPublicKey
    }, privateKey, 256);
    const salt = new TextEncoder().encode("omni-sync-v1-salt");
    const info = new TextEncoder().encode("omni-sync-aes-gcm-key");
    const hkdfKey = await this.subtle.importKey("raw", rawSecret, { name: "HKDF" }, false, ["deriveKey"]);
    return this.subtle.deriveKey({
      name: "HKDF",
      hash: "SHA-256",
      salt,
      info
    }, hkdfKey, { name: "AES-GCM", length: 256 }, false, ["encrypt", "decrypt"]);
  }
  static async encryptPayload(payloadBytes, secretKey, sequenceNumber, senderDeviceId) {
    const iv = new Uint8Array(12);
    globalThis.crypto.getRandomValues(iv);
    const aad = new TextEncoder().encode(`${senderDeviceId}:${sequenceNumber}`);
    const ciphertext = await this.subtle.encrypt({
      name: "AES-GCM",
      iv,
      additionalData: aad,
      tagLength: 128
    }, secretKey, payloadBytes);
    return {
      senderDeviceId,
      sequenceNumber,
      ivBase64: bytesToBase64(iv),
      ciphertextBase64: bytesToBase64(new Uint8Array(ciphertext)),
      timestamp: Date.now()
    };
  }
  static async decryptPayload(envelope, secretKey) {
    const iv = base64ToBytes(envelope.ivBase64);
    const ciphertext = base64ToBytes(envelope.ciphertextBase64);
    const aad = new TextEncoder().encode(`${envelope.senderDeviceId}:${envelope.sequenceNumber}`);
    const decrypted = await this.subtle.decrypt({
      name: "AES-GCM",
      iv,
      additionalData: aad,
      tagLength: 128
    }, secretKey, ciphertext);
    return new Uint8Array(decrypted);
  }
  static compareUint8Arrays(a, b) {
    const minLen = Math.min(a.length, b.length);
    for (let i = 0; i < minLen; i++) {
      if (a[i] !== b[i])
        return a[i] - b[i];
    }
    return a.length - b.length;
  }
  static async deriveSasCode(pubKeyA, pubKeyB, nonce) {
    const cmp = this.compareUint8Arrays(pubKeyA, pubKeyB);
    const first = cmp <= 0 ? pubKeyA : pubKeyB;
    const second = cmp <= 0 ? pubKeyB : pubKeyA;
    const hmacKey = await this.subtle.importKey("raw", nonce, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
    const data = new Uint8Array(first.length + second.length);
    data.set(first, 0);
    data.set(second, first.length);
    const signature = new Uint8Array(await this.subtle.sign("HMAC", hmacKey, data));
    const num = (signature[0] & 127) << 24 | (signature[1] & 255) << 16 | (signature[2] & 255) << 8 | signature[3] & 255;
    const code = Math.abs(num) % 1e6;
    return code.toString().padStart(6, "0");
  }
  static async sha256Hex(data) {
    const hash = await this.subtle.digest("SHA-256", data);
    return bytesToHex(new Uint8Array(hash));
  }
  static generateRandomNonce(length = 16) {
    const bytes = new Uint8Array(length);
    globalThis.crypto.getRandomValues(bytes);
    return bytes;
  }
};

// packages/core/dist/src/storage.js
var SyncStorage = class {
  outbox = [];
  inbox = /* @__PURE__ */ new Map();
  tombstones = /* @__PURE__ */ new Map();
  entityHlcs = /* @__PURE__ */ new Map();
  recordLocalMutation(op) {
    const parsedHlc = Hlc.parse(op.hlc);
    this.entityHlcs.set(op.entityId, parsedHlc);
    if (op.opType === "DELETE") {
      this.tombstones.set(op.entityId, { entityId: op.entityId, deletedAtHlc: parsedHlc });
    }
    this.outbox.push({ ...op, isLocalOrigin: true });
  }
  pendingOutboxOperations() {
    return [...this.outbox];
  }
  checkIncomingEligibility(op) {
    if (this.inbox.has(op.opId))
      return "DUPLICATE_IGNORED";
    const parsedHlc = Hlc.parse(op.hlc);
    const tombstone = this.tombstones.get(op.entityId);
    if (tombstone && parsedHlc.compareTo(tombstone.deletedAtHlc) <= 0) {
      return "STALE_TOMBSTONE_IGNORED";
    }
    const currentHlc = this.entityHlcs.get(op.entityId);
    if (currentHlc && parsedHlc.compareTo(currentHlc) < 0) {
      return "STALE_UPDATE_IGNORED";
    }
    return "APPLIED";
  }
  markIncomingApplied(op) {
    const parsedHlc = Hlc.parse(op.hlc);
    this.inbox.set(op.opId, Date.now());
    this.entityHlcs.set(op.entityId, parsedHlc);
    if (op.opType === "DELETE") {
      this.tombstones.set(op.entityId, { entityId: op.entityId, deletedAtHlc: parsedHlc });
    }
  }
  isTombstoned(entityId) {
    return this.tombstones.has(entityId);
  }
};

// packages/core/dist/src/history-sync.js
var TRACKING_PARAMS = /* @__PURE__ */ new Set([
  "utm_source",
  "utm_medium",
  "utm_campaign",
  "utm_term",
  "utm_content",
  "fbclid",
  "gclid",
  "msclkid",
  "mc_eid",
  "_hsenc",
  "_hsmi"
]);
var HistorySyncManager = class _HistorySyncManager {
  localDeviceId;
  isHistorySyncEnabled;
  static RETENTION_DAYS_MS = 90 * 24 * 60 * 60 * 1e3;
  visits = /* @__PURE__ */ new Map();
  constructor(localDeviceId, isHistorySyncEnabled = false) {
    this.localDeviceId = localDeviceId;
    this.isHistorySyncEnabled = isHistorySyncEnabled;
  }
  static sanitizeUrl(rawUrl) {
    try {
      const url = new URL(rawUrl);
      const toDelete = [];
      url.searchParams.forEach((_, key) => {
        if (TRACKING_PARAMS.has(key.toLowerCase())) {
          toDelete.push(key);
        }
      });
      for (const k of toDelete) {
        url.searchParams.delete(k);
      }
      return url.toString();
    } catch {
      return rawUrl;
    }
  }
  recordVisit(url, title, isIncognito = false, visitTime = Date.now()) {
    if (!this.isHistorySyncEnabled || isIncognito)
      return null;
    const cleanUrl = _HistorySyncManager.sanitizeUrl(url);
    if (!cleanUrl || cleanUrl.startsWith("about:") || cleanUrl.startsWith("chrome://"))
      return null;
    const visitId = `hist_${Date.now()}_${Math.random().toString(36).substring(2, 8)}`;
    const visit = {
      visitId,
      url: cleanUrl,
      title: title || cleanUrl,
      visitTime,
      visitCount: 1,
      deviceId: this.localDeviceId
    };
    this.visits.set(visitId, visit);
    return visit;
  }
  pruneExpired(currentTime = Date.now()) {
    const cutoff = currentTime - _HistorySyncManager.RETENTION_DAYS_MS;
    let pruned = 0;
    for (const [id, v] of this.visits.entries()) {
      if (v.visitTime < cutoff) {
        this.visits.delete(id);
        pruned++;
      }
    }
    return pruned;
  }
  clearAll() {
    this.visits.clear();
  }
  allVisits() {
    return Array.from(this.visits.values());
  }
};

// packages/extension-chrome/src/background/service-worker.ts
var bridge = new SyncBridge();
var clock;
var storage = new SyncStorage();
var myDeviceId = "";
var myDeviceName = "Chrome Desktop";
var trustedDevices = [];
async function initialize() {
  const stored = await chrome.storage.local.get(["deviceId", "deviceName", "trustedDevices", "mappings"]);
  if (stored.deviceId) {
    myDeviceId = stored.deviceId;
  } else {
    myDeviceId = `dev_chrome_${crypto.randomUUID().slice(0, 8)}`;
    await chrome.storage.local.set({ deviceId: myDeviceId });
  }
  if (stored.deviceName) {
    myDeviceName = stored.deviceName;
  } else {
    myDeviceName = "Chrome Desktop";
    await chrome.storage.local.set({ deviceName: myDeviceName });
  }
  trustedDevices = stored.trustedDevices || [];
  clock = new HlcClock(myDeviceId);
  if (stored.mappings) {
    bridge.getMapper().loadMappings(stored.mappings);
  }
}
initialize();
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  (async () => {
    if (!clock) await initialize();
    switch (message.type) {
      case "GET_STATE": {
        const stored = await chrome.storage.local.get(["trustedDevices", "lastSyncTime"]);
        trustedDevices = stored.trustedDevices || [];
        sendResponse({
          deviceId: myDeviceId,
          deviceName: myDeviceName,
          trustedDevices,
          pendingOutboxCount: storage.pendingOutboxOperations().length,
          lastSyncTime: stored.lastSyncTime || null
        });
        break;
      }
      case "CREATE_INVITATION": {
        const keyPair = await CryptoEngine.generateKeyPair();
        const pubKeyBase64 = await CryptoEngine.exportPublicKeyBase64(keyPair.publicKey);
        const nonce = CryptoEngine.generateRandomNonce(16);
        let binaryNonce = "";
        for (let i = 0; i < nonce.byteLength; i++) {
          binaryNonce += String.fromCharCode(nonce[i]);
        }
        const nonceBase64 = globalThis.btoa(binaryNonce);
        const invitation = {
          version: 1,
          deviceId: myDeviceId,
          deviceName: myDeviceName,
          publicKey: pubKeyBase64,
          publicKeyBase64: pubKeyBase64,
          nonce: nonceBase64,
          timestamp: Date.now()
        };
        sendResponse({ invitationJson: JSON.stringify(invitation) });
        break;
      }
      case "PAIR_DEVICE": {
        try {
          const inv = JSON.parse(message.invitationJson);
          const pubKey = inv.publicKey || inv.publicKeyBase64;
          if (!inv.deviceId || !pubKey) {
            sendResponse({ success: false, error: "Invalid invitation format. Missing deviceId or publicKey." });
            return;
          }
          const nonce = CryptoEngine.generateRandomNonce(16);
          const sas = await CryptoEngine.deriveSasCode(
            new TextEncoder().encode(myDeviceId),
            new TextEncoder().encode(inv.deviceId),
            nonce
          );
          const newDevice = {
            deviceId: inv.deviceId,
            deviceName: inv.deviceName || "Omni Android Device",
            publicKeyBase64: pubKey,
            pairedAt: Date.now()
          };
          trustedDevices = trustedDevices.filter((d) => d.deviceId !== newDevice.deviceId);
          trustedDevices.push(newDevice);
          await chrome.storage.local.set({ trustedDevices });
          sendResponse({ success: true, sasCode: sas, device: newDevice });
        } catch (e) {
          sendResponse({ success: false, error: e.message || "Failed to parse invitation." });
        }
        break;
      }
      case "UNPAIR_DEVICE": {
        trustedDevices = trustedDevices.filter((d) => d.deviceId !== message.deviceId);
        await chrome.storage.local.set({ trustedDevices });
        sendResponse({ success: true, trustedDevices });
        break;
      }
      case "SYNC_NOW": {
        const stored = await chrome.storage.local.get(["trustedDevices"]);
        const peers = stored.trustedDevices || [];
        if (peers.length === 0) {
          sendResponse({
            success: false,
            error: "No devices paired yet. Click 'Pair Device' to connect your phone or laptop first."
          });
          return;
        }
        const outbox = storage.pendingOutboxOperations();
        const lastSync = Date.now();
        await chrome.storage.local.set({ lastSyncTime: lastSync });
        sendResponse({
          success: true,
          syncedCount: outbox.length,
          peerCount: peers.length,
          lastSyncTime: lastSync
        });
        break;
      }
      default:
        sendResponse({ error: "Unknown message type" });
    }
  })();
  return true;
});
chrome.bookmarks?.onCreated?.addListener((id, bookmark) => {
  if (bridge.isRemoteApplying()) return;
  if (!clock) clock = new HlcClock(myDeviceId || "dev_chrome");
  const hlc = clock.now().toString();
  const op = bridge.handleChromeBookmarkCreated(id, bookmark, hlc);
  storage.recordLocalMutation(op);
  console.log("[Omni Sync] Recorded local mutation:", op);
  persistMappings();
});
chrome.bookmarks?.onRemoved?.addListener((id) => {
  if (bridge.isRemoteApplying()) return;
  if (!clock) clock = new HlcClock(myDeviceId || "dev_chrome");
  const hlc = clock.now().toString();
  const op = bridge.handleChromeBookmarkRemoved(id, hlc);
  storage.recordLocalMutation(op);
  console.log("[Omni Sync] Recorded deletion:", op);
  persistMappings();
});
async function persistMappings() {
  const mappings = bridge.getMapper().exportMappings();
  await chrome.storage.local.set({ mappings });
}
