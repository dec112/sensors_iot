package run.jkdev.dec4iot.jetpack.gsonmodels

import com.google.gson.annotations.SerializedName

data class BangleJsSendData(
    @SerializedName("info")
    val info: BangleJsSendDataInfo,

    @SerializedName("data")
    val data: BangleJsSendDataData
)

data class BangleJsSendDataInfo(
    @SerializedName("sensor_id")
    val sensor_id: Number,

    @SerializedName("sensor_endpoint")
    val sensor_endpoint: String,

    @SerializedName("mac_address")
    val mac_address: String,

    @SerializedName("bpm_only")
    val bpm_only: Boolean,

    @SerializedName("trigger_manual")
    val manually_triggered: Boolean
)

data class BangleJsSendDataData(
    val acl: BangleJsAccelData?,
    val bar: BangleJsBaroData?,
    val com: BangleJsCompData?,
    val hrm: BangleJsHrmData?,
    val bat: Number?
)