package com.favorito.work

import com.favorito.data.PendingLikeEntity
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Minimal YouTube Data API implementation for testing the queue approach.
 *
 * Official rating requires a YouTube video id. YouTube Music metadata usually
 * gives title/artist, not a stable video id, so this client searches by title
 * and artist first, then rates the top result. Production use should replace
 * this heuristic with an account-authorized resolver that stores the real id.
 */
class YoutubeDataApiClient(
    private val accessToken: String,
    private val http: OkHttpClient = OkHttpClient()
) {
    fun like(entity: PendingLikeEntity): Result<Unit> {
        val videoId = searchVideoId("${entity.artist} ${entity.title}")
            .getOrElse { return Result.failure(it) }
        return rateVideo(videoId)
    }

    private fun searchVideoId(query: String): Result<String> = runCatching {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("youtube/v3/search")
            .addQueryParameter("part", "id")
            .addQueryParameter("type", "video")
            .addQueryParameter("maxResults", "1")
            .addQueryParameter("q", query)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("search failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val items = JSONObject(body).getJSONArray("items")
            if (items.length() == 0) error("no YouTube result for queued like")
            items.getJSONObject(0).getJSONObject("id").getString("videoId")
        }
    }

    private fun rateVideo(videoId: String): Result<Unit> = runCatching {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("www.googleapis.com")
            .addPathSegments("youtube/v3/videos/rate")
            .addQueryParameter("id", videoId)
            .addQueryParameter("rating", "like")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .header("Authorization", "Bearer $accessToken")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("rate failed: HTTP ${response.code}")
        }
    }
}
