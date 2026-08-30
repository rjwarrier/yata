package com.mj.yata.ui.screen.lock

internal fun shouldLockOnAppLaunch(
    appLockEnabled: Boolean,
    biometricOrDeviceCredentialAvailable: Boolean,
    pinSet: Boolean
): Boolean =
    appLockEnabled && (biometricOrDeviceCredentialAvailable || pinSet)

internal fun shouldDisableLockToAvoidStrandingOwner(
    appLockEnabled: Boolean,
    biometricOrDeviceCredentialAvailable: Boolean,
    pinSet: Boolean
): Boolean =
    appLockEnabled && !biometricOrDeviceCredentialAvailable && !pinSet
