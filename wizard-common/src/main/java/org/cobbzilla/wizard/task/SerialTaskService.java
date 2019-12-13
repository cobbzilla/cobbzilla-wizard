package org.cobbzilla.wizard.task;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;

public abstract class SerialTaskService<T extends ITask<R>, R extends TaskResult> extends TaskServiceBase<T, R> {

    private final Map<String, T> tasksBySerial = new ConcurrentHashMap<>();

    protected abstract String getSerialIdentifier(T task);
    protected abstract T mergeTask(T task, T found);

    public T getTask (String serialId) { return tasksBySerial.get(serialId); }

    @Override synchronized public TaskId execute(T task) {

        final String id = getSerialIdentifier(task);
        final T found = getTask(id);
        if (found != null) {
            if (isRunning(id)) die("execute: another task is already running for " + id);
            task = mergeTask(task, found);
        }
        tasksBySerial.put(id, task);
        return super.execute(task);
    }

    public boolean isRunning(String id) { return isRunning(getTask(id)); }
    public boolean isRunning(T found) { return found != null && !found.getResult().isComplete(); }

}
