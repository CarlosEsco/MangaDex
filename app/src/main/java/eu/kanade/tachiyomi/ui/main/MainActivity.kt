package eu.kanade.tachiyomi.ui.main

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.GestureDetector
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.webkit.WebView
import androidx.core.graphics.ColorUtils
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import com.bluelinelabs.conductor.Conductor
import com.bluelinelabs.conductor.Controller
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.Router
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler
import com.elvishew.xlog.XLog
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.google.android.material.snackbar.Snackbar
import com.mikepenz.iconics.typeface.library.materialdesigndx.MaterialDesignDx
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.Migrations
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.download.DownloadManager
import eu.kanade.tachiyomi.data.download.DownloadService
import eu.kanade.tachiyomi.data.download.DownloadServiceListener
import eu.kanade.tachiyomi.data.library.LibraryUpdateService
import eu.kanade.tachiyomi.data.notification.NotificationReceiver
import eu.kanade.tachiyomi.data.notification.Notifications
import eu.kanade.tachiyomi.data.updater.UpdateChecker
import eu.kanade.tachiyomi.data.updater.UpdateResult
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.SourceManager
import eu.kanade.tachiyomi.ui.base.activity.BaseActivity
import eu.kanade.tachiyomi.ui.base.controller.BaseController
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.ui.library.LibraryController
import eu.kanade.tachiyomi.ui.manga.MangaDetailsController
import eu.kanade.tachiyomi.ui.recent_updates.RecentChaptersController
import eu.kanade.tachiyomi.ui.recently_read.RecentlyReadController
import eu.kanade.tachiyomi.ui.recents.RecentsController
import eu.kanade.tachiyomi.ui.security.SecureActivityDelegate
import eu.kanade.tachiyomi.ui.setting.AboutController
import eu.kanade.tachiyomi.ui.setting.SettingsController
import eu.kanade.tachiyomi.ui.setting.SettingsMainController
import eu.kanade.tachiyomi.ui.source.browse.BrowseSourceController
import eu.kanade.tachiyomi.util.system.contextCompatDrawable
import eu.kanade.tachiyomi.util.system.getResourceColor
import eu.kanade.tachiyomi.util.system.hasSideNavBar
import eu.kanade.tachiyomi.util.system.iconicsDrawableMedium
import eu.kanade.tachiyomi.util.system.isBottomTappable
import eu.kanade.tachiyomi.util.system.launchUI
import eu.kanade.tachiyomi.util.view.doOnApplyWindowInsets
import eu.kanade.tachiyomi.util.view.getItemView
import eu.kanade.tachiyomi.util.view.snack
import eu.kanade.tachiyomi.util.view.updateLayoutParams
import eu.kanade.tachiyomi.util.view.updatePadding
import eu.kanade.tachiyomi.util.view.visibleIf
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.widget.EndAnimatorListener
import eu.kanade.tachiyomi.widget.preference.MangadexLoginDialog
import kotlinx.android.synthetic.main.main_activity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs

open class MainActivity : BaseActivity(), DownloadServiceListener, MangadexLoginDialog.Listener {

    protected lateinit var router: Router

    val source: Source by lazy { Injekt.get<SourceManager>().getMangadex() }

    var backArrow: Drawable? = null
        private set
    private var searchDrawable: Drawable? = null
    private var dismissDrawable: Drawable? = null
    private lateinit var gestureDetector: GestureDetectorCompat

    private var snackBar: Snackbar? = null
    private var extraViewForUndo: View? = null
    private var canDismissSnackBar = false

    private var animationSet: AnimatorSet? = null
    private val downloadManager: DownloadManager by injectLazy()
    private val hideBottomNav
        get() = router.backstackSize > 1 && router.backstack[1].controller() !is DialogController

    private val updateChecker by lazy { UpdateChecker.getUpdateChecker() }
    private val isUpdaterEnabled = BuildConfig.INCLUDE_UPDATER

