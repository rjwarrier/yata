package com.mj.yata.ui.widgets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mj.yata.R
import com.mj.yata.domain.model.Person
import com.mj.yata.ui.theme.LocalYataAccents

// Sized by decoded bytes, not entry count. Bitmaps here are sampled to maxDimension = 200, up to
// ~160KB each, so a flat 30-entry cap could hold anywhere from under 1MB to ~5MB depending on how
// many decoded smaller than that ceiling. It also compounds with profile-photo changes: the app's
// own avatar gets a cache-busting `?t=` query param on every save (see ProfilePhotoUtils), which
// mints a new cache key each time rather than replacing the old one, so old entries only left via
// count-based eviction rather than being superseded. Budgeting by KB keeps memory bounded either
// way. onTrimMemory below hands back everything under real memory pressure.
private val avatarCache = object : android.util.LruCache<String, android.graphics.Bitmap>(6 * 1024) {
    override fun sizeOf(key: String, value: android.graphics.Bitmap): Int = value.byteCount / 1024
}

/** Called from [com.mj.yata.YataApplication]'s `onTrimMemory` — evicts the whole avatar cache
 * under real memory pressure rather than waiting for LRU turnover to get there naturally. */
fun trimAvatarCache() {
    avatarCache.evictAll()
}

@Composable
fun PersonAvatar(
    initials: String,
    accentKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    drawRing: Boolean = false,
    ringColor: Color = MaterialTheme.colorScheme.surface,
    photoUri: String? = null
) {
    val accents = LocalYataAccents.current
    val bgColor = accents.getAccent(accentKey)
    // Per accent rather than one ink for the whole palette — white initials on the yellow and
    // lime accents were all but invisible.
    val textColor = accents.onAccentFor(bgColor)
    val isMaterialGlyph = com.mj.yata.util.ProfilePhotoUtils.isMaterialGlyphUri(photoUri)
    // Transparent glyphs keep their source pixels on disk. Resolving their colors here makes a
    // wallpaper-driven dynamic color change recompose the avatar instead of leaving stale colors
    // baked into the saved PNG.
    val avatarBackgroundColor = if (isMaterialGlyph) MaterialTheme.colorScheme.primaryContainer else bgColor
    val glyphColor = MaterialTheme.colorScheme.onPrimaryContainer

    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, photoUri) {
        if (photoUri == null) {
            value = null
        } else {
            val cached = avatarCache.get(photoUri)
            if (cached != null) {
                value = cached
            } else {
                value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        com.mj.yata.util.ProfilePhotoUtils.decodeSampledBitmap(
                            context,
                            android.net.Uri.parse(photoUri),
                            maxDimension = 200
                        )?.also {
                            avatarCache.put(photoUri, it)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (drawRing) Modifier.border(2.dp, ringColor, CircleShape) else Modifier
            )
            .clip(CircleShape)
            .background(avatarBackgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        val loadedBitmap = bitmap
        if (loadedBitmap != null) {
            val imageBitmap = remember(loadedBitmap) { loadedBitmap.asImageBitmap() }
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (isMaterialGlyph) ColorFilter.tint(glyphColor) else null,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initials.uppercase(),
                color = textColor,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    // Proportional rather than tiered. The tiers had cliffs — a 56dp avatar took
                    // the same 18sp as a 40dp one and then 64dp jumped to 26sp — so the same
                    // initials looked a different weight depending on which screen you were on.
                    // 46% of the diameter leaves room for two bold letters (about 1.3em wide
                    // together) inside the circle. The floor keeps the 24dp avatars in the task
                    // rows legible, which is the smallest this is used at and the most common.
                    fontSize = maxOf(12f, size.value * 0.46f).sp
                )
            )
        }
    }
}

@Composable
fun AssigneeStack(
    people: List<Person>,
    modifier: Modifier = Modifier,
    avatarSize: Dp = 24.dp,
    maxAvatars: Int = 3
) {
    val displayedPeople = people.take(maxAvatars)
    val remaining = people.size - maxAvatars
    // Index 0 is the owner by convention (assigneeIds is built by append, so whoever was
    // assigned first sits at index 0) — rendered a touch larger so the stack reads "one owner,
    // N collaborators" instead of an undifferentiated pile of equal avatars.
    val ownerSize = if (people.size > 1) avatarSize * 1.15f else avatarSize

    Box(modifier = modifier) {
        displayedPeople.forEachIndexed { index, person ->
            val thisSize = if (index == 0) ownerSize else avatarSize
            PersonAvatar(
                initials = person.initials,
                accentKey = person.color,
                size = thisSize,
                drawRing = index > 0,
                ringColor = MaterialTheme.colorScheme.surface,
                photoUri = person.photoUri,
                modifier = Modifier.padding(start = (index * (avatarSize.value * 0.68f)).dp)
            )
        }
        if (remaining > 0) {
            val totalAvatars = displayedPeople.size
            Box(
                modifier = Modifier
                    .padding(start = (totalAvatars * (avatarSize.value * 0.68f)).dp)
                    .size(avatarSize)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.export_hidden_count, remaining),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (avatarSize >= 30.dp) 11.sp else 8.sp
                    )
                )
            }
        }
    }
}
