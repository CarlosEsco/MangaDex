package eu.kanade.tachiyomi.data.database.models

import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.isMerged
import eu.kanade.tachiyomi.source.model.isMergedChapter
import eu.kanade.tachiyomi.source.online.utils.MdUtil
import java.io.Serializable

interface Chapter : SChapter, Serializable {

    var id: Long?

    var manga_id: Long?
    
    var read: Boolean

    var bookmark: Boolean

    var last_page_read: Int

    var pages_left: Int

    var date_fetch: Long

    var source_order: Int

    val isRecognizedNumber: Boolean
        get() = chapter_number >= 0f

    companion object {

        fun create(): Chapter = ChapterImpl().apply {
            chapter_number = -1f
        }
    }
}

fun Chapter.scanlatorList(): List<String> {
    this.scanlator ?: return emptyList()
    return MdUtil.getScanlators(this.scanlator!!)
}

fun List<Chapter>.filterIfUsingCache(downloadManager: DownloadManager, manga: Manga, usingCachedManga: Boolean, ignoreMangaIsMerged: Boolean = false): List<Chapter> {
    return this.filter {
        when {
            usingCachedManga.not() -> true
            downloadManager.isChapterDownloaded(it, manga) -> true
            it.isMergedChapter() -> true
            manga.isMerged().not() -> !ignoreMangaIsMerged
            else -> false
        }
    }
}

