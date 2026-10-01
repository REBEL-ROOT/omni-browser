/*
 * Omni Browser - A premium, private, and secure web browser.
 * Copyright (C) 2026 RebelRoot Ltd
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.rebelroot.omni.webapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * High-quality curated catalog of premier Progressive Web Apps (PWAs).
 * Inspired by Nira Browser's PWA discovery mechanism with expanded modern entries,
 * categorized taxonomy, and rich metadata.
 */
enum class PwaCategory(val displayName: String, val icon: ImageVector) {
    ALL("All", Icons.Rounded.AllInclusive),
    AI("AI & LLMs", Icons.Rounded.AutoAwesome),
    PRODUCTIVITY("Productivity", Icons.Rounded.WorkOutline),
    DEVELOPER("Developer", Icons.Rounded.Code),
    DESIGN("Design & Art", Icons.Rounded.Palette),
    GAMES("Games & Fun", Icons.Rounded.SportsEsports),
    KNOWLEDGE("Knowledge", Icons.AutoMirrored.Rounded.MenuBook),
    MEDIA_SOCIAL("Media & Social", Icons.AutoMirrored.Rounded.Chat),
    UTILITIES("Utilities", Icons.Rounded.Handyman);

    companion object {
        fun fromString(value: String): PwaCategory {
            return entries.firstOrNull { it.displayName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
                ?: PRODUCTIVITY
        }
    }
}

data class PwaCatalogItem(
    val id: String,
    val name: String,
    val url: String,
    val category: PwaCategory,
    val description: String,
    val tag: String = "Popular",
    val keywords: List<String> = emptyList()
) {
    val displayDomain: String
        get() = try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host ?: url
            if (host.startsWith("www.")) host.removePrefix("www.") else host
        } catch (e: Exception) {
            url
        }

    val faviconUrl: String
        get() = "https://www.google.com/s2/favicons?sz=128&domain=$displayDomain"
}

object PwaCatalog {

