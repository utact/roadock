package dev.utact.roadock

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future

object RefreshQueue {
    private val executor = Executors.newSingleThreadExecutor()
    private val writes = ConcurrentHashMap<String, ChecklistItem>()
    private val stateLock = Any()

    fun pending(projectId: String): ChecklistItem? = writes[projectId]

    private fun notifyState(context: Context) = synchronized(stateLock) {
        val preferences = context.getSharedPreferences("roadock", Context.MODE_PRIVATE)
        preferences.edit().putLong("sync-state", preferences.getLong("sync-state", 0) + 1).apply()
    }

    fun setHumanChecked(context: Context, project: Project, item: ChecklistItem, checked: Boolean,
        client: GitHubClient = GitHubClient(), complete: (Exception?) -> Unit = {}): Future<*>? {
        val app = context.applicationContext
        if (writes.putIfAbsent(project.id, item) != null) return null
        notifyState(app)
        return executor.submit {
            val store = RoadockStore(app)
            var failure: Exception? = null
            try {
                val items = client.setHumanChecked(project, store.token(project.id), item, checked) {
                    store.projects().find { it.id == project.id } == project
                }
                store.saveCache(project, ChecklistCache(items, System.currentTimeMillis()))
            } catch (error: Exception) {
                failure = error
                val message = if (error is java.io.IOException && error !is FetchException)
                    app.getString(R.string.error_write_unknown) else app.errorMessage(error)
                val cache = if (error is HumanWriteConflict) ChecklistCache(error.items, System.currentTimeMillis(), message)
                    else store.cache(project.id).copy(error = message)
                store.saveCache(project, cache)
            } finally {
                writes.remove(project.id)
                notifyState(app)
                RoadockWidget.updateAll(app)
                complete(failure)
            }
        }
    }

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
                        store.cache(project.id).copy(error = app.errorMessage(error))
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
