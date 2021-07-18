package eu.kanade.tachiyomi.ui.manga.similar

import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.MangaListPage
import eu.kanade.tachiyomi.source.online.MangaDex
import eu.kanade.tachiyomi.ui.source.browse.NoResultsException
import eu.kanade.tachiyomi.ui.source.browse.Pager
import eu.kanade.tachiyomi.util.system.runAsObservable
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * SimilarPager inherited from the general Pager.
 */
class SimilarPager(val manga: Manga, val source: MangaDex) : Pager(1, false) {

    override fun requestNext(): Observable<MangaListPage> {
        return runAsObservable {
            if(currentPage == 1) {
                source.fetchSimilarManga(manga, false)
            } else if(currentPage == 2) {
                source.fetchSimilarExternalAnilistManga(manga, false)
            } else if(currentPage == 3) {
                source.fetchSimilarExternalMalManga(manga, false)
            } else {
                MangaListPage(emptyList(), false)
            }
        }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .doOnNext {
                if (it.manga.isNotEmpty() || currentPage < 5) {
                    onPageReceived(it)
                } else {
                    throw NoResultsException()
                }
            }
    }
}
