package run.jkdev.dec4iot.jetpack.gsonmodels

import com.google.gson.annotations.SerializedName

data class OnboardingQr(
    @SerializedName("sensor-id")
    var sensorId: String?,

    @SerializedName("serviceEndpoint")
    var endpoint: String?
)
