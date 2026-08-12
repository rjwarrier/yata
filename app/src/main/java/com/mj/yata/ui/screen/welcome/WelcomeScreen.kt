package com.mj.yata.ui.screen.welcome

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.yata.R
import com.mj.yata.ui.screen.main.MainViewModel
import com.mj.yata.ui.theme.LocalYataAccents
import com.mj.yata.ui.util.AdaptiveContentBox
import com.mj.yata.ui.widgets.CircularImageCropper
import com.mj.yata.ui.widgets.PersonAvatar
import com.mj.yata.ui.widgets.PresetAvatarChoice
import com.mj.yata.ui.widgets.YataCompactFieldShape
import com.mj.yata.ui.widgets.yataFieldColors
import com.mj.yata.util.ProfilePhotoUtils
import com.mj.yata.util.initialsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WelcomePage(
    val icon: ImageVector,
    @androidx.annotation.StringRes val title: Int,
    val description: String
)

private val pages = listOf(
    WelcomePage(
        icon = Icons.Default.TaskAlt,
        title = R.string.welcome_title_intro,
        description = "Yet Another Task App — organize your day, delegate to your team, and see everything at a glance. Natural-language quick add (\"call Priya tomorrow 3pm high priority\"), home-screen widgets, a Quick Settings tile, and self-hosted sync all come built in. A quick tour of the basics — replay it anytime from Settings → About."
    ),
    WelcomePage(
        icon = Icons.Default.Layers,
        title = R.string.welcome_title_projects_lists,
        description = "Projects group related tasks and track their combined progress — good for something like a client engagement with a deadline. Lists are simpler flat groupings for anything that doesn't need project-level tracking. Star either one for quick access from the drawer, give it an accent color, and mark it \"Exclude from Today\" if it's backlog you don't want cluttering your daily view."
    ),
    WelcomePage(
        icon = Icons.Default.People,
        title = R.string.welcome_title_people_delegation,
        description = "Assign tasks to yourself or teammates, and reassign as work shifts. The People tab shows who's carrying how much, including a 7-day overdue trend per person — so you can see who's falling behind before it becomes a problem. Add the Team Overdue widget to your home screen to keep an eye on it without opening the app."
    ),
    WelcomePage(
        icon = Icons.Default.Label,
        title = R.string.welcome_title_tags,
        description = "Flexible labels that cut across projects and lists — group by category, client type, or anything else that doesn't map to a single project. A task on a tagged project or list picks up that tag automatically, so you don't have to tag everything by hand. Star your most-used tags to pin them in the drawer."
    ),
    WelcomePage(
        icon = Icons.Default.CalendarMonth,
        title = R.string.welcome_title_today_upcoming,
        description = "Today shows what's due now or overdue, with a progress ring based on what was actually pending when the day started — clearing old backlog doesn't inflate it. Upcoming gives you a week strip or full month view, and Next 10 Days lays out everything ahead in one scrollable, date-sorted list. Delete anything with a swipe — you get an Undo snackbar before it's gone for good."
    ),
    WelcomePage(
        icon = Icons.Default.Analytics,
        title = R.string.welcome_title_analytics,
        description = "Completion streaks, overdue aging buckets, on-time delivery rate, and per-project/person/tag breakdowns — the numbers behind how you and your team are actually doing, not just what's on today's list. Switch between week and month views to spot trends early."
    )
)

/** Total pages shown: the static tour [pages] plus one trailing interactive "profile setup" page. */
private val totalPageCount = pages.size + 1

