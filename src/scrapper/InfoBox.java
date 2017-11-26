package scrapper;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public final class InfoBox extends BorderPane {
    private Text progressT = new Text(), totalT = new Text(), failedT = new Text();
    private double total; 
    private InvalidationListener invalidationListener;

    public InfoBox (String name) {
        getStyleClass().add("info-box");

        Text nameT = new Text(name);
        nameT.getStyleClass().add("name-text");
        totalT.getStyleClass().add("total-text");
        progressT.getStyleClass().add("progress-text");
        failedT.getStyleClass().add("failed-text");

        setLeft(nameT);
        setRight(new HBox(5, progressT, new Separator(Orientation.VERTICAL), totalT,new Separator(Orientation.VERTICAL), failedT));
        BorderPane.setMargin(nameT, new Insets(0, 10, 0, 0));
    }
    private AtomicInteger progress = new AtomicInteger(0);
    private AtomicInteger failed = new AtomicInteger(0);

    public int getFailedCount() {
        return failed.get();
    }
    public void setTotal(int total) {
        this.total = total;
        totalT.setText(String.valueOf(total));
    }

    public void progress(boolean success) {
        Platform.runLater(() -> {
            int p = success ? progress.incrementAndGet() : progress.get();
            int f = !success ? failed.incrementAndGet() : failed.get();

            if(p+f == total)
                getStyleClass().add("completed");

            this.progressT.setText(String.valueOf(p));
            this.failedT.setText(String.valueOf(f));
            if(invalidationListener != null)
                invalidationListener.invalidated(null);
        });
    }
    public void setListener(InvalidationListener func) {
        this.invalidationListener = func;
    }
}
