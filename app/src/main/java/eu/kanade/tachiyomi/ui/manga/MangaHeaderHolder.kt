package eu.kanade.tachiyomi.ui.manga

import android.annotation.SuppressLint
import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.loadAny
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.materialdesigndx.MaterialDesignDx
import com.mikepenz.iconics.utils.colorInt
import com.mikepenz.iconics.utils.contourColorInt
import com.mikepenz.iconics.utils.sizeDp
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Manga
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.isMerged
import eu.kanade.tachiyomi.source.model.isMergedChapter
import eu.kanade.tachiyomi.ui.base.holder.BaseFlexibleViewHolder
import eu.kanade.tachiyomi.util.system.contextCompatColor
import eu.kanade.tachiyomi.util.system.dpToPx
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.iconicsDrawable
import eu.kanade.tachiyomi.util.system.iconicsDrawableLarge
import eu.kanade.tachiyomi.util.system.isLTR
import eu.kanade.tachiyomi.util.view.gone
import eu.kanade.tachiyomi.util.view.isVisible
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.visInvisIf
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.util.view.visibleIf
import kotlinx.android.synthetic.main.manga_details_controller.*
import kotlinx.android.synthetic.main.manga_header_item.*
import java.util.Locale

class MangaHeaderHolder(
    private val view: View,
    private val adapter: MangaDetailsAdapter,
    startExpanded: Boolean
) : BaseFlexibleViewHolder(view, adapter) {

    private var showReadingButton = true
    private var showMoreButton = true

    init {
        chapter_layout.setOnClickListener { adapter.delegate.showChapterFilter() }
        if (start_reading_button != null) {
            start_reading_button.setOnClickListener { adapter.delegate.readNextChapter() }
            top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
                height = adapter.delegate.topCoverHeight()
            }
            more_button.setOnClickListener { expandDesc() }
            manga_summary.setOnClickListener {
                if (more_button_group.visibility == View.VISIBLE) {
                    expandDesc()
                } else {
                    collapseDesc()
                }
            }
            manga_summary.setOnLongClickListener {
                if (manga_summary.isTextSelectable && !adapter.recyclerView.canScrollVertically(-1)) {
                    (adapter.delegate as MangaDetailsController).swipe_refresh.isEnabled = false
                }
                false
            }
            manga_summary.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) view.requestFocus()
                false
            }
            if (!itemView.resources.isLTR) {
                more_bg_gradient.rotation = 180f
            }
            less_button.setOnClickListener { collapseDesc() }
            manga_genres_tags.setOnTagClickListener {
                adapter.delegate.tagClicked(it)
            }
            webview_button.setOnClickListener { adapter.delegate.showExternalSheet() }
            similar_button.setOnClickListener { adapter.delegate.openSimilar() }


            merge_button.setOnClickListener { adapter.delegate.openMerge() }

            share_button.setOnClickListener { adapter.delegate.prepareToShareManga() }
            favorite_button.setOnClickListener {
                adapter.delegate.favoriteManga(false)
            }
            favorite_button.setOnLongClickListener {
                adapter.delegate.favoriteManga(true)
                true
            }

            title.setOnLongClickListener {
                adapter.delegate.copyToClipboard(title.text.toString(), R.string.title)
                true
            }
            manga_author.setOnLongClickListener {
                adapter.delegate.copyToClipboard(manga_author.text.toString(), R.string.author)
                true
            }
            manga_cover.setOnClickListener { adapter.delegate.zoomImageFromThumb(cover_card) }
            track_button.setOnClickListener { adapter.delegate.showTrackingSheet() }
            if (startExpanded) expandDesc()
            else collapseDesc()
        } else {
            filter_button.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                marginEnd = 12.dpToPx
            }
        }
    }

    private fun expandDesc() {
        if (more_button.visibility == View.VISIBLE) {
            manga_summary.maxLines = Integer.MAX_VALUE
            manga_summary.setTextIsSelectable(true)
            manga_genres_tags.visible()
            less_button.visible()
            more_button_group.gone()
            title.maxLines = Integer.MAX_VALUE
        }
    }

    private fun collapseDesc() {
        manga_summary.setTextIsSelectable(false)
        manga_summary.isClickable = true
        manga_summary.maxLines = 3
        manga_genres_tags.gone()
        less_button.gone()
        more_button_group.visible()
        title.maxLines = 4
        adapter.recyclerView.post {
            adapter.delegate.updateScroll()
        }
    }

    @SuppressLint("SetTextI18n")
    fun bind(item: MangaHeaderItem, manga: Manga) {
        val presenter = adapter.delegate.mangaPresenter()
        title.text = manga.title

        if (manga.genre.isNullOrBlank().not()) manga_genres_tags.setTags(
            manga.genre?.split(",")?.map(String::trim)
        )
        else manga_genres_tags.setTags(emptyList())

        if (manga.author == manga.artist || manga.artist.isNullOrBlank()) {
            manga_author.text = manga.author?.trim()
        } else {
            manga_author.text = listOfNotNull(manga.author?.trim(), manga.artist?.trim()).joinToString(", ")
        }
        manga_summary.text =
            if (manga.description.isNullOrBlank()) itemView.context.getString(R.string.no_description)
            else manga.description?.trim()

        manga_summary.post {
            if (sub_item_group.visibility != View.GONE) {
                if ((manga_summary.lineCount < 3 && manga.genre.isNullOrBlank()) || less_button.isVisible()) {
                    manga_summary.setTextIsSelectable(true)
                    more_button_group.gone()
                    showMoreButton = less_button.isVisible()
                } else {
                    more_button_group.visible()
                }
            }
            if (adapter.hasFilter()) collapse()
            else expand()
        }
        manga_summary_label.text = itemView.context.getString(
            R.string.about_this_, manga.mangaType(itemView.context)
        )
        with(favorite_button) {
            val icon = when {
                item.isLocked -> MaterialDesignDx.Icon.gmf_lock
                item.manga.favorite -> CommunityMaterial.Icon2.cmd_heart as IIcon
                else -> CommunityMaterial.Icon2.cmd_heart_outline as IIcon
            }
            setImageDrawable(context.iconicsDrawableLarge(icon))
            adapter.delegate.setFavButtonPopup(this)
        }

        val tracked = presenter.isTracked() && !item.isLocked

        with(track_button) {
            setImageDrawable(context.iconicsDrawable(MaterialDesignDx.Icon.gmf_art_track, size = 32))
        }

        with(similar_button) {
            setImageDrawable(context.iconicsDrawableLarge(MaterialDesignDx.Icon.gmf_account_tree))
        }

        with(merge_button) {
            visibleIf(manga.status != SManga.COMPLETED || presenter.preferences.useCacheSource())
            val iconics = context.iconicsDrawableLarge(MaterialDesignDx.Icon.gmf_merge_type)
            if (presenter.manga.isMerged().not()) {
                iconics.colorInt = context.contextCompatColor(android.R.color.transparent)
                iconics.contourColorInt = context.getResourceColor(R.attr.colorAccent)
                iconics.contourWidthPx = 6
                iconics.sizeDp = 28
            }
            setImageDrawable(iconics)
        }

        with(webview_button) {
            setImageDrawable(context.iconicsDrawableLarge(CommunityMaterial.Icon2.cmd_web))
        }
        with(share_button) {
            setImageDrawable(context.iconicsDrawableLarge(MaterialDesignDx.Icon.gmf_share))
        }

        with(start_reading_button) {
            val nextChapter = presenter.getNextUnreadChapter()
            visibleIf(presenter.chapters.isNotEmpty() && !item.isLocked && !adapter.hasFilter())
            showReadingButton = isVisible()
            isEnabled = (nextChapter != null)
            text = if (nextChapter != null) {
                val readTxt =
                    if (nextChapter.isMergedChapter() || (nextChapter.chapter.vol.isEmpty() && nextChapter.chapter.chapter_txt.isEmpty())) {
                        nextChapter.chapter.name
                    } else {
                        listOf(nextChapter.chapter.vol, nextChapter.chapter.chapter_txt).joinToString(" ")
                    }
                resources.getString(
                    if (nextChapter.last_page_read > 0) R.string.continue_reading_
                    else R.string.start_reading_,
                    readTxt
                )
            } else {
                resources.getString(R.string.all_chapters_read)
            }
        }

        val count = presenter.chapters.size
        chapters_title.text = itemView.resources.getQuantityString(R.plurals.chapters, count, count)

        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = adapter.delegate.topCoverHeight()
        }

        manga_status.visibleIf(manga.status != 0)
        manga_status.text = (
            itemView.context.getString(
                when (manga.status) {
                    SManga.ONGOING -> R.string.ongoing
                    SManga.COMPLETED -> R.string.completed
                    SManga.LICENSED -> R.string.licensed
                    SManga.PUBLICATION_COMPLETE -> R.string.publication_complete
                    SManga.HIATUS -> R.string.hiatus
                    SManga.CANCELLED -> R.string.cancelled
                    else -> R.string.unknown
                }
            )
            )

        manga_rating.visibleIf(manga.rating != null)
        manga_rating.text = "  " + manga.rating

        manga_users.visibleIf(manga.users != null)
        manga_users.text = "  " + manga.users

        manga_missing_chapters.visibleIf(manga.missing_chapters != null)

        manga_missing_chapters.text = itemView.context.getString(R.string.missing_chapters, manga.missing_chapters)

        manga.genre?.let {
            r18_badge.visibleIf(it.contains("Hentai", true))
        }

        manga_lang_flag.visibility = View.VISIBLE
        when (manga.lang_flag?.toLowerCase(Locale.US)) {
            "cn" -> manga_lang_flag.setImageResource(R.drawable.ic_flag_china)
            "kr" -> manga_lang_flag.setImageResource(R.drawable.ic_flag_korea)
            "jp" -> manga_lang_flag.setImageResource(R.drawable.ic_flag_japan)
            else -> manga_lang_flag.visibility = View.GONE
        }

        filters_text.text = presenter.currentFilters()

        if (!manga.initialized) return
        updateCover(manga)
    }

    fun setTopHeight(newHeight: Int) {
        top_view.updateLayoutParams<ConstraintLayout.LayoutParams> {
            height = newHeight
        }
    }

    fun setBackDrop(color: Int) {
        true_backdrop.setBackgroundColor(color)
    }

    fun collapse() {
        val shouldHide = more_button.visibility == View.VISIBLE || more_button.visibility == View.INVISIBLE
        sub_item_group.gone()
        start_reading_button.gone()
        more_button_group.visInvisIf(!shouldHide)
        less_button.visibleIf(shouldHide)
        manga_genres_tags.visibleIf(shouldHide)
    }

    fun updateCover(manga: Manga) {
        if (!manga.initialized) return

        manga_cover.loadAny(
            manga,
            builder = {
                if (manga.favorite) networkCachePolicy(CachePolicy.DISABLED)
            }
        )

        val request = ImageRequest.Builder(view.context)
            .data(manga)
            .allowHardware(false) // Disable hardware bitmaps.
            .target { drawable ->
                // Generate the Palette on a background thread.
                adapter.delegate.generatePalette(drawable.toBitmap())
            }
            .build()

        Coil.imageLoader(view.context).enqueue(request)


        backdrop.loadAny(
            manga,
            builder = {
                if (manga.favorite) networkCachePolicy(CachePolicy.DISABLED)
            }
        )
    }

    fun expand() {
        sub_item_group.visible()
        if (!showMoreButton) more_button_group.gone()
        else {
            if (manga_summary.maxLines != Integer.MAX_VALUE) more_button_group.visible()
            else {
                less_button.visible()
                manga_genres_tags.visible()
            }
        }
        start_reading_button.visibleIf(showReadingButton)
    }

    fun showSimilarToolTip(activity: Activity?) {
        val act = activity ?: return
        SimilarToolTip(activity, view.context, similar_button)
    }

    override fun onLongClick(view: View?): Boolean {
        super.onLongClick(view)
        return false
    }
}
