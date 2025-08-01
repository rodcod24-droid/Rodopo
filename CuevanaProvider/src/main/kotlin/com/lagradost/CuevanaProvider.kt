package com.lagradost

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class CuevanaProvider : MainAPI() {
    override var mainUrl = "https://w3nv.cuevana.pro"
    override var name = "Cuevana"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair(mainUrl, "Recientemente actualizadas"),
            Pair("$mainUrl/estrenos/", "Estrenos"),
        )
        
        // Add series section
        try {
            val seriesDoc = app.get("$mainUrl/serie", timeout = 120).document
            val seriesItems = seriesDoc.select("section.home-series li, .MovieList li, .TPostMv").mapNotNull {
                try {
                    val title = it.selectFirst("h2.Title, .Title, h3")?.text()
                    if (title.isNullOrBlank()) return@mapNotNull null
                    
                    val poster = it.selectFirst("img.lazy")?.attr("data-src") 
                        ?: it.selectFirst("img")?.attr("src")
                    if (poster.isNullOrBlank()) return@mapNotNull null
                    
                    val url = it.selectFirst("a")?.attr("href")
                    if (url.isNullOrBlank()) return@mapNotNull null
                    
                    TvSeriesSearchResponse(
                        title,
                        fixUrl(url),
                        this.name,
                        TvType.TvSeries,
                        fixUrl(poster),
                        null,
                        null,
                    )
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }
            
            if (seriesItems.isNotEmpty()) {
                items.add(HomePageList("Series", seriesItems))
            }
        } catch (e: Exception) {
            logError(e)
        }
        
        // Add other sections
        for ((url, name) in urls) {
            try {
                val soup = app.get(url, timeout = 120).document
                val home = soup.select("section li.xxx.TPostMv, .MovieList li, .TPostMv").mapNotNull {
                    try {
                        val title = it.selectFirst("h2.Title, .Title, h3")?.text()
                        if (title.isNullOrBlank()) return@mapNotNull null
                        
                        val link = it.selectFirst("a")?.attr("href")
                        if (link.isNullOrBlank()) return@mapNotNull null
                        
                        val poster = it.selectFirst("img.lazy")?.attr("data-src") 
                            ?: it.selectFirst("img")?.attr("src") ?: ""
                        
                        val isMovie = link.contains("/pelicula/") || link.contains("/movie/")
                        
                        if (isMovie) {
                            MovieSearchResponse(
                                title,
                                fixUrl(link),
                                this.name,
                                TvType.Movie,
                                fixUrl(poster),
                                null
                            )
                        } else {
                            TvSeriesSearchResponse(
                                title,
                                fixUrl(link),
                                this.name,
                                TvType.TvSeries,
                                fixUrl(poster),
                                null,
                                null,
                            )
                        }
                    } catch (e: Exception) {
                        logError(e)
                        null
                    }
                }

                if (home.isNotEmpty()) {
                    items.add(HomePageList(name, home))
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        if (items.isEmpty()) throw ErrorLoadingException()
        return HomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=${query.replace(" ", "+")}"
        
        return try {
            val document = app.get(searchUrl, timeout = 120).document
            document.select("li.xxx.TPostMv, .MovieList li, .TPostMv").mapNotNull {
                try {
                    val title = it.selectFirst("h2.Title, .Title, h3")?.text()
                    if (title.isNullOrBlank()) return@mapNotNull null
                    
                    val href = it.selectFirst("a")?.attr("href")
                    if (href.isNullOrBlank()) return@mapNotNull null
                    
                    val image = it.selectFirst("img.lazy")?.attr("data-src") 
                        ?: it.selectFirst("img")?.attr("src") ?: ""
                    
                    val isSerie = href.contains("/serie/") || href.contains("/tv/")

                    if (isSerie) {
                        TvSeriesSearchResponse(
                            title,
                            fixUrl(href),
                            this.name,
                            TvType.TvSeries,
                            fixUrl(image),
                            null,
                            null
                        )
                    } else {
                        MovieSearchResponse(
                            title,
                            fixUrl(href),
                            this.name,
                            TvType.Movie,
                            fixUrl(image),
                            null
                        )
                    }
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }
        } catch (e: Exception) {
            logError(e)
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val soup = app.get(url, timeout = 120).document
            val title = soup.selectFirst("h1.Title, h1, .Title")?.text()
            if (title.isNullOrBlank()) return null
            
            val description = soup.selectFirst(".Description p, .wp-content p, .sinopsis")?.text()?.trim()
            val poster = soup.selectFirst(".movtv-info div.Image img, .poster img, img.wp-post-image")?.attr("data-src")
                ?: soup.selectFirst(".movtv-info div.Image img, .poster img, img.wp-post-image")?.attr("src")
            
            // Extract year
            val yearText = soup.selectFirst("footer p.meta, .year, .date")?.text() ?: ""
            val yearRegex = Regex("(\\d{4})")
            val year = yearRegex.find(yearText)?.value?.toIntOrNull()
            
            // Get episodes for TV series
            val episodes = soup.select(".all-episodes li.TPostMv article, .episodes-list li, .episode-item").mapNotNull { li ->
                try {
                    val href = li.select("a").attr("href")
                    if (href.isBlank()) return@mapNotNull null
                    
                    val epThumb = li.selectFirst("div.Image img, img")?.attr("data-src") 
                        ?: li.selectFirst("div.Image img, img")?.attr("src") ?: ""
                    
                    val seasonEpisodeText = li.selectFirst("span.Year, .episode-number, .ep-num")?.text() ?: ""
                    val seasonEpisodeParts = seasonEpisodeText.split("x")
                    
                    val season = if (seasonEpisodeParts.size >= 2) {
                        seasonEpisodeParts[0].trim().toIntOrNull() ?: 1
                    } else 1
                    
                    val episode = if (seasonEpisodeParts.size >= 2) {
                        seasonEpisodeParts[1].trim().toIntOrNull()
                    } else null
                    
                    Episode(
                        fixUrl(href),
                        li.selectFirst(".episode-title, .Title")?.text(),
                        season,
                        episode,
                        fixUrl(epThumb)
                    )
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }
            
            val tags = soup.select("ul.InfoList li.AAIco-adjust:contains(Genero) a, .genres a, .genre").map { it.text() }
            val tvType = if (episodes.isEmpty()) TvType.Movie else TvType.TvSeries
            
            // Get recommendations
            val recelement = if (tvType == TvType.TvSeries) {
                "main section div.series_listado.series div.xxx, .related-posts .post-item"
            } else {
                "main section ul.MovieList li, .related-posts .post-item"
            }
                
            val recommendations = soup.select(recelement).mapNotNull { element ->
                try {
                    val recTitle = element.select("h2.Title, .Title, h3").text()
                    if (recTitle.isBlank()) return@mapNotNull null
                    
                    val image = element.select("figure img, img").attr("data-src")
                        .ifBlank { element.select("figure img, img").attr("src") }
                    val recUrl = element.select("a").attr("href")
                    if (recUrl.isBlank()) return@mapNotNull null
                    
                    MovieSearchResponse(
                        recTitle,
                        fixUrl(recUrl),
                        this.name,
                        TvType.Movie,
                        fixUrl(image),
                        year = null
                    )
                } catch (e: Exception) {
                    logError(e)
                    null
                }
            }

            when (tvType) {
                TvType.TvSeries -> {
                    TvSeriesLoadResponse(
                        title,
                        url,
                        this.name,
                        tvType,
                        episodes,
                        fixUrl(poster ?: ""),
                        year,
                        description,
                        tags = tags,
                        recommendations = recommendations
                    )
                }
                TvType.Movie -> {
                    MovieLoadResponse(
                        title,
                        url,
                        this.name,
                        tvType,
                        url,
                        fixUrl(poster ?: ""),
                        year,
                        description,
                        tags = tags,
                        recommendations = recommendations
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            logError(e)
            null
        }
    }

    data class FembedResponse(
        @JsonProperty("url") val url: String,
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val document = app.get(data, timeout = 120).document
            var found = false
            
            // Look for video players in iframes
            document.select("div.TPlayer.embed_div iframe, iframe, .video-player iframe").forEach { iframe ->
                try {
                    val iframeSrc = iframe.attr("data-src").ifBlank { iframe.attr("src") }
                    if (iframeSrc.isNotBlank()) {
                        val iframeUrl = fixUrl(iframeSrc)
                        
                        // Handle fembed/cuevana API links
                        if (iframeUrl.contains("fembed") || iframeUrl.contains("api.cuevana")) {
                            val femregex = Regex("(https?://[^/]+/fembed/\\?h=[a-zA-Z0-9_-]+)")
                            femregex.findAll(iframeUrl).forEach { femMatch ->
                                try {
                                    val femUrl = femMatch.value
                                    val key = femUrl.substringAfter("?h=")
                                    
                                    val response = app.post(
                                        "https://api.cuevana3.me/fembed/api.php",
                                        headers = mapOf(
                                            "User-Agent" to USER_AGENT,
                                            "Accept" to "application/json, text/javascript, */*; q=0.01",
                                            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                                            "X-Requested-With" to "XMLHttpRequest",
                                            "Referer" to mainUrl
                                        ),
                                        data = mapOf("h" to key),
                                        timeout = 60
                                    ).text
                                    
                                    val json = parseJson<FembedResponse>(response)
                                    if (json.url.isNotBlank()) {
                                        loadExtractor(json.url, data, subtitleCallback, callback)
                                        found = true
                                    }
                                } catch (e: Exception) {
                                    logError(e)
                                }
                            }
                        } else {
                            // Try to load other extractors
                            try {
                                loadExtractor(iframeUrl, data, subtitleCallback, callback)
                                found = true
                            } catch (e: Exception) {
                                logError(e)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logError(e)
                }
            }
            
            found
        } catch (e: Exception) {
            logError(e)
            false
        }
    }
}
