/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.architecture.blueprints.todoapp.data

import com.example.android.architecture.blueprints.todoapp.data.source.local.TasksDao
import com.example.android.architecture.blueprints.todoapp.data.source.network.NetworkDataSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Default implementation of [TasksRepository]. Single entry point for managing tasks' data.
 *
 * @param tasksNetworkDataSource - The network data source
 * @param tasksDao - The local data source
 * @param coroutineDispatcher - The dispatcher to be used for long running or complex operations,
 * such as network calls or mapping many models. This is important to avoid blocking the calling
 * thread.
 */
class DefaultTasksRepository(
    private val tasksNetworkDataSource: NetworkDataSource,
    private val tasksDao: TasksDao,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default
) : TasksRepository {

    override suspend fun createTask(title: String, description: String): Task {
        val task = Task(title = title, description = description)
        tasksDao.insertTask(task.toLocalModel())
        saveTasksToNetwork()
        return task
    }

    override suspend fun updateTask(taskId: String, title: String, description: String) {
        val task = getTask(taskId)?.copy(
            title = title,
            description = description
        ) ?: throw Exception("Task (id $taskId) not found")

        tasksDao.insertTask(task.toLocalModel())
        saveTasksToNetwork()
    }

    override suspend fun getTasks(forceUpdate: Boolean): List<Task> {
        if (forceUpdate) {
            loadTasksFromNetwork()
        }
        return withContext(coroutineDispatcher) {
            tasksDao.getTasks().toExternalModels()
        }
    }

    override suspend fun refreshTasks() {
        loadTasksFromNetwork()
    }

    override fun getTasksStream(): Flow<List<Task>> {
        return tasksDao.observeTasks().map { tasks ->
            withContext(coroutineDispatcher) {
                tasks.toExternalModels()
            }
        }
    }

    override suspend fun refreshTask(taskId: String) {
        loadTasksFromNetwork()
    }

    override fun getTaskStream(taskId: String): Flow<Task?> {
        return tasksDao.observeTaskById(taskId).map { it.toExternalModel() }
    }

    /**
     * Get a Task with the given ID. Will return null if the task cannot be found.
     *
     * @param taskId - The ID of the task
     * @param forceUpdate - true if the task should be updated from the network data source first.
     */
    override suspend fun getTask(taskId: String, forceUpdate: Boolean): Task? {
        if (forceUpdate) {
            loadTasksFromNetwork()
        }
        return tasksDao.getTaskById(taskId)?.toExternalModel()
    }

    override suspend fun completeTask(taskId: String) {
        tasksDao.updateCompleted(taskId = taskId, completed = true)
        saveTasksToNetwork()
    }

    override suspend fun activateTask(taskId: String) {
        tasksDao.updateCompleted(taskId = taskId, completed = false)
        saveTasksToNetwork()
    }

    override suspend fun clearCompletedTasks() {
        tasksDao.deleteCompletedTasks()
        saveTasksToNetwork()
    }

    override suspend fun deleteAllTasks() {
        tasksDao.deleteTasks()
        saveTasksToNetwork()
    }

    override suspend fun deleteTask(taskId: String) {
        tasksDao.deleteTaskById(taskId)
        saveTasksToNetwork()
    }

    private suspend fun loadTasksFromNetwork() {
        withContext(coroutineDispatcher) {
            val remoteTasks = tasksNetworkDataSource.loadTasks()

            // Real apps might want to do a proper sync, deleting, modifying or adding each task.
            tasksDao.deleteTasks()
            remoteTasks.forEach { task ->
                tasksDao.insertTask(task.toTaskEntity())
            }
        }
    }

    private suspend fun saveTasksToNetwork() {
        withContext(coroutineDispatcher) {
            // Real apps may want to use a proper sync strategy here to avoid data conflicts.
            val localTasks = tasksDao.getTasks()
            tasksNetworkDataSource.saveTasks(localTasks.toNetworkModels())
        }
    }
}
