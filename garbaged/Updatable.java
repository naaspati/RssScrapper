package scrapper;

public interface Updatable {
    public void update();
    public boolean isChanged();
    public void setChanged(boolean value);
}
