package dev.utact.roadock

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.io.IOException
import java.net.UnknownHostException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

object RefreshQueue {
    private val executor = Executors.newSingleThreadExecutor()

    fun submit(context: Context, projectId: String? = null, complete: () -> Unit = {}): Future<*> {
        val app = context.applicationContext
        return executor.submit {
            try {
                val store = RoadockStore(app)
                store.projects().filter { projectId == null || it.id == projectId }.forEach { project ->
                    if (Thread.currentThread().isInterrupted) return@forEach
                    val cache = try {
                        ChecklistCache(GitHubClient().fetch(project, store.token(project.id)), System.currentTimeMillis())
                    } catch (error: Exception) {
                        val message = when (error) {
                            is UnknownHostException, is SocketTimeoutException -> "연결을 확인한 뒤 다시 새로고침해 주세요"
                            is IOException, is IllegalStateException -> error.message ?: "새로고침에 실패했습니다"
                            else -> "새로고침에 실패했습니다. 잠시 후 다시 시도해 주세요"
                        }
                        store.cache(project.id).copy(error = message)
                    }
                    store.saveCache(project, cache)
                    RoadockWidget.updateAll(app)
                }
            } finally {
                complete()
            }
        }
    }
}

class RefreshJobService : JobService() {
    private val jobs = ConcurrentHashMap<Int, Future<*>>()

    override fun onStartJob(params: JobParameters): Boolean = synchronized(jobs) {
        jobs[params.jobId] = RefreshQueue.submit(this, params.extras.getString("projectId")) {
            synchronized(jobs) {
                if (jobs.remove(params.jobId) != null) jobFinished(params, false)
            }
        }
        true
    }

    override fun onStopJob(params: JobParameters): Boolean = synchronized(jobs) {
        jobs.remove(params.jobId)?.cancel(true)
        true
    }

    companion object {
        fun schedulePeriodic(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (RoadockStore(context).projects().isEmpty()) {
                scheduler.cancel(1)
                return
            }
            if (scheduler.getPendingJob(1) != null) return
            scheduler.schedule(JobInfo.Builder(1, ComponentName(context, RefreshJobService::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build())
        }

    }
}
