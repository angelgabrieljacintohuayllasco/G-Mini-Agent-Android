package com.gmini.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Programacion de una tarea. Formas validas:
 * `{cron, timezone}` o `{intervalSeconds}` (minimo 60). Sin programacion la
 * tarea corre una vez, de inmediato.
 */
@Serializable
public data class Schedule(
    val cron: String? = null,
    val timezone: String? = null,
    @SerialName("interval_seconds") val intervalSeconds: Long? = null,
)

/** Cuerpo de `POST /api/v1/tasks`. */
@Serializable
public data class TaskRequest(
    val prompt: String,
    val title: String? = null,
    val schedule: Schedule? = null,
    val notify: List<String> = emptyList(),
)

/** Respuesta `202` al crear una tarea. */
@Serializable
public data class TaskCreated(
    @SerialName("task_id") val taskId: String,
    val status: String = "queued",
)

/** Estado de una tarea en segundo plano. */
@Serializable
public data class TaskInfo(
    @SerialName("task_id") val taskId: String,
    val title: String? = null,
    val prompt: String? = null,
    val status: String = "queued",
    val result: String? = null,
    val error: String? = null,
    val runs: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("finished_at") val finishedAt: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
)

@Serializable
public data class TaskList(
    val items: List<TaskInfo> = emptyList(),
)
