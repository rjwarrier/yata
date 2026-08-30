package com.mj.yata.ui.screen.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockPolicyTest {

    @Test
    fun launchLocksWhenPinIsSetEvenWithoutBiometrics() {
        assertTrue(
            shouldLockOnAppLaunch(
                appLockEnabled = true,
                biometricOrDeviceCredentialAvailable = false,
                pinSet = true
            )
        )
    }

    @Test
    fun launchDoesNotLockWhenNoUnlockMethodExists() {
        assertFalse(
            shouldLockOnAppLaunch(
                appLockEnabled = true,
                biometricOrDeviceCredentialAvailable = false,
                pinSet = false
            )
        )
    }

    @Test
    fun lockedScreenOnlyStandsDownWhenEnabledLockHasNoUnlockMethod() {
        assertTrue(
            shouldDisableLockToAvoidStrandingOwner(
                appLockEnabled = true,
                biometricOrDeviceCredentialAvailable = false,
                pinSet = false
            )
        )
        assertFalse(
            shouldDisableLockToAvoidStrandingOwner(
                appLockEnabled = true,
                biometricOrDeviceCredentialAvailable = false,
                pinSet = true
            )
        )
    }
}
