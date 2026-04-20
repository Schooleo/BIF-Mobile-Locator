package com.bif.app.feature.map.model

data class TripSummary(
    val startTime: Long,
    val endTime: Long
) {
    fun getDurationFormatted(): String {
        val duration = endTime - startTime
        val minutes = duration / 60000
        val seconds = (duration % 60000) / 1000
        return "${minutes} min ${seconds} sec"
    }
}