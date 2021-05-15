package eu.kanade.tachiyomi.ui.similar

import android.app.Activity
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.list.listItemsSingleChoice
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.preference.getOrDefault
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.ui.library.LibraryGroup
import eu.kanade.tachiyomi.ui.manga.MangaDetailsPresenter
import eu.kanade.tachiyomi.ui.manga.similar.SimilarPresenter
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourcePresenter
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.marginTop
import eu.kanade.tachiyomi.util.view.scrollViewWith
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import kotlinx.android.synthetic.main.browse_source_controller.*
import kotlinx.android.synthetic.main.browse_source_controller.empty_view
import kotlinx.android.synthetic.main.browse_source_controller.swipe_refresh
import kotlinx.android.synthetic.main.library_list_controller.*
import kotlinx.android.synthetic.main.manga_details_controller.*

/**
 * Controller that shows the latest manga from the catalogue. Inherit [BrowseCatalogueController].
 */
class SimilarController(bundle: Bundle) : BrowseSourceController(bundle) {

    lateinit var similarPresenter : SimilarPresenter

    constructor(manga: Manga, source: Source) : this(
        Bundle().apply {
            putLong(MANGA_ID, manga.id!!)
            putLong(SOURCE_ID_KEY, source.id)
            putBoolean(APPLY_INSET, false)
        }
    )

    override fun getTitle(): String? {
        return view?.context?.getString(R.string.similar)
    }

    override fun createPresenter(): BrowseSourcePresenter {
        similarPresenter = SimilarPresenter(bundle!!.getLong(MANGA_ID), this)
        return similarPresenter
    }

    override fun onViewCreated(view: View) {
        super.onViewCreated(view)
        fab.gone()
        swipe_refresh.isEnabled = true
        swipe_refresh.isRefreshing = similarPresenter.isRefreshing
        swipe_refresh.setProgressViewOffset(false, 0.dpToPx, 120.dpToPx)
        swipe_refresh.setOnRefreshListener {
            similarPresenter.refreshSimilarManga()
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.action_search).isVisible = false
        menu.findItem(R.id.action_open_in_web_view).isVisible = false
    }

    fun showUserMessage(message: String) {
        swipe_refresh?.isRefreshing = similarPresenter.isRefreshing
        view?.snack(message, Snackbar.LENGTH_LONG)
    }

    /**
     * Called from the presenter when the network request fails.
     *
     * @param error the error received.
     */
    override fun onAddPageError(error: Throwable) {
        super.onAddPageError(error)
        empty_view.show(
            CommunityMaterial.Icon.cmd_compass_off,
            "No Similar Manga found"
        )
    }

    override fun expandSearch() {
        activity?.onBackPressed()
    }
}
