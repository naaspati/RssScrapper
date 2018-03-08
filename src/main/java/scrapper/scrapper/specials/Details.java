package scrapper.scrapper.specials;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;

@Retention(RUNTIME)
public @interface Details {
    String value();
    String rss();
}
