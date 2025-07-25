package com.cuevana

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import java.util.*

class CuevanaProvider : MainAPI() {
    override var mainUrl = "https://wv5n.cuevana.biz"
    override var name = "Cuevana"
    override val hasMainPage = true
    override var lang = "es"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/estrenos" to "Estrenos",
        "$mainUrl/peliculas" to "Películas",
        "$mainUrl/series" to "Series",
        "$mainUrl/populares" to "Populares"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data + if (page == 1) "" else "?page=$page").document
        val home = document.select("article.TPost").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.title a")?.text()?.trim() ?: return null
        val href = fixUrl(this.selectFirst("h2.title a")?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.selectFirst("figure img")?.attr("data-src") ?: this.selectFirst("figure img")?.attr("src"))
        val quality = getQualityFromString(this.selectFirst("span.Qlty")?.text())
        val year = this.selectFirst("span.year")?.text()?.toIntOrNull()
        
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = app.get("$mainUrl/?s=$query").document
        return searchResponse.select("article.TPost").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1.title")?.text()?.trim() ?: return null
        val poster = fixUrlNull(document.selectFirst("div.poster img")?.attr("data-src") ?: document.selectFirst("div.poster img")?.attr("src"))
        val tags = document.select("div.genres a").map { it.text() }
        val year = document.selectFirst("span.date")?.text()?.substringAfter(",")?.trim()?.toIntOrNull()
        val tvType = if (document.select("div.seasons").isNotEmpty()) TvType.TvSeries else TvType.Movie
        val description = document.selectFirst("div.wp-content p")?.text()?.trim()
        val rating = document.selectFirst("span.dt_rating_vgs")?.text()?.toRatingInt()
        val duration = document.selectFirst("span.runtime")?.text()?.filter { it.isDigit() }?.toIntOrNull()

        return if (tvType == TvType.TvSeries) {
            val episodes = document.select("div.episodios article").map {
                val href = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
                val episodeName = it.selectFirst("h2.entry-title")?.text()
                val seasonEpisode = it.selectFirst("span.num-epi")?.text()?.split("x") ?: listOf("1", "1")
                val season = seasonEpisode.getOrNull(0)?.toIntOrNull() ?: 1
                val episode = seasonEpisode.getOrNull(1)?.toIntOrNull() ?: 0
                Episode(
                    data = href,
                    name = episodeName,
                    season = season,
                    episode = episode
                )
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        
        // Extract video sources from Cuevana
        document.select("div.player-option").forEach { server ->
            val serverUrl = server.attr("data-option")
            val serverName = server.text().trim()
            
            if (serverUrl.isNotEmpty()) {
                // Get the actual video URL from the server endpoint
                val videoResponse = app.get("$mainUrl/player.php?h=$serverUrl", referer = data)
                val videoDoc = videoResponse.document
                
                // Look for iframe sources
                videoDoc.select("iframe").forEach { iframe ->
                    val iframeSrc = iframe.attr("src")
                    if (iframeSrc.isNotEmpty()) {
                        extractFromIframe(iframeSrc, serverName, callback)
                    }
                }
                
                // Look for direct video sources
                videoDoc.select("source").forEach { source ->
                    val videoUrl = source.attr("src")
                    if (videoUrl.isNotEmpty()) {
                        callback.invoke(
                            ExtractorLink(
                                source = name,
                                name = "$name - $serverName",
                                url = videoUrl,
                                referer = data,
                                quality = getQualityFromUrl(videoUrl),
                                isM3u8 = videoUrl.contains(".m3u8")
                            )
                        )
                    }
                }
            }
        }
        
        // Extract subtitles
        document.select("track[kind=subtitles], track[kind=captions]").forEach { sub ->
            val subUrl = sub.attr("src")
            val subLang = sub.attr("srclang") ?: sub.attr("label") ?: "Spanish"
            if (subUrl.isNotEmpty()) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = subLang,
                        url = fixUrl(subUrl)
                    )
                )
            }
        }
        
        return true
    }

    private suspend fun extractFromIframe(url: String, serverName: String, callback: (ExtractorLink) -> Unit) {
        try {
            val doc = app.get(url, referer = mainUrl).document
            
            // Common patterns for embedded players
            doc.select("source").forEach { source ->
                val videoUrl = source.attr("src")
                if (videoUrl.isNotEmpty()) {
                    callback.invoke(
                        ExtractorLink(
                            source = name,
                            name = "$name - $serverName",
                            url = videoUrl,
                            referer = url,
                            quality = getQualityFromUrl(videoUrl),
                            isM3u8 = videoUrl.contains(".m3u8")
                        )
                    )
                }
            }
            
            // Look for JavaScript variables containing video URLs
            val scriptText = doc.select("script").joinToString(" ") { it.html() }
            Regex("""(?:file|src)["']?\s*:\s*["']([^"']+\.(?:mp4|m3u8|mkv))["']""").findAll(scriptText).forEach { match ->
                val videoUrl = match.groupValues[1]
                callback.invoke(
                    ExtractorLink(
                        source = name,
                        name = "$name - $serverName",
                        url = videoUrl,
                        referer = url,
                        quality = getQualityFromUrl(videoUrl),
                        isM3u8 = videoUrl.contains(".m3u8")
                    )
                )
            }
        } catch (e: Exception) {
            // Handle extraction errors silently
        }
    }

    private fun getQualityFromUrl(url: String): Int {
        return when {
            url.contains("1080") -> Qualities.P1080.value
            url.contains("720") -> Qualities.P720.value
            url.contains("480") -> Qualities.P480.value
            url.contains("360") -> Qualities.P360.value
            else -> Qualities.Unknown.value
        }
    }
}