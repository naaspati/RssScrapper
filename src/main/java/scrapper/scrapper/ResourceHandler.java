package scrapper.scrapper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import scrapper.Config;

abstract class ResourceHandler<E> implements AutoCloseable {
    
    private E value;
   protected ResourceHandler() throws InterruptedException {
        if(resources().isEmpty() && counter().get() < Config.THREAD_COUNT) {
            this.value = createNew();
            counter().incrementAndGet();
        } else 
            this.value = resources().take();
    }
    
    public E getValue() {
        return value;
    }
    @Override
    public void close()  {
        resources().offer(value);
        value = null;
    }
    
    protected abstract E createNew();
    protected abstract BlockingQueue<E> resources();
    protected abstract AtomicInteger counter();

}
