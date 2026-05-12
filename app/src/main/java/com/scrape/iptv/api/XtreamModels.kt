package com.scrape.iptv.api

import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

data class XtreamAuthResponse(
    @SerializedName("user_info") val userInfo: UserInfo?,
    @SerializedName("server_info") val serverInfo: ServerInfo?,
)

data class UserInfo(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("auth") val auth: Int? = null,
    @SerializedName("status") val status: String? = null,
    @SerializedName("exp_date") val expDate: String? = null,
    @SerializedName("is_trial") val isTrial: String? = null,
    @SerializedName("active_cons") val activeCons: String? = null,
    @SerializedName("max_connections") val maxConnections: String? = null,
)

data class ServerInfo(
    @SerializedName("url") val url: String? = null,
    @SerializedName("port") val port: String? = null,
    @SerializedName("https_port") val httpsPort: String? = null,
    @SerializedName("server_protocol") val serverProtocol: String? = null,
    @SerializedName("timezone") val timezone: String? = null,
)

data class LiveCategory(
    @SerializedName("category_id")
    @JsonAdapter(FlexibleStringAdapter::class)
    val categoryId: String? = null,
    @SerializedName("category_name") val categoryName: String? = null,
    @SerializedName("parent_id")
    @JsonAdapter(FlexibleStringAdapter::class)
    val parentId: String? = null,
)

data class LiveStream(
    @SerializedName("num") val num: Int? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("stream_type") val streamType: String? = null,
    @SerializedName("stream_id")
    @JsonAdapter(FlexibleStringAdapter::class)
    val streamId: String? = null,
    @SerializedName("stream_icon") val streamIcon: String? = null,
    @SerializedName("epg_channel_id")
    @JsonAdapter(FlexibleStringAdapter::class)
    val epgChannelId: String? = null,
    @SerializedName("category_id")
    @JsonAdapter(FlexibleStringAdapter::class)
    val categoryId: String? = null,
)
