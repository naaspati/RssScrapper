package scrapper;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractUpdatable implements Updatable {
    private final AtomicBoolean change = new AtomicBoolean(false);
    
    public AtomicBoolean getChange() {
        return change;
    }
    

    @Override
    public void update() {}

    @Override
    public boolean isChanged() {
        return change.get();
    }

    @Override
    public void setChanged(boolean value) {
        change.set(value);
    }
    public void setChanged() {
        change.set(true);
    }

}
