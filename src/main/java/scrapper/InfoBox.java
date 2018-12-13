package scrapper;





import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

public final class InfoBox extends BorderPane  implements Updatable {
    
    private final Text progressT = new Text();
    private final Text totalT = new Text();
    private final Text failedT = new Text();
    private double total;
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private final AtomicBoolean changed = new AtomicBoolean(false);

    public InfoBox (String name) {
        getStyleClass().add("info-box");
        Updatables.add(this);

        Text nameT = new Text(name);
        nameT.getStyleClass().add("name-text");
        totalT.getStyleClass().add("total-text");
        progressT.getStyleClass().add("progress-text");
        failedT.getStyleClass().add("failed-text");

        setLeft(nameT);
        setRight(new HBox(5, progressT, new Separator(Orientation.VERTICAL), totalT,new Separator(Orientation.VERTICAL), failedT));
        BorderPane.setMargin(nameT, new Insets(0, 10, 0, 0));
    }
    @Override
    public void update() {
        int p = progress.get();
        int f = failed.get();
        
        if(p+f == total)
            getStyleClass().add("completed");

        this.progressT.setText(String.valueOf(p));
        this.failedT.setText(String.valueOf(f));
    }
    @Override
    public boolean isChanged() {
        return changed.get();
    }
    @Override
    public void setChanged(boolean value) {
        changed.set(value);
    }
    public int getFailedCount() {
        return failed.get();
    }
    public void setTotal(int total) {
        this.total = total;
        totalT.setText(String.valueOf(total));
    }

    public void progress(boolean success) {
        if(success)
            progress.incrementAndGet();
        else
            failed.incrementAndGet();
            
        setChanged(true);
    }
    public void setCompleted(int completed) {
        progress.set(completed);
    }
}
