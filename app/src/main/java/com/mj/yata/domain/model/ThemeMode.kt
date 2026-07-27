package com.mj.yata.domain.model

/**
 * [AMOLED] is a dark variant rather than a third light/dark state: it forces dark and
 * additionally flattens backgrounds to true black (see YataTheme's toAmoled). It replaced a
 * SCHEDULED mode that switched light/dark on a clock — any value persisted from that no longer
 * resolves and falls back to [SYSTEM].
 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK, AMOLED
}
