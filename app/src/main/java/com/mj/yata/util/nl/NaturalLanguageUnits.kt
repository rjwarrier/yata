package com.mj.yata.util.nl

// Shared duration-unit regex fragments. Single source of truth for both the main parser
// (recurrence/relative-date rules) and TimeRules (writtenHourRegex's negative lookahead) — these
// used to be declared twice and had already drifted once (see relativeUnitKind's dedicated word
// list for another spot that still needs the accented forms kept in sync by hand).
internal const val DAY_UNIT = "(?:days?|dys?|dy|d|día|dia|jour|tag|tage|giorno|giorni|dag|dagen|dagar|dzien|dni|zi|zie|gun|hari|siku|araw|ngay)s?"
internal const val WEEK_UNIT = "(?:weeks?|wks?|wk|w|semana|semaine|woche|wochen|settimana|settimane|week|weken|vecka|veckor|tydzien|tygodnie|saptamana|hafta|minggu|wiki|linggo|tuan)s?"
internal const val MONTH_UNIT = "(?:months?|mos?|mths?|mth|mes(?:es)?|mês|mêses|mois|monat|monate|mese|mesi|maand|maanden|manad|manader|miesiac|miesiace|luna|ay|bulan|mwezi|buwan|thang)"
internal const val YEAR_UNIT = "(?:years?|yrs?|yr|y|año|ano|an|année|annee|jahr|jahre|anni|jaar|ar|rok|lata|yil|tahun|mwaka|taon|nam)s?"
internal const val QUARTER_UNIT = "(?:quarters?|qtrs?|qtr)"
