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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mj.yata.ui.screen.main.WelcomeSetupPreset
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
    @androidx.annotation.StringRes val description: Int
)

private data class WelcomeSetupOption(
    val preset: WelcomeSetupPreset,
    val icon: ImageVector,
    @androidx.annotation.StringRes val title: Int,
    @androidx.annotation.StringRes val description: Int,
    val changes: List<Int>
)

private val setupOptions = listOf(
    WelcomeSetupOption(
        preset = WelcomeSetupPreset.SIMPLE_LIST,
        icon = Icons.Default.TaskAlt,
        title = R.string.welcome_setup_simple_title,
        description = R.string.welcome_setup_simple_description,
        changes = listOf(
            R.string.welcome_setup_change_personal_list,
            R.string.welcome_setup_change_today_upcoming,
            R.string.welcome_setup_change_due_today
        )
    ),
    WelcomeSetupOption(
        preset = WelcomeSetupPreset.PERSONAL_PRODUCTIVITY,
        icon = Icons.Default.Person,
        title = R.string.welcome_setup_personal_title,
        description = R.string.welcome_setup_personal_description,
        changes = listOf(
            R.string.welcome_setup_change_today_someday,
            R.string.welcome_setup_change_goals_project,
            R.string.welcome_setup_change_estimate,
            R.string.welcome_setup_change_nudges
        )
    ),
    WelcomeSetupOption(
        preset = WelcomeSetupPreset.TEAM_PROJECTS,
        icon = Icons.Default.Groups,
        title = R.string.welcome_setup_team_title,
        description = R.string.welcome_setup_team_description,
        changes = listOf(
            R.string.welcome_setup_change_work_list,
            R.string.welcome_setup_change_launch_backlog,
            R.string.welcome_setup_change_people_enabled,
            R.string.welcome_setup_change_auto_assign
        )
    )
)

private val pages = listOf(
    WelcomePage(
        icon = Icons.Default.TaskAlt,
        title = R.string.welcome_title_intro,
        description = R.string.welcome_intro_description
    ),
    WelcomePage(
        icon = Icons.Default.Layers,
        title = R.string.welcome_title_organize,
        description = R.string.welcome_organize_description
    ),
    WelcomePage(
        icon = Icons.Default.Analytics,
        title = R.string.welcome_title_review,
        description = R.string.welcome_review_description
    )
)

/** Total pages shown: static tour pages plus profile setup, setup presets, and a final summary. */
private val totalPageCount = pages.size + 3

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
    var selectedPreset by remember { mutableStateOf(WelcomeSetupPreset.PERSONAL_PRODUCTIVITY) }
    var appliedPreset by remember { mutableStateOf<WelcomeSetupPreset?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                                text = stringResource(item.description),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    when (page - pages.size) {
                        0 -> WelcomeProfileSetupPage(viewModel = viewModel, accentColor = accents.accentA)
                        1 -> WelcomeSetupPresetPage(
                            selectedPreset = selectedPreset,
                            appliedPreset = appliedPreset,
                            onSelectPreset = { selectedPreset = it },
                            onApplyPreset = {
                                viewModel.applyWelcomeSetupPreset(selectedPreset)
                                appliedPreset = selectedPreset
                            }
                        )
                        else -> WelcomeSetupSummaryPage(
                            appliedPreset = appliedPreset,
                            selectedPreset = selectedPreset,
                            onApplyPreset = {
                                viewModel.applyWelcomeSetupPreset(selectedPreset)
                                appliedPreset = selectedPreset
                            }
                        )
                    }
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
                    .height(120.dp)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (pagerState.currentPage < totalPageCount - 1) {
                            TextButton(onClick = onFinish) {
                                Text(stringResource(R.string.welcome_skip))
                            }
                        }
                    }
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
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WelcomeSetupPresetPage(
    selectedPreset: WelcomeSetupPreset,
    appliedPreset: WelcomeSetupPreset?,
    onSelectPreset: (WelcomeSetupPreset) -> Unit,
    onApplyPreset: () -> Unit
) {
    val applied = appliedPreset == selectedPreset

    AdaptiveContentBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.welcome_setup_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.welcome_setup_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                setupOptions.forEach { option ->
                    WelcomePresetCard(
                        option = option,
                        selected = option.preset == selectedPreset,
                        applied = option.preset == appliedPreset,
                        onClick = { onSelectPreset(option.preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = onApplyPreset,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = if (applied) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(if (applied) R.string.welcome_setup_applied else R.string.welcome_setup_apply))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WelcomePresetCard(
    option: WelcomeSetupOption,
    selected: Boolean,
    applied: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = if (selected) 0.72f else 1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = option.icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(option.title),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    if (applied) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(option.description),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.82f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                if (selected) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        option.changes.forEach { change ->
                            Surface(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                },
                                contentColor = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = stringResource(change),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomeSetupSummaryPage(
    appliedPreset: WelcomeSetupPreset?,
    selectedPreset: WelcomeSetupPreset,
    onApplyPreset: () -> Unit
) {
    val option = setupOptions.first { it.preset == (appliedPreset ?: selectedPreset) }
    val hasApplied = appliedPreset != null
    val optionTitle = stringResource(option.title)

    AdaptiveContentBox {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasApplied) Icons.Default.CheckCircle else Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
            Text(
                text = stringResource(if (hasApplied) R.string.welcome_summary_ready_title else R.string.welcome_summary_apply_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (hasApplied) {
                    stringResource(R.string.welcome_summary_applied_description, optionTitle)
                } else {
                    stringResource(R.string.welcome_summary_unapplied_description, optionTitle)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (!hasApplied) {
                Spacer(modifier = Modifier.height(22.dp))
                Button(onClick = onApplyPreset, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.welcome_summary_apply_preset, optionTitle))
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
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
    val storedUserName by viewModel.userName.collectAsStateWithLifecycle()
    val storedUserEmail by viewModel.userEmail.collectAsStateWithLifecycle()
    val userPhotoUri by viewModel.userPhotoUri.collectAsStateWithLifecycle()

    // These fields must not be driven straight off the preference flows they write to. Doing that
    // sends every keystroke on a round trip — setUserName launches a coroutine that edits
    // DataStore, the flow re-emits, and only then does the field see the new text. In between,
    // recomposition hands the TextField the *previous* value, which snaps the cursor back a
    // character; typing at any speed then drops and reorders letters.
    //
    // So the draft is the source of truth once editing starts, and the stored value only seeds it
    // (covering a re-run of the tour with a profile already set). The same drafts feed the avatar
    // preview below, so the initials still update as the name is typed. This mirrors the profile
    // dialog in Settings, which seeds a draft on open and never binds the field to the flow.
    var nameDraft by rememberSaveable { mutableStateOf(storedUserName) }
    var emailDraft by rememberSaveable { mutableStateOf(storedUserEmail) }
    var profileEdited by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(storedUserName, storedUserEmail) {
        if (!profileEdited) {
            nameDraft = storedUserName
            emailDraft = storedUserEmail
        }
    }

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
                    initials = initialsFor(nameDraft),
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
                value = nameDraft,
                onValueChange = {
                    nameDraft = it
                    profileEdited = true
                    viewModel.setUserName(it)
                },
                singleLine = true,
                label = { Text(stringResource(R.string.settings_profile_name_label)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                shape = YataCompactFieldShape,
                colors = yataFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            TextField(
                value = emailDraft,
                onValueChange = {
                    emailDraft = it
                    profileEdited = true
                    viewModel.setUserEmail(it)
                },
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
