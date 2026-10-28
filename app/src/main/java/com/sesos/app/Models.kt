package com.sesos.app

enum class Kind { VIDEO, AUDIO, IMAGE }
enum class Status { RUNNING, DONE, ERROR, CANCELED }

data class MediaInfo(
    val pageUrl: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val duration: Int,
    val heights: List<Int>,
    val hasVideo: Boolean,
    val hasAudio: Boolean,
    val imageUrl: String?
)

data class DownloadItem(
    val id: String,
    val title: String,
    val kind: Kind,
    val progress: Float = 0f,
    val status: Status = Status.RUNNING,
    val message: String? = null,
    val uri: String? = null,
    val mime: String? = null
)
