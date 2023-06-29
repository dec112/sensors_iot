package run.jkdev.dec4iot.jetpack.gsonmodels

import com.google.gson.annotations.SerializedName

data class BangleJsConfig(
    @SerializedName("configured")
    var configured: Boolean,

    @SerializedName("sensor_id")
    var sensor_id: Number,

    @SerializedName("sensor_endpoint")
    var sensor_endpoint: String,

    @SerializedName("sensor_update_interval")
    var sensor_update_interval: Number
)
