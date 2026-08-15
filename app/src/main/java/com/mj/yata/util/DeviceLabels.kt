package com.mj.yata.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.mj.yata.domain.sync.SyncDeviceLabel

/**
 * The label this device stamps onto the sync snapshots it publishes, and matches against when
 * deciding whether a commit in the activity feed came from here. Shared by the writer
 * (GitHubSyncManager) and the reader (the sync activity feed) so the two can't disagree about
 * what this device is called.
 */
fun Context.syncDeviceLabel(): String =
    SyncDeviceLabel.of(userSetDeviceName(), Build.MANUFACTURER, Build.MODEL)

/**
 * What [syncDeviceLabel] would return if the user had never named the device. Still needed
 * alongside it: snapshots published before a rename carry this form, and the feed matches both so
 * renaming a device doesn't orphan the history it already pushed.
 */
fun modelSyncDeviceLabel(): String = SyncDeviceLabel.fromModel(Build.MANUFACTURER, Build.MODEL)

/**
 * Android's user-set device name (Settings > About phone > Device name). Readable without any
 * permission since API 25, and this app's minSdk is 26 — but wrapped anyway because it reaches a
 * ContentProvider, and an OEM ROM or restricted profile denying that must not be able to take down
 * a backup: the label is cosmetic, the snapshot it accompanies is not.
 */
private fun Context.userSetDeviceName(): String? = runCatching {
    Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
}.getOrNull()
