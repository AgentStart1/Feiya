package com.storyteller_f.feiya

enum class HidTaskKind { TEXT, RAW_KEY, CALIBRATION }

enum class HidTaskStatus {
    QUEUED, SENDING, CANCELLING, SENT, FAILED, CANCELLED;

    val isFinished: Boolean get() = this == SENT || this == FAILED || this == CANCELLED
    val canCancel: Boolean get() = this == QUEUED || this == SENDING
}

enum class HidTaskFailure { UNSUPPORTED_TEXT, NOT_CONNECTED, CONNECTION_CHANGED, SEND_FAILED }

/** Progress counts completed press/release pairs, not host-side text acknowledgement. */
data class HidSendTask(
    val id: Long,
    val kind: HidTaskKind,
    val deviceName: String?,
    val totalKeys: Int,
    val sentKeys: Int = 0,
    val status: HidTaskStatus = HidTaskStatus.QUEUED,
    val failure: HidTaskFailure? = null,
)
