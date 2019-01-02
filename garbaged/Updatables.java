package scrapper;

import java.util.ResourceBundle;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;

import javafx.application.Platform;

public final class Updatables {
    private static final CopyOnWriteArraySet<Updatable> UPDATABLES = new CopyOnWriteArraySet<>();
    
    static {
        System.out.println("Updatables: initiated");
        int refreshRate = Integer.parseInt(ResourceBundle.getBundle("config").getString("refresh.rate"));
        
        new Timer(true)
        .scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(UPDATABLES.isEmpty())
                    return;

                if(UPDATABLES.stream().anyMatch(Updatable::isChanged))
                    Platform.runLater(() -> {
                        for (Updatable uu : UPDATABLES) {
                            if(uu.isChanged()) {
                                uu.update();
                                uu.setChanged(false);    
                            }
                        }
                    });
            }
        }, refreshRate, refreshRate);
    }

    public static synchronized void add(Updatable u) {
        UPDATABLES.add(u);
    }
    public static synchronized void remove(AbstractUpdatable u) {
        UPDATABLES.remove(u);
    }
    
    private Updatables() {}
}
