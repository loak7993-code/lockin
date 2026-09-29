package com.lockin.focus.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.util.LruCache

data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
    val group: AppGroup,
)

enum class AppGroup(val label: String) {
    SOCIAL("Social"),
    VIDEO("Video"),
    GAMES("Games"),
    FEED("Feeds & browsers"),
    SHOPPING("Shopping"),
    OTHER("Other"),
}

/**
 * Well-known time sinks, keyed by package name. The labels are only used when
 * the app is actually installed, so this stays correct without shipping a
 * database of every app on the Play Store.
 */
object Distractors {
    val KNOWN: List<Pair<String, String>> = listOf(
        "com.zhiliaoapp.musically" to "TikTok",
        "com.zhiliaoapp.musically.go" to "TikTok Lite",
        "com.ss.android.ugc.aweme" to "TikTok (global)",
        "com.ss.android.ugc.trill" to "TikTok Lite (global)",
        "com.instagram.android" to "Instagram",
        "com.instagram.barcelona" to "Threads",
        "com.google.android.youtube" to "YouTube",
        "com.twitter.android" to "X",
        "com.facebook.katana" to "Facebook",
        "com.facebook.orca" to "Messenger",
        "com.reddit.frontpage" to "Reddit",
        "com.snapchat.android" to "Snapchat",
        "com.discord" to "Discord",
        "com.pinterest" to "Pinterest",
        "com.tumblr" to "Tumblr",
        "tv.twitch.android.app" to "Twitch",
        "tv.kick.mobile" to "Kick",
        "com.ninegag.android" to "9GAG",
        "co.masters.android" to "Clubhouse",
        "com.whatsapp" to "WhatsApp",
        "org.telegram.messenger" to "Telegram",
        "org.thoughtcrime.securesms" to "Signal",
        "com.spotify.music" to "Spotify",
        "com.netflix.mediaclient" to "Netflix",
        "com.amazon.mShop.android.shopping" to "Amazon",
        "com.ebay.mobile" to "eBay",
        "com.aliexpress.android" to "AliExpress",
        "com.shopee.id" to "Shopee",
        "com.roblox.client" to "Roblox",
        "com.supercell.clashofclans" to "Clash of Clans",
        "com.kiloo.subwaysurf" to "Subway Surfers",
        "com.innersloth.spaceroamers" to "Among Us",
        "com.mojang.minecraftpe" to "Minecraft",
        "com.king.candycrushsaga" to "Candy Crush",
        "com.ea.gp.fifaa" to "EA SPORTS FC",
        "com.android.chrome" to "Chrome",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "org.mozilla.firefox" to "Firefox",
        "com.opera.browser" to "Opera",
        "com.brave.browser" to "Brave",
        "com.microsoft.emm.deeplink" to "Edge",
    )

    fun groupFor(pkg: String): AppGroup = when (pkg) {
        "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.trill", "com.instagram.android", "com.instagram.barcelona",
        "com.twitter.android", "com.facebook.katana", "com.reddit.frontpage",
        "com.snapchat.android", "com.pinterest", "com.tumblr", "co.masters.android",
        "com.ninegag.android",
        -> AppGroup.SOCIAL

        "com.google.android.youtube", "tv.twitch.android.app", "tv.kick.mobile",
        "com.netflix.mediaclient", "com.spotify.music",
        -> AppGroup.VIDEO

        "com.roblox.client", "com.supercell.clashofclans", "com.kiloo.subwaysurf",
        "com.innersloth.spaceroamers", "com.mojang.minecraftpe", "com.king.candycrushsaga",
        "com.ea.gp.fifaa",
        -> AppGroup.GAMES

        "com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox",
        "com.opera.browser", "com.brave.browser", "com.microsoft.emm.deeplink",
        -> AppGroup.FEED

        "com.amazon.mShop.android.shopping", "com.ebay.mobile", "com.aliexpress.android",
        "com.shopee.id",
        -> AppGroup.SHOPPING

        else -> AppGroup.OTHER
    }
}

object AppCatalog {

    private const val ICON_SIZE_PX = 144
    private val iconCache = LruCache<String, ImageBitmap>(160)

    /**
     * Every launchable app the user can see. Backed by the launcher query in the
     * manifest, so no QUERY_ALL_PACKAGES is involved.
     */
    fun loadInstalled(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved: List<ResolveInfo> = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
        } catch (e: RuntimeException) {
            Log.w("LockIn", "Could not list installed apps", e)
            return emptyList()
        }

        val friendlyNames = Distractors.KNOWN.toMap()
        val seen = HashSet<String>(resolved.size)
        val apps = ArrayList<InstalledApp>(resolved.size)
        for (info in resolved) {
            val pkg = info.activityInfo?.packageName ?: continue
            if (!seen.add(pkg)) continue
            val applicationInfo = info.activityInfo.applicationInfo
            val label = friendlyNames[pkg] ?: runCatching { pm.getApplicationLabel(applicationInfo).toString() }
                .getOrDefault(pkg)
            apps += InstalledApp(
                packageName = pkg,
                label = label,
                system = applicationInfo.isSystemApp(),
                group = Distractors.groupFor(pkg),
            )
        }
        return apps.sortedWith(
            compareBy<InstalledApp>({ it.system }, { it.group.ordinal }, { it.label.lowercase() }),
        )
    }

    fun labelOf(context: Context, pkg: String): String {
        val pm = context.packageManager
        return runCatching {
            val info = pm.getApplicationInfo(pkg, 0)
            Distractors.KNOWN.firstOrNull { it.first == pkg }?.second
                ?: pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }

    /** Cached so scrolling a 150-row list does not decode 150 icons a second time. */
    fun iconOf(context: Context, pkg: String): ImageBitmap? {
        iconCache.get(pkg)?.let { return it }
        val pm = context.packageManager
        val drawable = runCatching { pm.getApplicationIcon(pkg) }.getOrNull() ?: return null
        val bitmap = runCatching {
            drawable.toBitmap(ICON_SIZE_PX, ICON_SIZE_PX, android.graphics.Bitmap.Config.ARGB_8888)
                .asImageBitmap()
        }.getOrNull() ?: return null
        iconCache.put(pkg, bitmap)
        return bitmap
    }

    private fun ApplicationInfo.isSystemApp(): Boolean =
        (flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
}
