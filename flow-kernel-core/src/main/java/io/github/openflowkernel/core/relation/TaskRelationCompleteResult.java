package io.github.openflowkernel.core.relation;

import io.github.openflowkernel.core.task.AbstractTask;

public class TaskRelationCompleteResult {
    private AbstractTask task;
    private Boolean allCompleted;
    private Boolean repeatedComplete;

    public AbstractTask getTask() {
        return task;
    }

    public void setTask(AbstractTask task) {
        this.task = task;
    }

    public Boolean getAllCompleted() {
        return allCompleted;
    }

    public void setAllCompleted(Boolean allCompleted) {
        this.allCompleted = allCompleted;
    }

    public Boolean getRepeatedComplete() {
        return repeatedComplete;
    }

    public void setRepeatedComplete(Boolean repeatedComplete) {
        this.repeatedComplete = repeatedComplete;
    }
}
