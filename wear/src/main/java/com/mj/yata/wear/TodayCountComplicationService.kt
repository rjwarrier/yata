package com.mj.yata.wear

import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest

class TodayCountComplicationService : ComplicationDataSourceService() {

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener
    ) {
        val count = TaskCountStore.getCount(applicationContext)
        listener.onComplicationData(buildData(count))
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return buildData(3)
    }

    private fun buildData(count: Int): ComplicationData {
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(count.toString()).build(),
            contentDescription = PlainComplicationText.Builder(
                if (count == 1) "1 task due today" else "$count tasks due today"
            ).build()
        )
            .setTitle(PlainComplicationText.Builder("YATA").build())
            .build()
    }
}
