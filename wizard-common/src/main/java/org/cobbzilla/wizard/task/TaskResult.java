package org.cobbzilla.wizard.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ToString
public class TaskResult<E extends TaskEvent> {

    @JsonIgnore @Getter @Setter private TaskBase task;
    @Getter @Setter private String actionMessageKey;
    @Getter @Setter private String target;

    @Getter @Setter private String returnValue;

    @Getter @Setter private volatile boolean success = false;
    @Getter @Setter @JsonIgnore private volatile Exception exception;

    /** an error message for the task */
    public String getError () { return exception == null ? null : exception.toString(); }
    public void setError (String error) { exception = new Exception(error); }
    public boolean hasError () { return getError() != null; }

    private final List<E> events = new ArrayList<>();
    public List<E> getEvents () {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    public void add(E event) {
        synchronized (events) {
            this.events.add(event);
        }
    }

    public void addAll(Collection<E> e) {
        synchronized (events) {
            events.addAll(e);
        }
    }

    public void error(E event, Exception e) {
        add((E) event.setException(e.toString()));
        this.exception = e;
    }

    public void success (E event) {
        add((E) event.setSuccess(true));
        this.success = true;
    }

    public void description (String actionMessageKey, String target) {
        setActionMessageKey(actionMessageKey);
        setTarget(target);
    }

    @JsonIgnore public boolean isComplete () { return success || hasError(); }

    public void initRetry() { exception = null; success = false; }

    public String getErrorMessageKey() {
        return hasError() && !getEvents().isEmpty() ? getMostRecentEvent().getMessageKey() : null;
    }

    // so json won't complain
    public void setErrorMessageKey(String ignored) {}

    @JsonIgnore public E getMostRecentEvent() { return events.isEmpty() ? null : events.get(events.size() - 1); }

}
