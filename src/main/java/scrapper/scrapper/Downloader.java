package scrapper.scrapper;

import static sam.console.ANSI.red;
import static sam.string.StringUtils.contains;
import static scrapper.Utils.CONNECT_TIMEOUT;
import static scrapper.Utils.READ_TIMEOUT;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Path;

import sam.nopkg.Junk;
import scrapper.ScrappingException;
import scrapper.Utils;

public interface Downloader {
	public boolean download(String url, Path dir) throws ScrappingException, IOException;	
}

class DefaultDownloader implements Downloader {

	protected static final String FILE_NAME_MARKER = "filename=\"";

	/*
	 *  FIXME 
	 *  - remove parameter String urlString
	 *  - copy download task
	 * 
	 */
	protected String getName(URLConnection c, URL url, String urlString) {
		String field = c.getHeaderField("Content-Disposition");

		String name = null;
		if(field != null) {
			field = contains(field, '\'') ? field.replace('\'', '"') : field;
			int start = field.indexOf(FILE_NAME_MARKER);
			if(start > 0) {
				int end = field.indexOf('\"', start + FILE_NAME_MARKER.length() + 1);
				if(end > 0)
					name = field.substring(start + FILE_NAME_MARKER.length(), end);
			}
		}

		if(name == null) {
			String ext = null;

			String mime = c.getContentType();
			if(mime != null) {
				if(contains(mime, ';'))
					mime = mime.substring(0, mime.indexOf(';'));

				ext = Utils.getExt(mime);
			}
			name = Utils.getFileName(url);

			if(ext != null)
				name = name.endsWith(ext) ? name : (name + ext);
		}

		if(name == null) {
			Utils.logger(getClass()).warn(red("extracting name failed: ")+urlString);
			return null;
		}

		int index = name.lastIndexOf('.');
		if(index > 0) {
			String ext = name.substring(index);
			if(contains(ext, ' '))
				return name + urlString.hashCode(); 

			return name.substring(0, index) + urlString.hashCode() + ext; 
		}
		return name + urlString.hashCode();
	}

	protected static URLConnection openConnection(URL url) throws IOException {
		URLConnection con = url.openConnection();
		con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36");
		con.setConnectTimeout(CONNECT_TIMEOUT);
		con.setReadTimeout(READ_TIMEOUT);
		con.connect();

		return con;
	}

	@Override
	public boolean download(String url, Path dir) throws ScrappingException, IOException {
		Junk.notYetImplemented();
		// TODO Auto-generated method stub
		return false;
	}

}