/** Shown automatically on first launch after install, and replayable any time from
 * Settings → About → "Show welcome tour". [onFinish] marks it seen (a no-op if already
 * seen, e.g. when replayed) and returns to whatever screen launched it.
 *
 * The final page asks for a name, an optional display-only email, and a profile picture
 * (system photo or an inbuilt icon) — the same fields Settings → Profile edits, written
 * live through [viewModel] as they're typed/picked rather than needing a separate save step,
 * since Skip/back-out should keep whatever was already entered.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(viewModel: MainViewModel, onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { totalPageCount })
    val scope = rememberCoroutineScope()
    val accents = LocalYataAccents.current

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onFinish) {
                    Text(stringResource(R.string.welcome_skip))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page < pages.size) {
                    val item = pages[page]
                    AdaptiveContentBox {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(CircleShape)
                                    .background(accents.accentA.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = accents.accentA,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(28.dp))
                            Text(
                                text = stringResource(item.title),
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = item.description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    WelcomeProfileSetupPage(viewModel = viewModel, accentColor = accents.accentA)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                (0 until totalPageCount).forEach { index ->
                    val isSelected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 8.dp,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "dotWidth"
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) accents.accentA else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = tween(durationMillis = 200),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = dotWidth, height = 8.dp)
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }

            AdaptiveContentBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                Button(
                    onClick = {
                        if (pagerState.currentPage < totalPageCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onFinish()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                        .height(52.dp)
                ) {
                    Text(
                        if (pagerState.currentPage < totalPageCount - 1) {
                            stringResource(R.string.welcome_next)
                        } else {
                            stringResource(R.string.welcome_get_started)
                        }
                    )
                }
            }
        }
    }
}

/** Name / email / avatar step — mirrors Settings → Profile's fields exactly (same DataStore
 * keys via [viewModel]) so whatever's entered here shows up there too, and vice versa if the
 * tour is replayed later. Email is explicitly labeled display-only: YATA has no accounts or
 * server-side identity, so there's nothing it could be used to sign into. */
@Composable
private fun WelcomeProfileSetupPage(viewModel: MainViewModel, accentColor: androidx.compose.ui.graphics.Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userPhotoUri by viewModel.userPhotoUri.collectAsStateWithLifecycle()

    var pickedPhotoBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                pickedPhotoBitmap = withContext(Dispatchers.IO) {
                    try {
                        ProfilePhotoUtils.decodeSampledBitmap(context, uri)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }
    fun launchPhotoPicker() {
        photoPickerLauncher.launch(
            androidx.activity.result.PickVisualMediaRequest(
                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
            )
        )
    }

    AdaptiveContentBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.welcome_title_profile),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.welcome_profile_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.clickable { launchPhotoPicker() }) {
                PersonAvatar(
                    initials = initialsFor(userName),
                    accentKey = "accentC",
                    size = 88.dp,
                    photoUri = userPhotoUri
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = stringResource(R.string.settings_change_profile_photo),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            TextField(
                value = userName,
                onValueChange = { viewModel.setUserName(it) },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_profile_name_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = YataCompactFieldShape,
                colors = yataFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = userEmail,
                onValueChange = { viewModel.setUserEmail(it) },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_profile_email_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done
                ),
                shape = YataCompactFieldShape,
                colors = yataFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.welcome_profile_email_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.welcome_profile_avatar_or_icon),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(ProfilePhotoUtils.PROFILE_AVATAR_PRESETS, key = { it.name }) { preset ->
                    PresetAvatarChoice(
                        preset = preset,
                        label = preset.label,
                        context = context,
                        onClick = {
                            pickedPhotoBitmap = ProfilePhotoUtils.presetAvatarBitmap(context, preset)
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    pickedPhotoBitmap?.let { bitmap ->
        CircularImageCropper(
            source = bitmap,
            onConfirm = { cropped ->
                scope.launch {
                    val savedUri = withContext(Dispatchers.IO) {
                        val isMaterialGlyph = ProfilePhotoUtils.looksLikeTransparentGlyph(cropped)
                        ProfilePhotoUtils.saveCircularProfilePhoto(
                            context,
                            cropped,
                            isMaterialGlyph = isMaterialGlyph
                        )
                    }
                    viewModel.setUserPhotoUri(savedUri.toString())
                    pickedPhotoBitmap = null
                }
            },
            onCancel = { pickedPhotoBitmap = null },
            onSelectNewImage = {
                pickedPhotoBitmap = null
                launchPhotoPicker()
            }
        )
    }
}
