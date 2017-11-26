package scrapper;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.text.Text;

public class ProgressBox extends Text {
    private final SimpleIntegerProperty progress = new SimpleIntegerProperty();
    public ProgressBox(String url, int total) {
        textProperty().bind(Bindings.concat(url+"\n", progress, " | ", total));
    }
    public SimpleIntegerProperty progressProperty() {
        return progress;
    }
}
