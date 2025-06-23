package es.upm.ies.surco.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import es.upm.ies.surco.api.ApiUserSessionState
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.api.ApiActions
import es.upm.ies.surco.session_logging.LoggingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Worker to upload session files to the server.
 */
class SessionFilesUploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val appMain = appContext.applicationContext as AppMain

    override suspend fun doWork(): Result {
        Log.i(TAG, "Uploading session files")
        return try {
            // Check Privacy Policy is accepted before proceeding
            if (!ApiActions.PrivacyPolicy.isAccepted()) {
                Log.w(TAG, "Privacy Policy not accepted, cannot upload session files")
                return Result.failure(Data.Builder().putBoolean("privacy_policy_not_accepted", true).build())
            }

            // Ensure the user is logged in
            if (ApiActions.User.state.value != ApiUserSessionState.LOGGED_IN) {
                Log.w(TAG, "User is not logged in, cannot upload session files")
                return Result.failure(Data.Builder().putBoolean("not_logged_in", true).build())
            }
            val files = LoggingSession.getSessionFiles()
            if (files.isEmpty()) {
                return Result.success(Data.Builder().putBoolean("no_files", true).build())
            }

            // Upload files using coroutines
            val allSuccessful = withContext(Dispatchers.IO) {
                files.all { file -> uploadFile(file) }
            }

            appMain.wasUploadedSuccessfully.postValue(allSuccessful)
            if (allSuccessful) {
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.retry() // Retry on failure
        }
    }

    suspend fun uploadFile(file: File): Boolean {
        val sessionState = ApiActions.User.upload(file)
        // delete file if upload was successful
        val success = sessionState == ApiUserSessionState.LOGGED_IN
        if (success) {
            file.delete()
        }
        return success
    }

    companion object {
        val TAG: String = SessionFilesUploadWorker::class.java.simpleName
    }
}
