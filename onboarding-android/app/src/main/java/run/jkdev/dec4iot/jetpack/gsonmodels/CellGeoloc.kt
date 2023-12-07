package run.jkdev.dec4iot.jetpack.gsonmodels

import com.google.gson.annotations.SerializedName

data class CellGeolocPayload(
    val carrier: String,
    @SerializedName("considerIp")
    val shouldConsiderIpAddress: Boolean,
    @SerializedName("homeMobileCountryCode")
    val simMobileCountryCode: Number,
    @SerializedName("homeMobileNetworkCode")
    val simMobileNetworkCode: Number,
    @SerializedName("cellTowers")
    val cells: List<CellInfo>
)

data class MLSResponse(
    val location: MLSLocation,
    val accuracy: Number,
    val fallback: String?
)

data class MLSLocation (
    @SerializedName("lat")
    val latitude: Number,
    @SerializedName("lng")
    val longitude: Number
)

data class CellInfo(
    val radioType: String,
    @SerializedName("mobileCountryCode")
    val mcc: Number,
    @SerializedName("mobileNetworkCode")
    val mnc: Number,
    val locationAreaCode: Number,
    val cellId: Number,
    @SerializedName("age")
    val lastDetectionMs: Number?,
    val signalStrength: Number?,
    val timingAdvance: Number?
)

data class NetworkInfo(
    val carrier: String,
    val mcc: Number,
    val mnc: Number
)
