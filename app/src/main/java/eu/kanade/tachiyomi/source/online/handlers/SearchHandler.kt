package eu.kanade.tachiyomi.source.online.handlers

import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangaListPage
import eu.kanade.tachiyomi.source.online.handlers.dto.MangaListDto
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import eu.kanade.tachiyomi.v5.db.V5DbHelper
import kotlinx.serialization.decodeFromString
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

class SearchHandler {
    private val network: NetworkHelper by injectLazy()
    private val filterHandler: FilterHandler by injectLazy()
    private val preferences: PreferencesHelper by injectLazy()
    private val apiMangaParser: ApiMangaParser by injectLazy()
    private val v5DbHelper: V5DbHelper by injectLazy()

    fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangaListPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val realQuery = query.removePrefix(PREFIX_ID_SEARCH)
            network.client.newCall(searchMangaByIdRequest(realQuery))
                .asObservableSuccess()
                .map { response ->
                    val details = apiMangaParser.mangaDetailsParse(response.body!!.string())
                    details.url = "/title/$realQuery/"
                    MangaListPage(listOf(details), false)
                }
        } else {
            network.client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    searchMangaParse(response)
                }
        }
    }

    private fun searchMangaParse(response: Response): MangaListPage {
        if (response.isSuccessful.not()) {
            throw Exception("Error getting search manga http code: ${response.code}")
        }

        if (response.code == 204) {
            return MangaListPage(emptyList(), false)
        }

        val mlResponse =
            MdUtil.jsonParser.decodeFromString<MangaListDto>(response.body!!.string())
        val hasMoreResults = mlResponse.limit + mlResponse.offset < mlResponse.total
        val coverMap = MdUtil.getCoversFromMangaList(mlResponse.results, network.client)

        val mangaList = mlResponse.results.map {
            val coverUrl = coverMap[it.data.id]
            MdUtil.createMangaEntry(it, coverUrl)
        }
        return MangaListPage(mangaList, hasMoreResults)
    }

    private fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val tempUrl = MdUtil.mangaUrl.toHttpUrlOrNull()!!.newBuilder()

        tempUrl.apply {
            addQueryParameter("limit", MdUtil.mangaLimit.toString())
            addQueryParameter("offset", (MdUtil.getMangaListOffset(page)))
            val actualQuery = query.replace(WHITESPACE_REGEX, " ")
            if (actualQuery.isNotBlank()) {
                addQueryParameter("title", actualQuery)
            }
        }

        val finalUrl = filterHandler.addFiltersToUrl(tempUrl, filters)

        return GET(finalUrl, network.headers, CacheControl.FORCE_NETWORK)
    }

    private fun searchMangaByIdRequest(id: String): Request {
        return GET(MdUtil.mangaUrl + "/" + id, network.headers, CacheControl.FORCE_NETWORK)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        val WHITESPACE_REGEX = "\\s".toRegex()
    }
}