    val curatedApps: List<PwaCatalogItem> = listOf(
        // === AI & LLMs ===
        PwaCatalogItem(
            id = "chatgpt",
            name = "ChatGPT",
            url = "https://chatgpt.com/",
            category = PwaCategory.AI,
            description = "OpenAI's advanced generative AI assistant for chat, reasoning & creativity.",
            tag = "Hot",
            keywords = listOf("openai", "gpt", "bot", "assistant", "ai")
        ),
        PwaCatalogItem(
            id = "gemini",
            name = "Google Gemini",
            url = "https://gemini.google.com/",
            category = PwaCategory.AI,
            description = "Google's direct multimodal AI assistant for research, analysis and ideation.",
            tag = "Featured",
            keywords = listOf("google", "gemini", "bard", "ai", "multimodal")
        ),
        PwaCatalogItem(
            id = "claude",
            name = "Claude AI",
            url = "https://claude.ai/",
            category = PwaCategory.AI,
            description = "Anthropic's thoughtful, nuanced AI assistant with superior writing & coding.",
            tag = "Pro Choice",
            keywords = listOf("anthropic", "claude", "ai", "coding")
        ),
        PwaCatalogItem(
            id = "perplexity",
            name = "Perplexity AI",
            url = "https://www.perplexity.ai/",
            category = PwaCategory.AI,
            description = "AI-powered conversational search engine with verified source citations.",
            tag = "Trending",
            keywords = listOf("search", "citations", "research", "ai")
        ),
        PwaCatalogItem(
            id = "huggingface",
            name = "Hugging Face",
            url = "https://huggingface.co/",
            category = PwaCategory.AI,
            description = "Open-source machine learning hub hosting models, datasets and Spaces.",
            tag = "Dev Hub",
            keywords = listOf("ml", "models", "open source", "huggingface")
        ),
        PwaCatalogItem(
            id = "poe",
            name = "Poe",
            url = "https://poe.com/",
            category = PwaCategory.AI,
            description = "Quora's multi-bot aggregator offering fast access to various AI models.",
            tag = "Aggregator",
            keywords = listOf("bots", "quora", "models", "chat")
        ),

        // === Productivity & Office ===
        PwaCatalogItem(
            id = "notion",
            name = "Notion",
            url = "https://www.notion.so/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Connected workspace for wiki, docs, task tracking and team knowledge.",
            tag = "Essential",
            keywords = listOf("notes", "wiki", "docs", "workspace", "tasks")
        ),
        PwaCatalogItem(
            id = "slack",
            name = "Slack",
            url = "https://app.slack.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Enterprise communication platform with channels, messaging and calls.",
            tag = "Popular",
            keywords = listOf("team", "chat", "channels", "work")
        ),
        PwaCatalogItem(
            id = "trello",
            name = "Trello",
            url = "https://trello.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Visual Kanban board platform for agile sprint and project management.",
            tag = "Kanban",
            keywords = listOf("kanban", "boards", "projects", "todo")
        ),
        PwaCatalogItem(
            id = "asana",
            name = "Asana",
            url = "https://asana.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Cross-functional work management tool for tracking initiatives and goals.",
            tag = "Work",
            keywords = listOf("work", "teams", "tasks", "management")
        ),
        PwaCatalogItem(
            id = "google_calendar",
            name = "Google Calendar",
            url = "https://calendar.google.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Schedule meetings, set reminders, and manage your daily agenda.",
            tag = "Essential",
            keywords = listOf("google", "events", "calendar", "schedule")
        ),
        PwaCatalogItem(
            id = "google_docs",
            name = "Google Docs",
            url = "https://docs.google.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Real-time collaborative word processing directly in the browser.",
            tag = "Office",
            keywords = listOf("docs", "word", "sheets", "office", "google")
        ),
        PwaCatalogItem(
            id = "outlook",
            name = "Outlook",
            url = "https://outlook.live.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Microsoft's webmail, calendar, and contacts suite with spam filtering.",
            tag = "Email",
            keywords = listOf("email", "microsoft", "mail", "calendar")
        ),
        PwaCatalogItem(
            id = "todoist",
            name = "Todoist",
            url = "https://todoist.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Clean task manager and to-do list with natural language input.",
            tag = "To-Do",
            keywords = listOf("tasks", "todo", "habits", "planner")
        ),
        PwaCatalogItem(
            id = "clickup",
            name = "ClickUp",
            url = "https://app.clickup.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "One app to replace them all: tasks, docs, chat, and goal tracking.",
            tag = "All-In-One",
            keywords = listOf("tasks", "clickup", "sprints", "management")
        ),
        PwaCatalogItem(
            id = "miro",
            name = "Miro",
            url = "https://miro.com/",
            category = PwaCategory.PRODUCTIVITY,
            description = "Visual workspace for team brainstorming, wireframing and mind maps.",
            tag = "Whiteboard",
            keywords = listOf("whiteboard", "canvas", "brainstorm", "sticky")
        ),

        // === Developer & Engineering ===
        PwaCatalogItem(
            id = "github",
            name = "GitHub",
            url = "https://github.com/",
            category = PwaCategory.DEVELOPER,
            description = "World's leading platform for code hosting, PR reviews, issues & CI/CD.",
            tag = "Essential",
            keywords = listOf("git", "code", "repos", "pull requests", "dev")
        ),
        PwaCatalogItem(
            id = "gitlab",
            name = "GitLab",
            url = "https://gitlab.com/",
            category = PwaCategory.DEVELOPER,
            description = "Single application for the entire DevSecOps software delivery lifecycle.",
            tag = "DevOps",
            keywords = listOf("git", "devops", "ci/cd", "repos")
        ),
        PwaCatalogItem(
            id = "stackoverflow",
            name = "Stack Overflow",
            url = "https://stackoverflow.com/",
            category = PwaCategory.DEVELOPER,
            description = "The world's largest online developer Q&A community.",
            tag = "Q&A",
            keywords = listOf("questions", "answers", "coding", "bugs")
        ),
        PwaCatalogItem(
            id = "dev_to",
            name = "DEV Community",
            url = "https://dev.to/",
            category = PwaCategory.DEVELOPER,
            description = "A constructive, inclusive social network for software developers.",
            tag = "Community",
            keywords = listOf("articles", "blog", "developers", "tutorials")
        ),
        PwaCatalogItem(
            id = "hackernews",
            name = "Hacker News",
            url = "https://news.ycombinator.com/",
            category = PwaCategory.DEVELOPER,
            description = "Y Combinator's curated tech news, startups, and engineering discourse.",
            tag = "News",
            keywords = listOf("yc", "tech", "startups", "hacker news")
        ),
        PwaCatalogItem(
            id = "codesandbox",
            name = "CodeSandbox",
            url = "https://codesandbox.io/",
            category = PwaCategory.DEVELOPER,
            description = "Instant cloud development environment for rapid web prototyping.",
            tag = "Cloud IDE",
            keywords = listOf("editor", "javascript", "react", "sandbox")
        ),
        PwaCatalogItem(
            id = "replit",
            name = "Replit",
            url = "https://replit.com/",
            category = PwaCategory.DEVELOPER,
            description = "Build, deploy, and collaborate on code in 50+ languages in your browser.",
            tag = "Cloud IDE",
            keywords = listOf("python", "nodejs", "hosting", "cloud")
        ),
        PwaCatalogItem(
            id = "mdn",
            name = "MDN Web Docs",
            url = "https://developer.mozilla.org/",
            category = PwaCategory.DEVELOPER,
            description = "Mozilla's authoritative documentation on HTML, CSS, JavaScript, and Web APIs.",
            tag = "Reference",
            keywords = listOf("mozilla", "html", "css", "javascript", "docs")
        ),

        // === Design & Art ===
        PwaCatalogItem(
            id = "photopea",
            name = "Photopea",
            url = "https://www.photopea.com/",
            category = PwaCategory.DESIGN,
            description = "Powerful online photo editor supporting PSD, XCF, Sketch, and RAW formats.",
            tag = "Top Pick",
            keywords = listOf("photoshop", "psd", "graphics", "editor", "images")
        ),
        PwaCatalogItem(
            id = "canva",
            name = "Canva",
            url = "https://www.canva.com/",
            category = PwaCategory.DESIGN,
            description = "Intuitive graphic design suite for social posts, presentations and banners.",
            tag = "Popular",
            keywords = listOf("templates", "posters", "social", "design")
        ),
        PwaCatalogItem(
            id = "excalidraw",
            name = "Excalidraw",
            url = "https://excalidraw.com/",
            category = PwaCategory.DESIGN,
            description = "Virtual hand-drawn style whiteboard for architecture diagrams and sketches.",
            tag = "Open Source",
            keywords = listOf("whiteboard", "diagrams", "sketch", "hand-drawn")
        ),
        PwaCatalogItem(
            id = "draw_io",
            name = "Draw.io",
            url = "https://app.diagrams.net/",
            category = PwaCategory.DESIGN,
            description = "Professional diagramming and flowchart tool with offline browser support.",
            tag = "Diagrams",
            keywords = listOf("flowchart", "uml", "network", "diagram")
        ),
        PwaCatalogItem(
            id = "squoosh",
            name = "Squoosh",
            url = "https://squoosh.app/",
            category = PwaCategory.DESIGN,
            description = "Google's offline image compression and modern WebP/AVIF format converter.",
            tag = "Utility",
            keywords = listOf("compression", "images", "webp", "avif")
        ),
        PwaCatalogItem(
            id = "figma",
            name = "Figma",
            url = "https://www.figma.com/",
            category = PwaCategory.DESIGN,
            description = "Industry standard collaborative vector interface and UI design platform.",
            tag = "Pro UI",
            keywords = listOf("ui", "ux", "vector", "prototype")
        ),

        // === Games & Fun ===
        PwaCatalogItem(
            id = "lichess",
            name = "Lichess",
            url = "https://lichess.org/",
            category = PwaCategory.GAMES,
            description = "100% free, ad-free, open-source chess server with analysis and puzzles.",
            tag = "Free Chess",
            keywords = listOf("chess", "puzzles", "open source", "board")
        ),
        PwaCatalogItem(
            id = "chess_com",
            name = "Chess.com",
            url = "https://www.chess.com/",
            category = PwaCategory.GAMES,
            description = "The premier global online chess community with lessons and bots.",
            tag = "Popular",
            keywords = listOf("chess", "multiplayer", "lessons")
        ),
        PwaCatalogItem(
            id = "geoguessr",
            name = "GeoGuessr",
            url = "https://www.geoguessr.com/",
            category = PwaCategory.GAMES,
            description = "Geography exploration game where you deduce locations using Google Street View.",
            tag = "Geography",
            keywords = listOf("maps", "geography", "quiz", "game")
        ),
        PwaCatalogItem(
            id = "monkeytype",
            name = "Monkeytype",
            url = "https://monkeytype.com/",
            category = PwaCategory.GAMES,
            description = "Minimalist, ultra-customizable typing test tracking WPM and accuracy.",
            tag = "Typing",
            keywords = listOf("speed", "typing", "wpm", "keyboard")
        ),
        PwaCatalogItem(
            id = "typeracer",
            name = "TypeRacer",
            url = "https://play.typeracer.com/",
            category = PwaCategory.GAMES,
            description = "Competitive multiplayer typing race against players across the world.",
            tag = "Multiplayer",
            keywords = listOf("race", "typing", "multiplayer")
        ),
        PwaCatalogItem(
            id = "wordle",
            name = "Wordle",
            url = "https://www.nytimes.com/games/wordle/index.html",
            category = PwaCategory.GAMES,
            description = "The iconic daily 5-letter word puzzle game.",
            tag = "Daily Puzzle",
            keywords = listOf("word", "puzzle", "daily", "guess")
        ),
        PwaCatalogItem(
            id = "game_2048",
            name = "2048",
            url = "https://play2048.co/",
            category = PwaCategory.GAMES,
            description = "Addictive sliding tile number puzzle to merge tiles up to 2048.",
            tag = "Classic",
            keywords = listOf("numbers", "puzzle", "sliding")
        ),

        // === Knowledge & Learning ===
        PwaCatalogItem(
            id = "wikipedia",
            name = "Wikipedia",
            url = "https://www.wikipedia.org/",
            category = PwaCategory.KNOWLEDGE,
            description = "The free online encyclopedia created and maintained by volunteers globally.",
            tag = "Encyclopedia",
            keywords = listOf("articles", "encyclopedia", "facts", "wiki")
        ),
        PwaCatalogItem(
            id = "medium",
            name = "Medium",
            url = "https://medium.com/",
            category = PwaCategory.KNOWLEDGE,
            description = "Open platform where readers find insightful thinking on any topic.",
            tag = "Reading",
            keywords = listOf("articles", "stories", "writing", "blog")
        ),
        PwaCatalogItem(
            id = "wolframalpha",
            name = "WolframAlpha",
            url = "https://www.wolframalpha.com/",
            category = PwaCategory.KNOWLEDGE,
            description = "Computational knowledge engine computing answers across mathematics and sciences.",
            tag = "Math & Science",
            keywords = listOf("math", "science", "calculator", "equations")
        ),
        PwaCatalogItem(
            id = "duolingo",
            name = "Duolingo",
            url = "https://www.duolingo.com/",
            category = PwaCategory.KNOWLEDGE,
            description = "Gamified, bite-sized language learning for over 40 languages.",
            tag = "Languages",
            keywords = listOf("learn", "languages", "spanish", "french")
        ),
        PwaCatalogItem(
            id = "reuters",
            name = "Reuters",
            url = "https://www.reuters.com/",
            category = PwaCategory.KNOWLEDGE,
            description = "Unbiased breaking international news, financial data and analysis.",
            tag = "Global News",
            keywords = listOf("news", "finance", "world", "reuters")
        ),

        // === Media & Social ===
        PwaCatalogItem(
            id = "x_twitter",
            name = "X / Twitter",
            url = "https://x.com/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Real-time global town square for breaking events and live discussion.",
            tag = "Social",
            keywords = listOf("twitter", "x", "tweets", "social")
        ),
        PwaCatalogItem(
            id = "reddit",
            name = "Reddit",
            url = "https://www.reddit.com/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Dive into anything with millions of community discussions and subreddits.",
            tag = "Community",
            keywords = listOf("reddit", "subreddits", "discussion", "forum")
        ),
        PwaCatalogItem(
            id = "bluesky",
            name = "Bluesky",
            url = "https://bsky.app/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Decentralized open social network built on the AT Protocol.",
            tag = "Decentralized",
            keywords = listOf("atproto", "social", "bluesky", "threads")
        ),
        PwaCatalogItem(
            id = "threads",
            name = "Threads",
            url = "https://www.threads.net/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Meta's text-based conversational app connected to the fediverse.",
            tag = "Meta",
            keywords = listOf("instagram", "meta", "threads", "text")
        ),
        PwaCatalogItem(
            id = "mastodon",
            name = "Mastodon",
            url = "https://mastodon.social/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Open-source, decentralized social network powered by ActivityPub.",
            tag = "Fediverse",
            keywords = listOf("fediverse", "activitypub", "open source")
        ),
        PwaCatalogItem(
            id = "spotify_web",
            name = "Spotify Web",
            url = "https://open.spotify.com/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Stream millions of songs and podcasts directly in your web browser.",
            tag = "Audio",
            keywords = listOf("music", "songs", "podcasts", "audio")
        ),
        PwaCatalogItem(
            id = "soundcloud",
            name = "SoundCloud",
            url = "https://soundcloud.com/",
            category = PwaCategory.MEDIA_SOCIAL,
            description = "Discover trending tracks, independent artists, and DJ mixes.",
            tag = "Indie Audio",
            keywords = listOf("music", "artists", "remix", "streaming")
        ),

        // === Utilities & Security ===
        PwaCatalogItem(
            id = "speedtest",
            name = "Speedtest by Ookla",
            url = "https://www.speedtest.net/",
            category = PwaCategory.UTILITIES,
            description = "Test internet speed, download, upload bandwidth, latency and ping.",
            tag = "Network",
            keywords = listOf("speed", "bandwidth", "ping", "test")
        ),
        PwaCatalogItem(
            id = "virustotal",
            name = "VirusTotal",
            url = "https://www.virustotal.com/",
            category = PwaCategory.UTILITIES,
            description = "Scan files, domains, IPs and URLs for malware using 70+ antivirus engines.",
            tag = "Security",
            keywords = listOf("antivirus", "malware", "scan", "security")
        ),
        PwaCatalogItem(
            id = "cyberchef",
            name = "CyberChef",
            url = "https://gchq.github.io/CyberChef/",
            category = PwaCategory.UTILITIES,
            description = "The Swiss Army Knife for encryption, encoding, compression and hashing.",
            tag = "Cyber Tool",
            keywords = listOf("crypto", "hex", "base64", "hashing", "security")
        ),
        PwaCatalogItem(
            id = "temp_mail",
            name = "10 Minute Mail",
            url = "https://10minutemail.com/",
            category = PwaCategory.UTILITIES,
            description = "Temporary disposable email service to protect your personal inbox from spam.",
            tag = "Privacy",
            keywords = listOf("disposable", "temp", "email", "spam")
        )
    )

    /**
     * Match a given web URL to a suggested PWA item
     */
    fun findByUrl(url: String): PwaCatalogItem? {
        val cleanInput = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        return curatedApps.firstOrNull { app ->
            val cleanAppUrl = app.url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
            cleanInput.startsWith(cleanAppUrl) || cleanAppUrl.startsWith(cleanInput)
        }
    }
}
