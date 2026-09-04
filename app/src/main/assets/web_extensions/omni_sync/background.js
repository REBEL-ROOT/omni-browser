// packages/extension-firefox/src/background/firefox-mapper.ts
var FirefoxPlacesMapper = class {
  guidToSync = /* @__PURE__ */ new Map();
  syncToGuid = /* @__PURE__ */ new Map();
  constructor() {
    this.registerMapping("root________", "root");
    this.registerMapping("menu________", "root");
    this.registerMapping("toolbar_____", "bookmarks_bar");
    this.registerMapping("unfiled_____", "other_bookmarks");
    this.registerMapping("mobile______", "mobile_bookmarks");
  }
  registerMapping(guid, syncId) {
    this.guidToSync.set(guid, syncId);
    this.syncToGuid.set(syncId, guid);
  }
  removeMapping(guid) {
    const syncId = this.guidToSync.get(guid);
    if (syncId) {
      this.syncToGuid.delete(syncId);
    }
    this.guidToSync.delete(guid);
  }
  getSyncId(guid) {
    return this.guidToSync.get(guid);
  }
  getGuid(syncId) {
    return this.syncToGuid.get(syncId);
  }
  exportMappings() {
    return Object.fromEntries(this.guidToSync.entries());
  }
  loadMappings(mappings) {
    for (const [guid, syncId] of Object.entries(mappings)) {
      this.registerMapping(guid, syncId);
    }
  }
};

