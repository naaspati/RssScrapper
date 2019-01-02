package scrapper;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public final class InfoBox extends BorderPane {
    
    private final Text progressT = new Text();
    private final Text totalT = new Text();
    private final Text failedT = new Text();
    private volatile double total;
    private volatile int progress, failed;

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
    public void update() {
        if(progress+failed == total)
            getStyleClass().add("completed");

        this.progressT.setText(Integer.toString(progress));
        this.failedT.setText(Integer.toString(failed));
    }
    public int getFailedCount() {
        return failed;
    }
    public void setTotal(int total) {
        this.total = total;
        totalT.setText(Integer.toString(total));
    }

    public void progress(boolean success) {
        if(success)
            progress++;
        else
            failed++;
    }
    public void setCompleted(int completed) {
        progress = completed;
    }
}
