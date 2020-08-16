package org.cobbzilla.wizard.server;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.memory.ThreadLocalPoolProvider;
import org.glassfish.grizzly.threadpool.DefaultWorkerThread;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.cobbzilla.util.system.OutOfMemoryErrorUncaughtExceptionHandler.EXIT_ON_OOME;

public class ExitOnOutOfMemoryErrorThreadFactory implements ThreadFactory {

    private final ThreadPoolConfig config;
    private final AtomicInteger counter = new AtomicInteger(0);

    public ExitOnOutOfMemoryErrorThreadFactory(ThreadPoolConfig config) {
        this.config = config;
    }

    @Override public Thread newThread(Runnable r) {
        final MemoryManager mm = config.getMemoryManager();
        final ThreadLocalPoolProvider threadLocalPoolProvider;

        if (mm instanceof ThreadLocalPoolProvider) {
            threadLocalPoolProvider = (ThreadLocalPoolProvider) mm;
        } else {
            threadLocalPoolProvider = null;
        }

        final DefaultWorkerThread thread =
                new DefaultWorkerThread(Grizzly.DEFAULT_ATTRIBUTE_BUILDER,
                        config.getPoolName() + '(' + counter.incrementAndGet() + ')',
                        ((threadLocalPoolProvider != null) ? threadLocalPoolProvider.createThreadLocalPool() : null),
                        r);

        thread.setUncaughtExceptionHandler(EXIT_ON_OOME);
        thread.setPriority(config.getPriority());
        thread.setDaemon(config.isDaemon());
        final ClassLoader initial = config.getInitialClassLoader();
        if (initial != null) {
            thread.setContextClassLoader(initial);
        }

        return thread;
    }

}
