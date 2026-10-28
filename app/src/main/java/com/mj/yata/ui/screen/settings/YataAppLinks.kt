package com.mj.yata.ui.screen.settings

internal const val YATA_PLAY_STORE_URL = "https://play.google.com/store/apps/details?id=com.mj.yata"
internal const val YATA_GITHUB_RELEASES_URL = "https://github.com/rjwarrier/yata/releases"

internal fun buildAppShareText(
    message: String,
    includePlayStoreLink: Boolean,
    includeGitHubLink: Boolean
): String = listOfNotNull(
    message.trim().takeIf { it.isNotEmpty() },
    YATA_PLAY_STORE_URL.takeIf { includePlayStoreLink },
    YATA_GITHUB_RELEASES_URL.takeIf { includeGitHubLink }
).joinToString("\n\n")
