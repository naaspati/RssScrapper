package scrapper.scrapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;




import scrapper.EnvConfig;

public class BufferHandler extends ResourceHandler<byte[]> {
    private static final LinkedBlockingQueue<byte[]> resources = new LinkedBlockingQueue<>(EnvConfig.THREAD_COUNT);
    private static final AtomicInteger count = new AtomicInteger();

   public BufferHandler() throws InterruptedException {
        super();
    }
    @Override
    protected byte[] createNew() {
        return new byte[8*1024];
    }
    @Override
    protected BlockingQueue<byte[]> resources() {
        return resources;
    }
    @Override
    protected AtomicInteger counter() {
        return count;
    }
    
    public long pipe(InputStream source, Path path) throws IOException {
        try(OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)){
          return  pipe(source, os);
        }
    }
    public long pipe(InputStream source, OutputStream sink) throws IOException {
            long nread = 0L;
            int n;
            while ((n = source.read(getValue())) > 0) {
                sink.write(getValue(), 0, n);
                nread += n;
            }
            return nread;
        }
}

