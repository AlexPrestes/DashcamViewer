package com.alexprestes.dashcamviewer.domain.model

import androidx.documentfile.provider.DocumentFile

data class VolumeInfo(
    val documentFile: DocumentFile,
    val name: String,
    val videoCount: Int
)