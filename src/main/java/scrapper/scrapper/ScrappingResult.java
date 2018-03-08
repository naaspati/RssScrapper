package scrapper.scrapper;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ScrappingResult implements Serializable {
    private static final long serialVersionUID = 1845258022364933736L;

    public static final ScrappingResult SUCCESSFUL = new ScrappingResult("--SUCCESSFUL--SUCCESSFUL--", null, Paths.get("."));
    public static final ScrappingResult YOUTUBE = new ScrappingResult("--YOUTUBE--YOUTUBE--", null, Paths.get("."));

    private final String url;
    private final List<String> urlsList;
    private final String folderString;
    private transient Path folderPath;

    public ScrappingResult(String url, List<String> urlsList, Path folder) {
        this.url = url;
        this.urlsList = urlsList;
        this.folderPath = folder;
        this.folderString = folderPath.toString();
    }

    public String getUrl() { return url; }
    public List<String> getUrlsList() { return urlsList; }
    public Path getFolderPath() { 
        if(folderPath == null)
            folderPath = Paths.get(folderString);
        
        return folderPath;
    }

    public DownloadTask[] toDownloadTasks(){
        Path folder = getFolderPath();
        if(urlsList == null || urlsList.isEmpty())
            return new DownloadTask[0];

        if(urlsList.size() == 1)
            return new DownloadTask[] {new DownloadTask(urlsList.get(0), folder, true)};

        return urlsList.stream()
                .filter(u -> !DownloadTask.isDownloaded(folder, u))
                .map(u -> new DownloadTask(u, folder, false))
                .toArray(DownloadTask[]::new);
    }
}
