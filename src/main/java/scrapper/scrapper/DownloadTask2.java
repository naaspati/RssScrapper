package scrapper.scrapper;

import java.io.Serializable;
import java.nio.file.Path;

public class DownloadTask2 implements Serializable {
    private static final long serialVersionUID = 1L;
    
    final String urlString;
    final boolean toParent;
    final String name;
    final String folder;

    public DownloadTask2(String urlString, boolean toParent, String name, Path folder) {
        this.urlString = urlString;
        this.toParent = toParent;
        this.name = name;
        this.folder = folder.toString();
    }
}
