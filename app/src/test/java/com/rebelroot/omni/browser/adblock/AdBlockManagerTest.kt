package com.rebelroot.omni.browser.adblock

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class AdBlockManagerTest {

    private fun createTestManager(masterEnabled: Boolean = true): Pair<AdBlockManager, MutableSet<String>> {
        val manager = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").let {
            it.isAccessible = true
            val unsafe = it.get(null) as sun.misc.Unsafe
            unsafe.allocateInstance(AdBlockManager::class.java) as AdBlockManager
        }

        val mockPrefs = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getBoolean" -> {
                    val key = args?.getOrNull(0) as? String
                    val defVal = args?.getOrNull(1) as? Boolean ?: true
                    if (key == "master_enabled") masterEnabled else defVal
                }
                "getLong" -> args?.getOrNull(1) ?: 0L
                "getString" -> args?.getOrNull(1)
                else -> null
            }
        } as SharedPreferences

        val prefsField = AdBlockManager::class.java.getDeclaredField("prefs")
        prefsField.isAccessible = true
        prefsField.set(manager, mockPrefs)

        val blockedField = AdBlockManager::class.java.getDeclaredField("blockedDomains")
        blockedField.isAccessible = true
        val blockedSet = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
        blockedField.set(manager, blockedSet)

        return Pair(manager, blockedSet)
    }

    @Test
    fun testExactDomainBlocked() {
        val (manager, blockedSet) = createTestManager()
        blockedSet.add("exoclick.com")
        blockedSet.add("doubleclick.net")

        assertTrue(manager.isHostBlocked("exoclick.com"))
        assertTrue(manager.isHostBlocked("doubleclick.net"))
        assertFalse(manager.isHostBlocked("wikipedia.org"))
    }

    @Test
    fun testSubdomainHierarchicalBlocking() {
        val (manager, blockedSet) = createTestManager()
        blockedSet.add("exoclick.com")
        blockedSet.add("ads.google.com")

        // Subdomain matching root domain
        assertTrue(manager.isHostBlocked("syndication.exoclick.com"))
        assertTrue(manager.isHostBlocked("a.b.c.exoclick.com"))
        assertTrue(manager.isHostBlocked("www.syndication.exoclick.com"))

        // Subdomain matching subdomain rule
        assertTrue(manager.isHostBlocked("sub.ads.google.com"))
        assertTrue(manager.isHostBlocked("cdn.sub.ads.google.com"))

        // Unrelated subdomains of allowed domains should NOT be blocked
        assertFalse(manager.isHostBlocked("docs.google.com"))
        assertFalse(manager.isHostBlocked("mail.google.com"))
    }

    @Test
    fun testEssentialAuthDomainsNeverBlocked() {
        val (manager, blockedSet) = createTestManager()
        // Even if bad filter rule tried to block google.com or accounts.google.com
        blockedSet.add("google.com")
        blockedSet.add("accounts.google.com")
        blockedSet.add("github.com")
        blockedSet.add("appleid.apple.com")

        assertFalse(manager.isHostBlocked("accounts.google.com"))
        assertFalse(manager.isHostBlocked("apis.google.com"))
        assertFalse(manager.isHostBlocked("ssl.gstatic.com"))
        assertFalse(manager.isHostBlocked("accounts.youtube.com"))
        assertFalse(manager.isHostBlocked("appleid.apple.com"))
        assertFalse(manager.isHostBlocked("login.microsoftonline.com"))
        assertFalse(manager.isHostBlocked("login.live.com"))
        assertFalse(manager.isHostBlocked("github.com"))
    }

    @Test
    fun testWwwPrefixStripped() {
        val (manager, blockedSet) = createTestManager()
        blockedSet.add("popads.net")

        assertTrue(manager.isHostBlocked("www.popads.net"))
        assertTrue(manager.isHostBlocked("popads.net"))
    }

    @Test
    fun testMasterToggleDisabled() {
        val (manager, blockedSet) = createTestManager(masterEnabled = false)
        blockedSet.add("exoclick.com")

        assertFalse(manager.isHostBlocked("exoclick.com"))
        assertFalse(manager.isHostBlocked("syndication.exoclick.com"))
    }

    @Test
    fun testEmptyAndNullHostsSafe() {
        val (manager, _) = createTestManager()

        assertFalse(manager.isHostBlocked(null))
        assertFalse(manager.isHostBlocked(""))
        assertFalse(manager.isHostBlocked("   "))
    }

    @Test
    fun testImmuneDomainsHelper() {
        assertTrue(AdBlockManager.isImmuneDomain("google.com"))
        assertTrue(AdBlockManager.isImmuneDomain("www.google.com"))
        assertTrue(AdBlockManager.isImmuneDomain("google.co.uk"))
        assertTrue(AdBlockManager.isImmuneDomain("google.de"))
        assertTrue(AdBlockManager.isImmuneDomain("google.co.in"))
        assertTrue(AdBlockManager.isImmuneDomain("amazon.com"))
        assertTrue(AdBlockManager.isImmuneDomain("www.amazon.de"))
        assertTrue(AdBlockManager.isImmuneDomain("spotify.com"))
        assertTrue(AdBlockManager.isImmuneDomain("youtube.com"))
        assertTrue(AdBlockManager.isImmuneDomain("bing.com"))
        assertTrue(AdBlockManager.isImmuneDomain("duckduckgo.com"))
        assertTrue(AdBlockManager.isImmuneDomain("wikipedia.org"))
        assertTrue(AdBlockManager.isImmuneDomain("apple.com"))
        assertTrue(AdBlockManager.isImmuneDomain("github.com"))
        assertTrue(AdBlockManager.isImmuneDomain("reddit.com"))

        assertFalse(AdBlockManager.isImmuneDomain("adservice.google.com"))
        assertFalse(AdBlockManager.isImmuneDomain("googleadservices.com"))
        assertFalse(AdBlockManager.isImmuneDomain("doubleclick.net"))
        assertFalse(AdBlockManager.isImmuneDomain("amazon-adsystem.com"))
        assertFalse(AdBlockManager.isImmuneDomain("exoclick.com"))
    }

    @Test
    fun testImmuneDomainsProtectedInIsHostBlockedEvenIfPoisonedInCache() {
        val (manager, blockedSet) = createTestManager()
        // Simulate stale/poisoned cache containing major domains
        blockedSet.add("google.com")
        blockedSet.add("amazon.com")
        blockedSet.add("spotify.com")
        blockedSet.add("youtube.com")
        blockedSet.add("bing.com")
        blockedSet.add("adservice.google.com")
        blockedSet.add("doubleclick.net")

        // Immune domains and their top-level search/browsing subdomains must NOT be blocked
        assertFalse(manager.isHostBlocked("www.google.com"))
        assertFalse(manager.isHostBlocked("google.com"))
        assertFalse(manager.isHostBlocked("search.google.com"))
        assertFalse(manager.isHostBlocked("spotify.com"))
        assertFalse(manager.isHostBlocked("www.spotify.com"))
        assertFalse(manager.isHostBlocked("amazon.com"))
        assertFalse(manager.isHostBlocked("www.amazon.com"))
        assertFalse(manager.isHostBlocked("youtube.com"))
        assertFalse(manager.isHostBlocked("bing.com"))

        // Actual ad servers must still be blocked
        assertTrue(manager.isHostBlocked("adservice.google.com"))
        assertTrue(manager.isHostBlocked("doubleclick.net"))
        assertTrue(manager.isHostBlocked("sub.doubleclick.net"))
    }

    @Test
    fun testParseFilterRulesRobustness() {
        val (manager, _) = createTestManager()
        val filterList = listOf(
            "! Easylist / AdGuard / Fanboy sample rules",
            "# Comment line",
            "[Auto-generated metadata]",
            "||google.com/_/+1/\$third-party",
            "||google.com^*/fastbutton?\$third-party",
            "||spotify.com/follow/\$third-party",
            "||amazon.com/cdp/getVideoAds",
            "||amazon.com/aan/",
            "||bing.com/rewardsapp/reportactivity?\$badfilter",
            "||youtube.com^\$domain=sarapbabe.com",
            "||reddit.com^\$removeparam=recap_redirect",
            "@@||amazon.com^\$generichide",
            "google.com##.ad-container",
            "google.com#@#SHARE-BUTTON",
            ".-facebook-share.",
            ".fbshare.js",
            "_share_bookmark.",
            "\$script,3p,denyallow=google.com",
            "0.0.0.0 doubleclick.net",
            "127.0.0.1 tracker.example.com # ad tracker",
            "||adservice.google.com^",
            "||evil-tracker.org^\$third-party",
            "||ads.network.com^\$image,script"
        ).joinToString("\n")

        val parsed = manager.parseFilterRules(filterList)

        // Must NOT extract legitimate platform root domains from path/scoped rules
        assertFalse(parsed.contains("google.com"))
        assertFalse(parsed.contains("spotify.com"))
        assertFalse(parsed.contains("amazon.com"))
        assertFalse(parsed.contains("bing.com"))
        assertFalse(parsed.contains("youtube.com"))
        assertFalse(parsed.contains("reddit.com"))

        // Must NOT extract path fragments or cosmetic selectors
        assertFalse(parsed.contains(".-facebook-share."))
        assertFalse(parsed.contains(".fbshare.js"))
        assertFalse(parsed.contains("_share_bookmark."))

        // Must extract valid ad and tracking domains
        assertTrue(parsed.contains("doubleclick.net"))
        assertTrue(parsed.contains("tracker.example.com"))
        assertTrue(parsed.contains("adservice.google.com"))
        assertTrue(parsed.contains("evil-tracker.org"))
        assertTrue(parsed.contains("ads.network.com"))
    }

    @Test
    fun testIsValidDomainCandidate() {
        assertTrue(AdBlockManager.isValidDomainCandidate("doubleclick.net"))
        assertTrue(AdBlockManager.isValidDomainCandidate("adservice.google.com"))
        assertTrue(AdBlockManager.isValidDomainCandidate("sub.domain.co.uk"))
        assertTrue(AdBlockManager.isValidDomainCandidate("194.63.143.96"))

        assertFalse(AdBlockManager.isValidDomainCandidate(".-facebook-share."))
        assertFalse(AdBlockManager.isValidDomainCandidate(".fbshare.js"))
        assertFalse(AdBlockManager.isValidDomainCandidate("_share_bookmark."))
        assertFalse(AdBlockManager.isValidDomainCandidate("google.com##.ad"))
        assertFalse(AdBlockManager.isValidDomainCandidate("0.0.0.0"))
        assertFalse(AdBlockManager.isValidDomainCandidate("127.0.0.1"))
        assertFalse(AdBlockManager.isValidDomainCandidate("test..com"))
        assertFalse(AdBlockManager.isValidDomainCandidate(""))
    }
}
