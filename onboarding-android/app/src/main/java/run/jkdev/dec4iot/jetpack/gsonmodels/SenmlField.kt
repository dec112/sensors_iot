package run.jkdev.dec4iot.jetpack.gsonmodels

import com.google.gson.annotations.SerializedName

data class SenmlField(
    @SerializedName("n")
    val name: String,

    @SerializedName("u")
    val unit: String? = null,

    @SerializedName("v")
    val value: Number? = null,

    @SerializedName("vs")
    val string_value: String? = null,

    @SerializedName("vb")
    val boolean_value: Boolean? = null,

    @SerializedName("s")
    val sum: Number? = null,

    @SerializedName("t")
    val time: String? = null,

    @SerializedName("ut")
    val update_time: Number? = null,

    @SerializedName("bn")
    val base_name: String? = null,

    @SerializedName("bt")
    var base_time: Double? = null,

    @SerializedName("bu")
    val base_unit: String? = null,

    @SerializedName("bv")
    val base_value: Number? = null,

    @SerializedName("bs")
    val base_sum: Number? = null,

    @SerializedName("bver")
    val base_version: Number? = null
)
