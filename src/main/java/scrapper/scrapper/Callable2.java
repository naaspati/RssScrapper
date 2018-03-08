package scrapper.scrapper;

import java.util.concurrent.Callable;

public class Callable2 {
    final ScrappingResult completed;
    final Callable<ScrappingResult> task;

    public Callable2(Callable<ScrappingResult> task) {
        this.completed = null;
        this.task = task;
    }
    public Callable2(ScrappingResult completed) {
        this.completed = completed;
        this.task = null;
    }
    public boolean isCompleted() {
        return completed != null;
    }
    public ScrappingResult getCompleted() {
        return completed;
    }
    public Callable<ScrappingResult> getTask() {
        return task;
    }
}