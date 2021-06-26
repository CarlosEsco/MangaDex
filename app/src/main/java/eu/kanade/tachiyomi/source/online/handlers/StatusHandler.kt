package eu.kanade.tachiyomi.source.online.handlers

import com.elvishew.xlog.XLog
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import uy.kohesive.injekt.injectLazy

class StatusHandler {
    val preferences: PreferencesHelper by injectLazy()
    val network: NetworkHelper by injectLazy()

    /* suspend fun fetchMangaWithStatus(statusList: List<FollowStatus>) = flow<List<SManga>> {
         coroutineScope {
             val mangaKeys = statusList.map { it.toDex() }.map { status ->
                 async {
                     network.authService.readingStatusByType(status)
                 }
             }.awaitAll().map {
                 it.body()!!
             }.map {
                 it.statuses.keys
             }.flatten()

             val mangaList = mangaKeys.chunked(100)
                 .map { keys ->
                     async {
                         val map = mutableMapOf<String, Any>("ids[]" to keys)
                         network.service.search(ProxyRetrofitQueryMap(map))
                     }
                 }.awaitAll()
                 .map {
                     it.body()!!
                 }.map {
                     it.results.map { mangaDto ->
                         mangaDto.toBasicManga()
                     }
                 }.flatten()

             emit(mangaList)

         }
     }.flowOn(Dispatchers.IO)*/

    suspend fun markChapterRead(chapterId: String) {
        runCatching {
            network.authService.markChapterRead(chapterId)
        }.onFailure {
            XLog.e("error trying to mark chapter read", it)
        }
    }

    suspend fun markChapterUnRead(chapterId: String) {
        runCatching {
            network.authService.markChapterUnRead(chapterId)
        }.onFailure {
            XLog.e("error trying to mark chapter unread", it)
        }
    }

    suspend fun getReadChapterIds(mangaId: String) = flow {
        val result = runCatching {
            network.authService.readChaptersForManga(mangaId).body()!!.chapterIds.toSet()
        }.onFailure {
            XLog.e("error trying to get chapterIds", it)
        }
        emit(result.getOrDefault(emptySet()))
    }.flowOn(Dispatchers.IO)
}