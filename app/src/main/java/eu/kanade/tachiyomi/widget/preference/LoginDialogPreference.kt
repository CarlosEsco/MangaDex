package eu.kanade.tachiyomi.widget.preference

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.customview.customView
import com.afollestad.materialdialogs.customview.getCustomView
import com.bluelinelabs.conductor.ControllerChangeHandler
import com.bluelinelabs.conductor.ControllerChangeType
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.databinding.PrefAccountLoginBinding
import eu.kanade.tachiyomi.ui.base.controller.DialogController
import eu.kanade.tachiyomi.util.view.visible
import eu.kanade.tachiyomi.widget.SimpleTextWatcher
import kotlinx.android.synthetic.main.pref_account_login.view.*
import kotlinx.android.synthetic.main.pref_site_login.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import rx.Subscription
import uy.kohesive.injekt.injectLazy

abstract class LoginDialogPreference(
    @StringRes private val usernameLabelRes: Int? = null,
    bundle: Bundle? = null
) :
    DialogController(bundle) {

    var v: View? = null
        private set

    protected lateinit var binding: PrefAccountLoginBinding
    val preferences: PreferencesHelper by injectLazy()

    val scope = CoroutineScope(Job() + Dispatchers.Main)

    var requestSubscription: Subscription? = null

    open var canLogout = false

    override fun onCreateDialog(savedViewState: Bundle?): Dialog {
        val dialog = MaterialDialog(activity!!).apply {
            customView(R.layout.pref_account_login, scrollable = false)
        }
        binding = PrefAccountLoginBinding.bind(dialog.getCustomView())

        onViewCreated(dialog.view)

        return dialog
    }

    fun onViewCreated(view: View) {
        v = view.apply {
            if (usernameLabelRes != null) {
                binding.usernameInput.hint = view.context.getString(usernameLabelRes)
            }

            binding.login.setOnClickListener {
                checkLogin()
            }

            binding.twoFactorCheck?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    two_factor_holder.visibility = View.VISIBLE
                } else {
                    two_factor_holder.visibility = View.GONE
                }
            }

            setCredentialsOnView(this)
        }
    }

    override fun onChangeStarted(handler: ControllerChangeHandler, type: ControllerChangeType) {
        super.onChangeStarted(handler, type)
        if (!type.isEnter) {
            onDialogClosed()
        }
    }

    open fun onDialogClosed() {
        scope.cancel()
        requestSubscription?.unsubscribe()
    }

    protected abstract fun checkLogin()

    protected abstract fun setCredentialsOnView(view: View)
}
