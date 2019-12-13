package org.cobbzilla.wizard.task;

import org.cobbzilla.wizard.validation.ConstraintViolationBean;

import java.util.List;
import java.util.concurrent.Callable;

public interface ITask<R> extends Callable<R> {

    public TaskId getTaskId();

    public R getResult();

    public R execute();

    public void init();

    public void description(String messageKey, String target);

    public void addEvent(String messageKey);

    public void error(String messageKey, Exception e);

    public void error(String messageKey, String message);

    public void error(String messageKey, List<ConstraintViolationBean> errors);

    public void cancel();

}
