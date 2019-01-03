package scrapper;

import javafx.geometry.Orientation;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
final class InfoBox extends VBox {
	static class CountBox extends HBox {
		final Text completed = new Text("11");
		final Text total = new Text("11");
		final Text failed = new Text("11");

		public CountBox() {
			super(5);
			total.getStyleClass().add("total-text");
			completed.getStyleClass().add("progress-text");
			failed.getStyleClass().add("failed-text");

			getChildren().addAll(completed, new Separator(Orientation.VERTICAL), failed,new Separator(Orientation.VERTICAL), total);
		}
	}

	final CountBox main = new CountBox();
	final CountBox sub = new CountBox();

	public InfoBox (String name) {
		getStyleClass().add("info-box");

		Text nameT = new Text(name);
		nameT.getStyleClass().add("name-text");
		main.getStyleClass().add("main");
		sub.getStyleClass().add("sub");

		getChildren().addAll(nameT, main, sub); 
	}
	public void setCompleted() {
		getStyleClass().add("completed");
	}
}