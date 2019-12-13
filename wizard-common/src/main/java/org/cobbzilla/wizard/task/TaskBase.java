package org.cobbzilla.wizard.task;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cobbzilla.wizard.validation.ConstraintViolationBean;
import org.cobbzilla.wizard.validation.MultiViolationException;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.reflect.ReflectionUtil.getFirstTypeParam;
import static org.cobbzilla.util.reflect.ReflectionUtil.instantiate;

@Accessors(chain=true)
public abstract class TaskBase<R extends TaskResult<E>, E extends TaskEvent> implements ITask<R> {

    @Getter @Setter protected TaskId taskId;
    @Getter @Setter protected R result = (R) instantiate(getFirstTypeParam(getClass(), TaskResult.class));

    @Override public void init() {
        taskId = new TaskId();
        taskId.initUUID();
        result.setTask(this);
    }

    @Override public void description(String messageKey, String target) { result.description(messageKey, target); }

    @Override public void addEvent(String messageKey) { result.add(newEvent(messageKey)); }

    protected E newEvent(String messageKey) {
        return (E) ((E) instantiate(getFirstTypeParam(result.getClass(), TaskEvent.class)))
                .setMessageKey(messageKey)
                .setTaskId(taskId.getUuid());
    }

    @Override public void error(String messageKey, Exception e) { result.error(newEvent(messageKey), e); }

    @Override public void error(String messageKey, String message) { error(messageKey, new Exception(message)); }

    @Override public void error(String messageKey, List<ConstraintViolationBean> errors) {
        error(messageKey, new MultiViolationException(errors));
    }

    private Thread thread;
    protected volatile boolean cancelled = false;
    public static final long TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    protected long getTerminationTimeout() { return TIMEOUT; }

    @Override public R call() throws Exception {
        thread = Thread.currentThread();
        return execute();
    }

    @Override public void cancel() {
        cancelled = true;
        if (thread != null) thread.interrupt();
    }

}
