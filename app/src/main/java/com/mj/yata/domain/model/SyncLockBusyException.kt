package com.mj.yata.domain.model

data class SyncLockInfo(
    val lockedAt: Long?,
    val ageText: String,
    val ownerDevice: String?
)

class SyncLockBusyException(
    val lockInfo: SyncLockInfo,
    cause: Throwable? = null
) : IllegalStateException(messageFor(lockInfo), cause) {
    companion object {
        private fun messageFor(info: SyncLockInfo): String {
            val owner = info.ownerDevice?.takeIf { it.isNotBlank() }?.let { " Locked by $it." }.orEmpty()
            return "Another device is syncing; sync lock age is ${info.ageText}.$owner If no other device is syncing, clear the stale sync lock in Settings."
        }
    }
}
