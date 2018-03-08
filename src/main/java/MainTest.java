import java.io.IOException;
import java.nio.file.Paths;

import sam.myutils.fileutils.FilesUtils;
import scrapper.scrapper.DownloadTask2;

public class MainTest {
    public static void main(String[] args) throws IOException, ClassNotFoundException {
        // FilesUtils.writeObjectToFile(new DownloadTask2("urlString", true, "name", Paths.get("folder")), Paths.get("temp"));
        
        System.out.println((DownloadTask2)FilesUtils.readObjectFromFile(Paths.get("temp")));
        
    }
}
