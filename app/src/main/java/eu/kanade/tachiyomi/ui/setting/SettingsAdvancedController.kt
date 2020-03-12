package eu.kanade.tachiyomi.ui.setting

import android.app.Dialog
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Toast
import androidx.preference.PreferenceScreen
import com.afollestad.materialdialogs.MaterialDialog
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.cache.ChapterCache
import eu.kanade.tachiyomi.data.cache.CoverCache
import eu.kanade.tachiyomi.data.database.DatabaseHelper
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.glide.GlideApp
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.library.LibraryUpdateService.Target
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.util.DiskUtil
import eu.kanade.tachiyomi.util.launchUI
import eu.kanade.tachiyomi.util.toast
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import rx.Observable
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class SettingsAdvancedController : SettingsController() {

    private val network: NetworkHelper by injectLazy()

    private val chapterCache: ChapterCache by injectLazy()

    private val coverCache: CoverCache by injectLazy()

    private val db: DatabaseHelper by injectLazy()

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.pref_category_advanced

        preference {
            key = CLEAR_CACHE_KEY
            titleRes = R.string.pref_clear_chapter_cache
            summary = context.getString(R.string.used_cache, chapterCache.readableSize)

            onClick { clearChapterCache() }
        }
        preference {
            key = CLEAR_CACHE_IMAGES_KEY
            titleRes = R.string.pref_clear_image_cache
            summary = context.getString(R.string.used_cache, getChaperCacheSize())

            onClick { clearImageCache() }
        }
        preference {
            titleRes = R.string.pref_clear_cookies

            onClick {
                network.cookieManager.removeAll()
                activity?.toast(R.string.cookies_cleared)
            }
        }
        preference {
            titleRes = R.string.pref_clear_database
            summaryRes = R.string.pref_clear_database_summary

            onClick {
                val ctrl = ClearDatabaseDialogController()
                ctrl.targetController = this@SettingsAdvancedController
                ctrl.showDialog(router)
            }
        }

        preference {
            titleRes = R.string.pref_refresh_library_tracking
            summaryRes = R.string.pref_refresh_library_tracking_summary

            onClick { LibraryUpdateService.start(context, target = Target.TRACKING) }
        }
        preference {
            titleRes = R.string.pref_clean_downloads

            summaryRes = R.string.pref_clean_downloads_summary

            onClick { cleanupDownloads() }
        }
    }

    private fun cleanupDownloads() {
        if (job?.isActive == true) return
        activity?.toast(R.string.starting_cleanup)
        job = GlobalScope.launch(Dispatchers.IO, CoroutineStart.DEFAULT) {
            val mangaList = db.getMangas().executeAsBlocking()
            val sourceManager: SourceManager = Injekt.get()
            val downloadManager: DownloadManager = Injekt.get()
            var foldersCleared = 0
            val mdex = sourceManager.getMangadex()
            for (manga in mangaList) {
                val chapterList = db.getChapters(manga).executeAsBlocking()
                foldersCleared += downloadManager.cleanupChapters(chapterList, manga, mdex)
            }
            launchUI {
                val activity = activity ?: return@launchUI
                val cleanupString =
                    if (foldersCleared == 0) activity.getString(R.string.no_cleanup_done)
                    else resources!!.getQuantityString(
                        R.plurals.cleanup_done,
                        foldersCleared,
                        foldersCleared
                    )
                activity.toast(cleanupString, Toast.LENGTH_LONG)
            }
        }
    }

    private fun getChaperCacheSize(): String {
        val dirCache = GlideApp.getPhotoCacheDir(activity!!)
        val realSize1 = DiskUtil.getDirectorySize(dirCache!!)
        val realSize2 = DiskUtil.getDirectorySize(coverCache.cacheDir)
        return Formatter.formatFileSize(activity!!, realSize1 + realSize2)
    }

    private fun clearImageCache() {
        if (activity == null) return

        // Clear the glide disk cache for our chapters
        // Note: small hack since we require the glide clear to be in a background thread
        // https://stackoverflow.com/a/46292711
        val thread = Thread(Runnable {
            GlideApp.get(activity!!).clearDiskCache()
        })
        thread.start()
        GlideApp.get(activity!!).clearMemory()
        thread.join()

        // Delete all files from the image cache folder
        val files = coverCache.cacheDir.listFiles() ?: return
        var deletedFiles = 0
        Observable.defer { Observable.from(files) }
                .doOnNext { file ->
                    if (file.delete()) {
                        deletedFiles++
                    }
                }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                }, {
                    activity?.toast(R.string.cache_delete_error)
                }, {
                    activity?.toast(
                            resources?.getQuantityString(
                                    R.plurals.cache_deleted,
                                    deletedFiles, deletedFiles
                            )
                    )
                    findPreference(CLEAR_CACHE_IMAGES_KEY)?.summary =
                            resources?.getString(R.string.used_cache, getChaperCacheSize())
                })
    }

    private fun clearChapterCache() {
        if (activity == null) return

        val files = chapterCache.cacheDir.listFiles() ?: return
        var deletedFiles = 0
        Observable.defer { Observable.from(files) }
            .doOnNext { file ->
                if (chapterCache.removeFileFromCache(file.name)) {
                    deletedFiles++
                }
            }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            }, {
                activity?.toast(R.string.cache_delete_error)
            }, {
                activity?.toast(
                    resources?.getQuantityString(
                        R.plurals.cache_deleted,
                        deletedFiles, deletedFiles
                    )
                )
                findPreference(CLEAR_CACHE_KEY)?.summary =
                    resources?.getString(R.string.used_cache, chapterCache.readableSize)
            })
    }

    class ClearDatabaseDialogController : DialogController() {
        override fun onCreateDialog(savedViewState: Bundle?): Dialog {
            return MaterialDialog(activity!!)
                .message(R.string.clear_database_confirmation)
                .positiveButton(android.R.string.yes) {
                    (targetController as? SettingsAdvancedController)?.clearDatabase()
                }
                .negativeButton(android.R.string.no)
        }
    }

    private fun clearDatabase() {
        // Avoid weird behavior by going back to the library.
        val newBackstack = listOf(RouterTransaction.with(LibraryController())) +
            router.backstack.drop(1)

        router.setBackstack(newBackstack, FadeChangeHandler())

        db.deleteMangasNotInLibrary().executeAsBlocking()
        db.deleteHistoryNoLastRead().executeAsBlocking()
        activity?.toast(R.string.clear_database_completed)
    }

    private companion object {
        const val CLEAR_CACHE_KEY = "pref_clear_cache_key"
        const val CLEAR_CACHE_IMAGES_KEY = "pref_clear_cache_images_key"

        private var job: Job? = null
    }
}
