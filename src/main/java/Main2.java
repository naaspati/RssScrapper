import java.io.UnsupportedEncodingException;

import org.slf4j.Logger;

import scrapper.Utils;

public class Main2 {

	public static void main(String[] args) throws UnsupportedEncodingException {
		Logger g = Utils.logger(Main2.class);
		
		g.info("anime");
		g.warn("anime");
		g.info("anime-2");
		g.warn("anime-2");
		g.debug("aanie");
		
		System.out.println(g.isDebugEnabled());
	}

}
