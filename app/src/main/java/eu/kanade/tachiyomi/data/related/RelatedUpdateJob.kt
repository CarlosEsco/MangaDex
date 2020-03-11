package eu.kanade.tachiyomi.data.related

import com.evernote.android.job.Job
import com.evernote.android.job.JobManager
import com.evernote.android.job.JobRequest
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import java.util.concurrent.TimeUnit
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class RelatedUpdateJob : Job() {

    override fun onRunJob(params: Params): Result {
        RelatedUpdateService.start(context)
        return Result.SUCCESS
    }

    companion object {
        const val TAG = "RelatedUpdate"

        fun setupTask() {

            // Get if we should have any download restrictions
            val preferences = Injekt.get<PreferencesHelper>()
            val restrictions = preferences.relatedUpdateRestriction()!!
            val acRestriction = "ac" in restrictions
            val wifiRestriction = if ("wifi" in restrictions)
                JobRequest.NetworkType.UNMETERED
            else
                JobRequest.NetworkType.CONNECTED

            // Build the download job
            JobRequest.Builder(TAG)
                .setPeriodic(TimeUnit.HOURS.toMillis(24), TimeUnit.MINUTES.toMillis(60))
                .setRequiredNetworkType(wifiRestriction)
                .setRequiresCharging(acRestriction)
                .setRequirementsEnforced(true)
                .setUpdateCurrent(true)
                .build()
                .schedule()
        }

        fun runTaskNow() {
            JobRequest.Builder(TAG)
                .startNow()
                .build()
                .schedule()
        }

        fun cancelTask() {
            JobManager.instance().cancelAllForTag(TAG)
        }
    }
}