// packages/extension-firefox/src/background/sync-bridge.ts
var FirefoxSyncBridge = class {
  mapper = new FirefoxPlacesMapper();
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
  handleFirefoxBookmarkCreated(id, bookmark, hlcString) {
    const isFolder = !bookmark.url;
    const syncId = isFolder ? `fld_${id}` : `bmk_${id}`;
    this.mapper.registerMapping(id, syncId);
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
  handleFirefoxBookmarkRemoved(id, hlcString) {
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
  async applyRemoteOperation(op) {
    this.setRemoteApplying(true);
    const bookmarksApi = globalThis.browser?.bookmarks || globalThis.chrome?.bookmarks;
    if (!bookmarksApi) {
      this.setRemoteApplying(false);
      return;
    }
    try {
      if (op.opType === "DELETE") {
        const guid = this.mapper.getGuid(op.entityId);
        if (guid) {
          try {
            await bookmarksApi.removeTree(guid);
          } catch (_e) {
            try {
              await bookmarksApi.remove(guid);
            } catch (_e2) {
            }
          }
          this.mapper.removeMapping(guid);
        }
      } else if (op.opType === "CREATE") {
        const existingGuid = this.mapper.getGuid(op.entityId);
        if (existingGuid) return;
        const parentGuid = (op.bookmarkPayload?.parentId || op.folderPayload?.parentId) === "root" ? "toolbar_____" : this.mapper.getGuid(op.bookmarkPayload?.parentId || op.folderPayload?.parentId || "") || "toolbar_____";
        if (op.entityType === "FOLDER" && op.folderPayload) {
          const created = await bookmarksApi.create({
            parentId: parentGuid,
            title: op.folderPayload.title || "Folder"
          });
          this.mapper.registerMapping(created.id, op.entityId);
        } else if (op.entityType === "BOOKMARK" && op.bookmarkPayload) {
          const created = await bookmarksApi.create({
            parentId: parentGuid,
            title: op.bookmarkPayload.title || op.bookmarkPayload.url || "Bookmark",
            url: op.bookmarkPayload.url || "https://"
          });
          this.mapper.registerMapping(created.id, op.entityId);
        }
      } else if (op.opType === "UPDATE_CONTENT") {
        const guid = this.mapper.getGuid(op.entityId);
        if (guid) {
          const changes = {};
          if (op.bookmarkPayload) {
            if (op.bookmarkPayload.title) changes.title = op.bookmarkPayload.title;
            if (op.bookmarkPayload.url) changes.url = op.bookmarkPayload.url;
          } else if (op.folderPayload) {
            if (op.folderPayload.title) changes.title = op.folderPayload.title;
          }
          await bookmarksApi.update(guid, changes);
        }
      }
    } catch (e) {
      console.warn("[Omni Sync Firefox] Failed to apply remote operation:", op, e);
    } finally {
      this.setRemoteApplying(false);
    }
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

// packages/extension-firefox/src/background/service-worker.ts
var browserApi = globalThis.browser || globalThis.chrome;
var bridge = new FirefoxSyncBridge();
var clock;
var storage = new SyncStorage();
var myDeviceId = "";
var myDeviceName = "Firefox Desktop";
var trustedDevices = [];
async function initialize() {
  const stored = await browserApi.storage.local.get(["deviceId", "deviceName", "trustedDevices", "mappings"]);
  if (stored.deviceId) {
    myDeviceId = stored.deviceId;
  } else {
    myDeviceId = `dev_firefox_${crypto.randomUUID().slice(0, 8)}`;
    await browserApi.storage.local.set({ deviceId: myDeviceId });
  }
  if (stored.deviceName) {
    myDeviceName = stored.deviceName;
  } else {
    myDeviceName = "Firefox Desktop";
    await browserApi.storage.local.set({ deviceName: myDeviceName });
  }
  trustedDevices = stored.trustedDevices || [];
  clock = new HlcClock(myDeviceId);
  if (stored.mappings) {
    bridge.getMapper().loadMappings(stored.mappings);
  }
}
initialize();
browserApi.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  (async () => {
    if (!clock) await initialize();
    switch (message.type) {
      case "GET_STATE": {
        const stored = await browserApi.storage.local.get(["trustedDevices", "lastSyncTime"]);
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
            lanHost: inv.lanHost || null,
            lanPort: inv.lanPort || 8765,
            pairedAt: Date.now()
          };
          trustedDevices = trustedDevices.filter((d) => d.deviceId !== newDevice.deviceId);
          trustedDevices.push(newDevice);
          await browserApi.storage.local.set({ trustedDevices });
          sendResponse({ success: true, sasCode: sas, device: newDevice });
        } catch (e) {
          sendResponse({ success: false, error: e.message || "Failed to parse invitation." });
        }
        break;
      }
      case "UNPAIR_DEVICE": {
        trustedDevices = trustedDevices.filter((d) => d.deviceId !== message.deviceId);
        await browserApi.storage.local.set({ trustedDevices });
        sendResponse({ success: true, trustedDevices });
        break;
      }
      case "SYNC_NOW": {
        const stored = await browserApi.storage.local.get(["trustedDevices"]);
        const peers = stored.trustedDevices || [];
        if (peers.length === 0) {
          sendResponse({
            success: false,
            error: "No devices paired yet. Click 'Pair Device' to connect your phone or laptop first."
          });
          return;
        }
        let outbox = storage.pendingOutboxOperations();
        if (outbox.length === 0) {
          const tree = await new Promise((resolve) => {
            const bmkApi = browserApi?.bookmarks;
            if (bmkApi?.getTree) {
              const res = bmkApi.getTree((t) => resolve(t || []));
              if (res && typeof res.then === "function") res.then((t) => resolve(t || [])).catch(() => resolve([]));
            } else {
              resolve([]);
            }
          });
          if (tree && tree.length > 0) {
            let harvest2 = function(nodes) {
              for (const n of nodes) {
                if (n.url) {
                  const hlc = clock.now().toString();
                  const op = bridge.handleFirefoxBookmarkCreated(n.id, n, hlc);
                  storage.recordLocalMutation(op);
                }
                if (n.children) harvest2(n.children);
              }
            };
            var harvest = harvest2;
            harvest2(tree);
            outbox = storage.pendingOutboxOperations();
          }
        }
        const localTabs = await new Promise((resolve) => {
          const tabsApi = browserApi?.tabs;
          if (tabsApi?.query) {
            const res = tabsApi.query({}, (tabs) => {
              const cleaned = (tabs || []).filter((t) => t.url && !t.incognito && !t.url.startsWith("chrome://") && !t.url.startsWith("about:")).map((t) => ({ title: t.title || t.url, url: t.url, favicon: t.favIconUrl || null }));
              resolve(cleaned);
            });
            if (res && typeof res.then === "function") {
              res.then((tabs) => {
                const cleaned = (tabs || []).filter((t) => t.url && !t.incognito && !t.url.startsWith("chrome://") && !t.url.startsWith("about:")).map((t) => ({ title: t.title || t.url, url: t.url, favicon: t.favIconUrl || null }));
                resolve(cleaned);
              }).catch(() => resolve([]));
            }
          } else {
            resolve([]);
          }
        });
        let totalApplied = 0;
        let lastSync = Date.now();
        let syncErrors = [];
        for (const peer of peers) {
          try {
            const result = await syncWithLanPeer(peer, outbox, localTabs);
            if (result && result.remoteOperations && Array.isArray(result.remoteOperations)) {
              for (const remoteOp of result.remoteOperations) {
                await bridge.applyRemoteOperation(remoteOp);
                totalApplied++;
              }
              await persistMappings();
            }
            if (result && result.remoteTabs && Array.isArray(result.remoteTabs)) {
              await browserApi.storage.local.set({
                remoteTabs: result.remoteTabs,
                remoteDeviceName: peer.deviceName || "Omni Android Phone"
              });
            }
          } catch (err) {
            console.warn(`[Omni Sync Firefox] LAN sync with ${peer.deviceName || peer.deviceId} failed:`, err.message);
            syncErrors.push(`${peer.deviceName || "Peer"}: ${err.message}`);
          }
        }
        await browserApi.storage.local.set({ lastSyncTime: lastSync });
        if (syncErrors.length < peers.length) {
          triggerSuccessBadge();
          const peerNames = peers.map((p) => p.deviceName || "Phone").join(", ");
          showSyncNotification(
            "Omni Sync: Synchronized",
            `Synced ${outbox.length} bookmarks & ${localTabs.length} tabs with ${peerNames}`
          );
        }
        sendResponse({
          success: syncErrors.length < peers.length,
          syncedCount: outbox.length,
          appliedRemoteCount: totalApplied,
          peerCount: peers.length,
          lastSyncTime: lastSync,
          errors: syncErrors.length > 0 ? syncErrors : void 0
        });
        break;
      }
      default:
        sendResponse({ error: "Unknown message type" });
    }
  })();
  return true;
});
function showSyncNotification(title, message) {
  try {
    const notifApi = browserApi?.notifications || chrome?.notifications;
    if (notifApi?.create) {
      notifApi.create({
        type: "basic",
        iconUrl: browserApi?.runtime?.getURL("icons/icon128.png") || "icons/icon128.png",
        title,
        message,
        priority: 1
      });
    }
  } catch (_e) {
  }
}
function triggerSuccessBadge() {
  try {
    const action = browserApi?.browserAction || chrome?.action || chrome?.browserAction;
    if (action?.setBadgeText) {
      action.setBadgeText({ text: "\u2713" });
      action.setBadgeBackgroundColor({ color: "#10B981" });
      setTimeout(() => {
        try {
          action.setBadgeText({ text: "" });
        } catch (_e) {
        }
      }, 3500);
    }
  } catch (_e) {
  }
}
async function syncWithLanPeer(device, outboxOps, openTabs = []) {
  const candidateHosts = [];
  if (device.lanHost) candidateHosts.push(device.lanHost);
  if (device.lanHost === "10.0.2.15" || device.lanHost && device.lanHost.startsWith("10.0.2.")) {
    candidateHosts.unshift("127.0.0.1", "localhost");
  } else if (!candidateHosts.includes("127.0.0.1")) {
    candidateHosts.push("127.0.0.1");
  }
  const port = device.lanPort || 8765;
  const payload = {
    action: "SYNC_EXCHANGE",
    deviceId: myDeviceId,
    deviceName: myDeviceName,
    operations: outboxOps,
    openTabs,
    timestamp: Date.now()
  };
  let lastError = null;
  for (const host of candidateHosts) {
    try {
      const wsUrl = `ws://${host}:${port}/sync`;
      const result = await new Promise((resolve, reject) => {
        const ws = new WebSocket(wsUrl);
        const timer = setTimeout(() => {
          try {
            ws.close();
          } catch (_e) {
          }
          reject(new Error("WebSocket timeout on " + host));
        }, 3e3);
        ws.onopen = () => {
          ws.send(JSON.stringify(payload));
        };
        ws.onmessage = (event) => {
          clearTimeout(timer);
          try {
            const data = JSON.parse(event.data);
            try {
              ws.close();
            } catch (_e) {
            }
            resolve(data);
          } catch (e) {
            reject(e);
          }
        };
        ws.onerror = (err) => {
          clearTimeout(timer);
          reject(err);
        };
      });
      return result;
    } catch (_wsErr) {
      try {
        const httpUrl = `http://${host}:${port}/api/sync/exchange`;
        const resp = await fetch(httpUrl, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload)
        });
        if (resp.ok) {
          return await resp.json();
        }
      } catch (httpErr) {
        lastError = httpErr;
      }
    }
  }
  throw lastError || new Error("Could not connect to peer on any host (" + candidateHosts.join(", ") + ")");
}
browserApi.bookmarks?.onCreated?.addListener((id, bookmark) => {
  if (bridge.isRemoteApplying()) return;
  if (!clock) clock = new HlcClock(myDeviceId || "dev_firefox");
  const hlc = clock.now().toString();
  const op = bridge.handleFirefoxBookmarkCreated(id, bookmark, hlc);
  storage.recordLocalMutation(op);
  persistMappings();
});
browserApi.bookmarks?.onRemoved?.addListener((id) => {
  if (bridge.isRemoteApplying()) return;
  if (!clock) clock = new HlcClock(myDeviceId || "dev_firefox");
  const hlc = clock.now().toString();
  const op = bridge.handleFirefoxBookmarkRemoved(id, hlc);
  storage.recordLocalMutation(op);
  persistMappings();
});
async function persistMappings() {
  const mappings = bridge.getMapper().exportMappings();
  await browserApi.storage.local.set({ mappings });
}
