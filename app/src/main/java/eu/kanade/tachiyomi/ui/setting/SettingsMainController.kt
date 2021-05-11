package eu.kanade.tachiyomi.ui.setting

import android.content.Intent
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.net.toUri
import androidx.preference.PreferenceScreen
import com.bluelinelabs.conductor.Controller
import com.mikepenz.iconics.typeface.library.community.material.CommunityMaterial
import com.mikepenz.iconics.typeface.library.materialdesigndx.MaterialDesignDx
import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.util.system.iconicsDrawableMedium
import eu.kanade.tachiyomi.util.system.openInBrowser
import eu.kanade.tachiyomi.util.view.withFadeTransaction
import eu.kanade.tachiyomi.v5.job.V5MigrationJob

class SettingsMainController : SettingsController() {

    init {
        setHasOptionsMenu(true)
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) = with(screen) {
        titleRes = R.string.settings

        val size = 18

        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_warning)
            titleRes = R.string.v5_migration_service
            summary = context.resources.getString(R.string.v5_migration_desc)
            onClick {
                V5MigrationJob.doWorkNow()
            }
        }

        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_tune)
            titleRes = R.string.general
            onClick { navigateTo(SettingsGeneralController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_book)
            titleRes = R.string.library
            onClick { navigateTo(SettingsLibraryController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(CommunityMaterial.Icon.cmd_google_chrome)
            titleRes = R.string.site_specific_settings
            onClick { navigateTo(SettingsSiteController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_chrome_reader_mode)
            titleRes = R.string.reader
            onClick { navigateTo(SettingsReaderController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_file_download)
            titleRes = R.string.downloads
            onClick { navigateTo(SettingsDownloadController()) }
        }

        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_sync)
            titleRes = R.string.tracking
            onClick { navigateTo(SettingsTrackingController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_backup)
            titleRes = R.string.backup
            onClick { navigateTo(SettingsBackupController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_code)
            titleRes = R.string.advanced
            onClick { navigateTo(SettingsAdvancedController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_info)
            titleRes = R.string.about
            onClick { navigateTo(AboutController()) }
        }
        preference {
            iconDrawable = context.iconicsDrawableMedium(MaterialDesignDx.Icon.gmf_volunteer_activism)
            titleRes = R.string.dex_donations
            onClick {
                val intent = Intent(Intent.ACTION_VIEW, "https://mangadex.org/support".toUri())
                startActivity(intent)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.settings_main, menu)
        menu.findItem(R.id.action_bug_report).isVisible = BuildConfig.DEBUG
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_help -> activity?.openInBrowser(URL_HELP)
            R.id.action_bug_report -> activity?.openInBrowser(URL_BUG_REPORT)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun navigateTo(controller: Controller) {
        router.pushController(controller.withFadeTransaction())
    }

    private companion object {
        private const val URL_HELP = "https://tachiyomi.org/help/"
        private const val URL_BUG_REPORT = "https://github.com/CarlosEsco/Neko/issues"
    }
}
