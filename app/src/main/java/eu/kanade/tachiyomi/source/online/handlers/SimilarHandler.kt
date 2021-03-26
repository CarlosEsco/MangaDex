package eu.kanade.tachiyomi.source.online.handlers

import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.database.models.MangaSimilarImpl
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class SimilarHandler(val preferences: PreferencesHelper) {

    /**
     * fetch our similar mangas
     */
    fun fetchSimilar(manga: Manga): Observable<MangasPage> {

        // Parse the Mangadex id from the URL
        val mangaid = MdUtil.getMangaId(manga.url).toLong()
        val lowQualityCovers = preferences.lowQualityCovers()
        val cacheResponse = preferences.useCacheSource()

        // Get our current database
        val db = Injekt.get<DatabaseHelper>()
        val similarMangaDb = db.getSimilar(mangaid).executeAsBlocking() ?: return Observable.just(MangasPage(mutableListOf(), false))

        // Check if we have a result
        val similarMangaTitles = similarMangaDb.matched_titles.split(MangaSimilarImpl.DELIMITER)
        val similarMangaIds = similarMangaDb.matched_ids.split(MangaSimilarImpl.DELIMITER)
        val similarMangas = similarMangaIds.mapIndexed { index, similarId ->
            SManga.create().apply {
                initialized = false
                title = similarMangaTitles[index]
                url = "/manga/$similarId/"
                thumbnail_url = if(cacheResponse) null else MdUtil.formThumbUrl(url, lowQualityCovers)
            }
        }

        // Return the matches
        return Observable.just(MangasPage(similarMangas, false))
    }
}
