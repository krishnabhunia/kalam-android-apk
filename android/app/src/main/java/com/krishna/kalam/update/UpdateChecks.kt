package com.krishna.kalam.update

import android.content.Context
import com.krishna.kalam.AppVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateCheckRecord(val at: Long, val message: String, val includeBeta: Boolean,
                             val installedVersion: String) {
    fun encode(): String = JSONObject().put("at", at).put("message", message)
        .put("includeBeta", includeBeta).put("installedVersion", installedVersion).toString()

    companion object {
        fun decode(value: String?, installedVersion: String): UpdateCheckRecord? = try {
            if (value.isNullOrEmpty()) null else JSONObject(value).let { row ->
                val at = row.getLong("at")
                val message = row.getString("message")
                val version = row.getString("installedVersion")
                if (at <= 0 || message.isBlank() || version != installedVersion) null
                else UpdateCheckRecord(at, message, row.getBoolean("includeBeta"), version)
            }
        } catch (_: Exception) { null }
    }
}

/** A background check's result remains readable after Android ends Kalam's process. */
object UpdateChecks {
    private const val FILE = "github-update-checks"
    private const val ENABLED = "daily-enabled"
    private const val LAST_CHECK = "last-check"
    private val mutableLastCheck = MutableStateFlow<UpdateCheckRecord?>(null)
    val lastCheck = mutableLastCheck.asStateFlow()

    private fun storage(context: Context) = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    fun dailyEnabled(context: Context): Boolean = storage(context).getBoolean(ENABLED, true)

    suspend fun setDailyEnabled(context: Context, enabled: Boolean) {
        withContext(Dispatchers.IO) {
            check(storage(context).edit().putBoolean(ENABLED, enabled).commit()) { "Could not save the daily update preference" }
        }
        DailyUpdateSchedule.apply(context)
    }

    suspend fun restoreHistory(context: Context) {
        mutableLastCheck.value = withContext(Dispatchers.IO) {
            UpdateCheckRecord.decode(storage(context).getString(LAST_CHECK, null), AppVersion.NAME)
        }
    }

    suspend fun check(context: Context, includeBeta: Boolean): Boolean {
        val successful = GitHubUpdates.check(includeBeta)
        val record = UpdateCheckRecord(System.currentTimeMillis(), GitHubUpdates.status.value.message,
            includeBeta, AppVersion.NAME)
        withContext(Dispatchers.IO) {
            check(storage(context).edit().putString(LAST_CHECK, record.encode()).commit()) { "Could not save the update check result" }
        }
        mutableLastCheck.value = record
        return successful
    }
}
