package love.shirokasoke.aggressivepatch.netty;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.Logger;

import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import love.shirokasoke.aggressivepatch.MyMod;

public final class ZstdCodecExecutors {

    public static final Logger LOG = MyMod.LOG;

    private static volatile EventExecutorGroup workers;
    private static volatile int lastThreads = 0;

    private ZstdCodecExecutors() {}

    public static EventExecutorGroup workers(int threads) {
        if (threads <= 0) {
            return null;
        }
        EventExecutorGroup group = workers;
        if (group == null || lastThreads != threads) {
            synchronized (ZstdCodecExecutors.class) {
                group = workers;
                if (group != null) {
                    group.shutdownGracefully();
                }

                group = new DefaultEventExecutorGroup(threads, new CodecThreadFactory());
                workers = group;
                lastThreads = threads;
                LOG.info("zstd codec offloaded to a dedicated pool of {} thread(s)", threads);

            }
        }
        return group;
    }

    public static void shutdown() {
        synchronized (ZstdCodecExecutors.class) {
            if (workers != null) {
                workers.shutdownGracefully(0, 2, TimeUnit.SECONDS);
                workers = null;
                LOG.info("zstd codec worker pool stopped");
            }
        }
    }

    private static final class CodecThreadFactory implements ThreadFactory {

        private final AtomicInteger id = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            final Thread t = new Thread(r, "Zstd-Codec-" + id.incrementAndGet());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

                @Override
                public void uncaughtException(Thread thread, Throwable e) {
                    LOG.error("Uncaught exception on {}", thread.getName(), e);
                }
            });
            return t;
        }
    }
}
