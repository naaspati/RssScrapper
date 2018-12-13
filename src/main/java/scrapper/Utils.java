package scrapper;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public interface Utils {

    public static String getFileName(URL url) {
        String s = url.getPath();
        int start = s.lastIndexOf('/');
        int end = s.length(); 
        if(start == end - 1) {
            start = s.lastIndexOf('/', start - 1);
            end--;
        }
        return s.substring(start + 1, end);
    }

    public static void openFile(File file) throws IOException {
        Runtime.getRuntime().exec("explorer \""+file.getName()+"\"", null, file.getParentFile());
    }
}