    fun setUndoSnackBar(snackBar: Snackbar?, extraViewToCheck: View? = null) {
        this.snackBar = snackBar
        canDismissSnackBar = false
        launchUI {
            delay(1000)
            if (this@MainActivity.snackBar == snackBar) {
                canDismissSnackBar = true
            }
        }
        extraViewForUndo = extraViewToCheck
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Create a webview before extensions do or else they will break night mode theme
        // https://stackoverflow.com/questions/54191883
        XLog.d("Manually instantiating WebView to avoid night mode issue.")
        try {
            WebView(applicationContext)
        } catch (e: Exception) {
            XLog.e("Exception when creating webview at start", e)
        }
        super.onCreate(savedInstanceState)

        // Do not let the launcher create a new activity http://stackoverflow.com/questions/16283079
        if (!isTaskRoot && this !is SearchActivity) {
            finish()
            return
        }
        gestureDetector = GestureDetectorCompat(this, GestureListener())

        setContentView(R.layout.main_activity)

        setSupportActionBar(toolbar)

        backArrow = this.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_arrow_back)
        searchDrawable = this.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_search)
        dismissDrawable = this.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_close)

        var continueSwitchingTabs = false
        bottom_nav.getItemView(R.id.nav_library)?.setOnLongClickListener {
            if (!LibraryUpdateService.isRunning()) {
                LibraryUpdateService.start(this)
                main_content.snack(R.string.updating_library) {
                    anchorView = bottom_nav
                    setAction(R.string.cancel) {
                        LibraryUpdateService.stop(context)
                        Handler().post { NotificationReceiver.dismissNotification(context, Notifications.ID_LIBRARY_PROGRESS) }
                    }
                }
            }
            true
        }
        for (id in listOf(R.id.nav_recents, R.id.nav_browse)) {
            bottom_nav.getItemView(id)?.setOnLongClickListener {
                bottom_nav.selectedItemId = id
                bottom_nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller() as? BottomSheetController
                    controller?.toggleSheet()
                }
                true
            }
        }
        bottom_nav.setOnNavigationItemSelectedListener { item ->
            val id = item.itemId
            val currentController = router.backstack.lastOrNull()?.controller()
            if (!continueSwitchingTabs && currentController is BottomNavBarInterface) {
                if (!currentController.canChangeTabs {
                        continueSwitchingTabs = true
                        this@MainActivity.bottom_nav.selectedItemId = id
                    }
                ) return@setOnNavigationItemSelectedListener false
            }
            continueSwitchingTabs = false
            val currentRoot = router.backstack.firstOrNull()
            if (currentRoot?.tag()?.toIntOrNull() != id) {
                when (id) {
                    R.id.nav_library -> setRoot(LibraryController(), id)
                    R.id.nav_recents -> setRoot(RecentsController(), id)
                    else -> {
                        if (!source.isLogged() && !preferences.useCacheSource()) {
                            val dialog = MangadexLoginDialog(source, this)
                            dialog.showDialog(router)
                        } else {
                            setBrowseRoot()
                        }
                    }
                }
            } else if (currentRoot.tag()?.toIntOrNull() == id) {
                if (router.backstackSize == 1) {
                    val controller =
                        router.getControllerWithTag(id.toString()) as? BottomSheetController
                    controller?.toggleSheet()
                }
            }
            true
        }
        val container: ViewGroup = findViewById(R.id.controller_container)

        val content: ViewGroup = findViewById(R.id.main_content)
        DownloadService.addListener(this)
        content.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        container.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        supportActionBar?.setDisplayShowCustomEnabled(true)

        setNavBarColor(content.rootWindowInsets)
        content.doOnApplyWindowInsets { v, insets, _ ->
            setNavBarColor(insets)
            val contextView = window?.decorView?.findViewById<View>(R.id.action_mode_bar)
            contextView?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.systemWindowInsetLeft
                rightMargin = insets.systemWindowInsetRight
            }
            // Consume any horizontal insets and pad all content in. There's not much we can do
            // with horizontal insets
            v.updatePadding(
                left = insets.systemWindowInsetLeft, right = insets.systemWindowInsetRight
            )
            appbar.updatePadding(
                top = insets.systemWindowInsetTop
            )
            bottom_nav.updatePadding(bottom = insets.systemWindowInsetBottom)
        }

        router = Conductor.attachRouter(this, container, savedInstanceState)
        if (!router.hasRootController()) {
            // Set start screen
            if (!handleIntentAction(intent)) {
                bottom_nav.selectedItemId = R.id.nav_library
            }
        }

        toolbar.setNavigationOnClickListener {
            val rootSearchController = router.backstack.lastOrNull()?.controller()
            if (rootSearchController is RootSearchInterface) {
                rootSearchController.expandSearch()
            } else onBackPressed()
        }

        bottom_nav.visibleIf(!hideBottomNav)
        bottom_nav.alpha = if (hideBottomNav) 0f else 1f
        router.addChangeListener(object : ControllerChangeHandler.ControllerChangeListener {
            override fun onChangeStarted(
                to: Controller?,
                from: Controller?,
                isPush: Boolean,
                container: ViewGroup,
                handler: ControllerChangeHandler
            ) {

                syncActivityViewWithController(to, from, isPush)
                appbar.y = 0f
                snackBar?.dismiss()
            }

            override fun onChangeCompleted(
                to: Controller?,
                from: Controller?,
                isPush: Boolean,
                container: ViewGroup,
                handler: ControllerChangeHandler
            ) {
                appbar.y = 0f
                showDLQueueTutorial()
            }
        })

        syncActivityViewWithController(router.backstack.lastOrNull()?.controller())

        toolbar.navigationIcon = if (router.backstackSize > 1) backArrow else searchDrawable
        (router.backstack.lastOrNull()?.controller() as? BaseController)?.setTitle()
        (router.backstack.lastOrNull()?.controller() as? SettingsController)?.setTitle()

        if (savedInstanceState == null) {
            // Show changelog or similar manga enabling on install prompt
            // NOTE: we show the similar manga dialog first so it is behind the changelog
            if (Migrations.upgrade(preferences)) {
                if (!BuildConfig.DEBUG) ChangelogDialogController().showDialog(router)
            }
        }
    }

    fun setDismissIcon(enabled: Boolean) {
        toolbar.navigationIcon = if (enabled) dismissDrawable else searchDrawable
    }

    fun showNavigationArrow() {
        toolbar.navigationIcon = backArrow
    }

    private fun setNavBarColor(insets: WindowInsets?) {
        if (insets == null) return
        window.navigationBarColor = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            // basically if in landscape on a phone
            // For lollipop, draw opaque nav bar
            if (insets.hasSideNavBar())
                Color.BLACK
            else Color.argb(179, 0, 0, 0)
        }
        // if the android q+ device has gesture nav, transparent nav bar
        // this is here in case some crazy with a notch uses landscape
        else if (insets.isBottomTappable()) {
            getColor(android.R.color.transparent)
        }
        // if in landscape with 2/3 button mode, fully opaque nav bar
        else if (insets.hasSideNavBar()) {
            getResourceColor(R.attr.colorPrimaryVariant)
        }
        // if in portrait with 2/3 button mode, translucent nav bar
        else {
            ColorUtils.setAlphaComponent(
                getResourceColor(R.attr.colorPrimaryVariant), 179
            )
        }
    }

    override fun startSupportActionMode(callback: androidx.appcompat.view.ActionMode.Callback): androidx.appcompat.view.ActionMode? {
        window?.statusBarColor = getResourceColor(R.attr.colorPrimaryVariant)
        return super.startSupportActionMode(callback)
    }

    override fun onSupportActionModeFinished(mode: androidx.appcompat.view.ActionMode) {
        launchUI {
            val scale = Settings.Global.getFloat(
                contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1.0f
            )
            val duration = resources.getInteger(android.R.integer.config_mediumAnimTime) * scale
            delay(duration.toLong())
            delay(100)
            if (Color.alpha(window?.statusBarColor ?: Color.BLACK) >= 255) window?.statusBarColor =
                getResourceColor(
                    android.R.attr.statusBarColor
                )
        }
        super.onSupportActionModeFinished(mode)
    }

    override fun onResume() {
        super.onResume()
        getAppUpdates()
        DownloadService.callListeners()
        showDLQueueTutorial()
    }

    private fun showDLQueueTutorial() {
        if (router.backstackSize == 1 && this !is SearchActivity &&
            downloadManager.hasQueue() && !preferences.shownDownloadQueueTutorial().get()
        ) {
            val recentsItem = bottom_nav.getItemView(R.id.nav_recents) ?: return
            preferences.shownDownloadQueueTutorial().set(true)
            TapTargetView.showFor(
                this,
                TapTarget.forView(
                    recentsItem,
                    getString(R.string.manage_whats_downloading),
                    getString(R.string.visit_recents_for_download_queue)
                ).outerCircleColor(R.color.colorAccent).outerCircleAlpha(0.95f).titleTextSize(20)
                    .titleTextColor(android.R.color.white).descriptionTextSize(16)
                    .descriptionTextColor(R.color.md_white_1000_76)
                    .icon(contextCompatDrawable(R.drawable.ic_recent_read_32dp))
                    .targetCircleColor(android.R.color.white).targetRadius(45),
                object : TapTargetView.Listener() {
                    override fun onTargetClick(view: TapTargetView) {
                        super.onTargetClick(view)
                        bottom_nav.selectedItemId = R.id.nav_recents
                    }
                }
            )
        }
    }

    override fun onPause() {
        super.onPause()
        snackBar?.dismiss()
    }

    private fun getAppUpdates() {
        if (isUpdaterEnabled &&
            Date().time >= preferences.lastAppCheck().get() + TimeUnit.DAYS.toMillis(1)
        ) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val result = updateChecker.checkForUpdate()
                    preferences.lastAppCheck().set(Date().time)
                    if (result is UpdateResult.NewUpdate<*>) {
                        val body = result.release.info
                        val url = result.release.downloadLink

                        // Create confirmation window
                        withContext(Dispatchers.Main) {
                            AboutController.NewUpdateDialogController(body, url).showDialog(router)
                        }
                    }
                } catch (error: Exception) {
                    XLog.e(error)
                }
            }
        }
    }

    /**
     * Called when login dialog is closed, refreshes the adapter.
     *
     * @param source clicked item containing source information.
     */
    override fun siteLoginDialogClosed(source: Source) {
        if (source.isLogged()) {
            setBrowseRoot()
        }
    }

    override fun onNewIntent(intent: Intent) {
        if (!handleIntentAction(intent)) {
            super.onNewIntent(intent)
        }
    }

    fun setBrowseRoot() {
        setRoot(BrowseSourceController(), R.id.nav_browse)
    }

    protected open fun handleIntentAction(intent: Intent): Boolean {
        val notificationId = intent.getIntExtra("notificationId", -1)
        if (notificationId > -1) NotificationReceiver.dismissNotification(
            applicationContext, notificationId, intent.getIntExtra("groupId", 0)
        )
        when (intent.action) {
            SHORTCUT_LIBRARY -> bottom_nav.selectedItemId = R.id.nav_library
            SHORTCUT_RECENTLY_UPDATED, SHORTCUT_RECENTLY_READ -> {
                bottom_nav.selectedItemId = R.id.nav_recents
                val controller: Controller = when (intent.action) {
                    SHORTCUT_RECENTLY_UPDATED -> RecentChaptersController()
                    else -> RecentlyReadController()
                }
                router.pushController(controller.withFadeTransaction())
            }
            SHORTCUT_BROWSE -> bottom_nav.selectedItemId = R.id.nav_browse
            SHORTCUT_MANGA -> {
                val extras = intent.extras ?: return false
                if (router.backstack.isEmpty()) bottom_nav.selectedItemId = R.id.nav_library
                router.pushController(MangaDetailsController(extras).withFadeTransaction())
            }
            SHORTCUT_DOWNLOADS -> {
                bottom_nav.selectedItemId = R.id.nav_recents
                router.popToRoot()
                bottom_nav.post {
                    val controller =
                        router.backstack.firstOrNull()?.controller() as? RecentsController
                    controller?.showSheet()
                }
            }
            else -> return false
        }
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        DownloadService.removeListener(this)
        toolbar?.setNavigationOnClickListener(null)
    }

    override fun onBackPressed() {
        val sheetController = router.backstack.last().controller() as? BottomSheetController
        if (if (router.backstackSize == 1) !(sheetController?.handleSheetBack() ?: false)
            else !router.handleBack()
        ) {
            SecureActivityDelegate.locked = true
            super.onBackPressed()
        }
    }

    private fun setRoot(controller: Controller, id: Int) {
        router.setRoot(controller.withFadeTransaction().tag(id.toString()))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // Initialize option to open catalogue settings.
            R.id.action_settings -> {
                router.pushController(
                    (RouterTransaction.with(SettingsMainController())).popChangeHandler(
                        FadeChangeHandler()
                    ).pushChangeHandler(FadeChangeHandler())
                )
            }
            else -> return super.onOptionsItemSelected(item)
        }
        return super.onOptionsItemSelected(item)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            if (snackBar != null && snackBar!!.isShown) {
                val sRect = Rect()
                snackBar!!.view.getGlobalVisibleRect(sRect)

                val extRect: Rect? = if (extraViewForUndo != null) Rect() else null
                extraViewForUndo?.getGlobalVisibleRect(extRect)
                // This way the snackbar will only be dismissed if
                // the user clicks outside it.
                if (canDismissSnackBar && !sRect.contains(
                        ev.x.toInt(),
                        ev.y.toInt()
                    ) && (extRect == null || !extRect.contains(ev.x.toInt(), ev.y.toInt()))
                ) {
                    snackBar?.dismiss()
                    snackBar = null
                    extraViewForUndo = null
                }
            } else if (snackBar != null) {
                snackBar = null
                extraViewForUndo = null
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    protected open fun syncActivityViewWithController(
        to: Controller?,
        from: Controller? = null,
        isPush: Boolean = false
    ) {
        if (from is DialogController || to is DialogController) {
            return
        }
        val onRoot = router.backstackSize == 1
        if (onRoot) {
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
            toolbar.navigationIcon = searchDrawable
        } else {
            showNavigationArrow()
        }

        bottom_nav.visibility = if (!hideBottomNav) View.VISIBLE else bottom_nav.visibility
        animationSet?.cancel()
        animationSet = AnimatorSet()
        val alphaAnimation = ValueAnimator.ofFloat(
            bottom_nav.alpha, if (hideBottomNav) 0f else 1f
        )
        alphaAnimation.addUpdateListener { valueAnimator ->
            bottom_nav.alpha = valueAnimator.animatedValue as Float
        }
        alphaAnimation.addListener(
            EndAnimatorListener {
                bottom_nav.visibility = if (hideBottomNav) View.GONE else View.VISIBLE
            }
        )
        alphaAnimation.duration = 200
        alphaAnimation.startDelay = 50
        animationSet?.playTogether(alphaAnimation)
        animationSet?.start()
    }

    override fun downloadStatusChanged(downloading: Boolean) {
        val hasQueue = downloading || downloadManager.hasQueue()
        launchUI {
            if (hasQueue) {
                bottom_nav?.getOrCreateBadge(R.id.nav_recents)
                showDLQueueTutorial()
            } else {
                bottom_nav?.removeBadge(R.id.nav_recents)
            }
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            var result = false
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            if (abs(diffX) <= abs(diffY)) {
                val sheetRect = Rect()
                bottom_nav.getGlobalVisibleRect(sheetRect)
                if (sheetRect.contains(
                        e1.x.toInt(), e1.y.toInt()
                    ) && abs(diffY) > Companion.SWIPE_THRESHOLD && abs(velocityY) > Companion.SWIPE_VELOCITY_THRESHOLD && diffY <= 0
                ) {
                    val bottomSheetController =
                        router.backstack.lastOrNull()?.controller() as? BottomSheetController
                    bottomSheetController?.showSheet()
                }
                result = true
            }
            return result
        }
    }

    companion object {

        const val SWIPE_THRESHOLD = 100
        const val SWIPE_VELOCITY_THRESHOLD = 100

        // Shortcut actions
        const val SHORTCUT_LIBRARY = "eu.kanade.tachiyomi.SHOW_LIBRARY"
        const val SHORTCUT_RECENTLY_UPDATED = "eu.kanade.tachiyomi.SHOW_RECENTLY_UPDATED"
        const val SHORTCUT_RECENTLY_READ = "eu.kanade.tachiyomi.SHOW_RECENTLY_READ"
        const val SHORTCUT_BROWSE = "eu.kanade.tachiyomi.SHOW_BROWSE"
        const val SHORTCUT_DOWNLOADS = "eu.kanade.tachiyomi.SHOW_DOWNLOADS"
        const val SHORTCUT_MANGA = "eu.kanade.tachiyomi.SHOW_MANGA"

        const val INTENT_SEARCH = "neko.SEARCH"
        const val INTENT_SEARCH_QUERY = "query"
        const val INTENT_SEARCH_FILTER = "filter"
    }
}

interface BottomNavBarInterface {
    fun canChangeTabs(block: () -> Unit): Boolean
}

interface RootSearchInterface {
    fun expandSearch() {
        if (this is Controller) activity?.toolbar?.menu?.findItem(R.id.action_search)
            ?.expandActionView()
    }
}

interface BottomSheetController {
    fun showSheet()
    fun toggleSheet()
    fun handleSheetBack(): Boolean
    fun sheetIsExpanded(): Boolean
}
