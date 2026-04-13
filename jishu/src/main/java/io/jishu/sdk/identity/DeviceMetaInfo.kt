package io.jishu.sdk.identity

import android.os.Build

internal data class DeviceMetaInfo(
    val osName: String,
    val osVersion: String,
    val deviceName: String
)

internal fun collectDeviceMetaInfo(): DeviceMetaInfo {
    val osName = "Android"
    val osVersion = Build.VERSION.RELEASE?.trim().orEmpty()
    val rawModel = Build.MODEL?.trim().orEmpty()
    val deviceName = rawModel.ifEmpty { "unknown" }

    return DeviceMetaInfo(
        osName = osName,
        osVersion = osVersion,
        deviceName = deviceName
    )
}
