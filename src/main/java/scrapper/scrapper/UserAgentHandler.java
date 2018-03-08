package scrapper.scrapper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import com.jaunt.Document;
import com.jaunt.ResponseException;
import com.jaunt.UserAgent;

import scrapper.Config;

public final class UserAgentHandler extends ResourceHandler<UserAgent> {
    private static final BlockingQueue<UserAgent> resources = new LinkedBlockingQueue<>(Config.THREAD_COUNT);
    private static final AtomicInteger count = new AtomicInteger();

    UserAgentHandler() throws InterruptedException {
        super();
    }
    public void visit(String url) throws ResponseException {
        getValue().visit(url);
    }
    public Document doc() {
        return getValue().doc;
    }
    @Override
    protected UserAgent createNew() {
        return new UserAgent();
    }
    @Override
    protected BlockingQueue<UserAgent> resources() {
        return resources;
    }
    @Override
    protected AtomicInteger counter() {
        return count;
    }
}
