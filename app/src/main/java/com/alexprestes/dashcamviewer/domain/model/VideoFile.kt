package com.alexprestes.dashcamviewer.domain.model

import android.net.Uri
import java.time.ZonedDateTime

data class VideoFile(
    val uri: Uri,
    val name: String,
    val timestamp: ZonedDateTime,
    //val endTimestamp: ZonedDateTime,
    val cameraType: CameraType,
    val isEvent: Boolean
)
