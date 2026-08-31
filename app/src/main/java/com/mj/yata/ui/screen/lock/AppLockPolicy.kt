package com.mj.yata.ui.screen.lock

internal fun shouldLockOnAppLaunch(
    appLockEnabled: Boolean,
    biometricOrDeviceCredentialAvailable: Boolean,
    pinSet: Boolean
): Boolean =
    appLockEnabled && hasAppLockUnlockPath(biometricOrDeviceCredentialAvailable, pinSet)

internal fun shouldDisableLockToAvoidStrandingOwner(
    appLockEnabled: Boolean,
    biometricOrDeviceCredentialAvailable: Boolean,
    pinSet: Boolean
): Boolean =
    appLockEnabled && !hasAppLockUnlockPath(biometricOrDeviceCredentialAvailable, pinSet)

internal fun hasAppLockUnlockPath(
    biometricOrDeviceCredentialAvailable: Boolean,
    pinSet: Boolean
): Boolean =
    biometricOrDeviceCredentialAvailable || pinSet
