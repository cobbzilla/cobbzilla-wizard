package org.cobbzilla.wizard.task;

import lombok.AllArgsConstructor;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskServiceBase<T extends ITask<R>, R extends TaskResult> {

    protected final ExecutorService executor = Executors.newFixedThreadPool(5);
    protected final Map<String, T> taskMap = new ConcurrentHashMap<>();

    public TaskId execute(T task) {
        task.init();
        executor.submit(new TaskWrapper(task, this));
        taskMap.put(task.getTaskId().getUuid(), task);
        return task.getTaskId();
    }

    public R getResult(String taskId) {
        final T task = taskMap.get(taskId);
        return task == null ? null : task.getResult();
    }

    public T cancel(String taskId) {
        final T task = taskMap.remove(taskId);
        if (task != null) task.cancel();
        return task;

    }

    protected void completed(T task) {
        final R result = task.getResult();
        if (!result.isComplete()) {
            result.setError("task completed with neither success nor error!"); // should never happen
        }
        // todo: periodically clean out old tasks from taskMap
    }

    @AllArgsConstructor
    private class TaskWrapper implements Callable<R> {
        private T task;
        private TaskServiceBase<T, R> taskService;
        @Override public R call() throws Exception {
            try {
                return task.call();
            } finally {
                taskService.completed(task);
            }
        }

    }
}
