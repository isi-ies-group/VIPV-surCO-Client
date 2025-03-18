package es.upm.ies.surco.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import es.upm.ies.surco.api.ApiUserSessionState
import es.upm.ies.surco.AppMain
import es.upm.ies.surco.session_logging.LoggingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Worker to upload session files to the server.
 */
class SessionFilesUploadWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): ListenableWorker.Result {
        Log.i(TAG, "Uploading session files")
        return try {
            val files = LoggingSession.getSessionFiles()
            if (files.isEmpty()) {
                return ListenableWorker.Result.success(Data.Builder().putBoolean("no_files", true).build())
            }

            // Upload files using coroutines
            val allSuccessful = withContext(Dispatchers.IO) {
                files.all { file -> uploadFile(file) }
            }

            AppMain.Companion.instance.wasUploadedSuccessfully.postValue(allSuccessful)
            if (allSuccessful) {
                ListenableWorker.Result.success()
            } else {
                ListenableWorker.Result.retry()
            }
        } catch (_: Exception) {
            ListenableWorker.Result.retry() // Retry on failure
        }
    }

    suspend fun uploadFile(file: File): Boolean {
        val sessionState = AppMain.Companion.instance.apiUserSession.upload(file)
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
