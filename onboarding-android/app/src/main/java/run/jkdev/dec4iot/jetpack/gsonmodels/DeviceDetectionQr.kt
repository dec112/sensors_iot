package run.jkdev.dec4iot.jetpack.gsonmodels

import com.google.gson.annotations.SerializedName

data class DeviceDetectionQr(
    @SerializedName("hello")
    var trustyDevice: String,

    @SerializedName("bleMac")
    var macAddress: String
)
