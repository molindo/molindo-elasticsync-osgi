package at.molindo.elasticsync.scrutineer.internal;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import at.molindo.elasticsync.api.IdAndVersion;
import at.molindo.elasticsync.api.LogUtils;

public class IdAndVersionStreamVerifier {

    private static final Logger LOG = LogUtils.loggerForThisClass();

	public void verify(IdAndVersionStream primaryStream, IdAndVersionStream secondayStream, String type, IdAndVersionStreamVerifierListener idAndVersionStreamVerifierListener) {
        long numItems = 0;
        long begin = System.currentTimeMillis();

        try {

            parallelOpenStreamsAndWait(primaryStream, secondayStream, type);

            Iterator<IdAndVersion> primaryIterator = primaryStream.iterator();
            Iterator<IdAndVersion> secondaryIterator = secondayStream.iterator();

            IdAndVersion primaryItem = next(primaryIterator);
            IdAndVersion secondaryItem = next(secondaryIterator);

            while (primaryItem != null && secondaryItem != null) {
                if (primaryItem.equals(secondaryItem)) {
                    primaryItem = verifiedNext(primaryIterator, primaryItem);
                    secondaryItem = next(secondaryIterator);
                } else if (primaryItem.getId().equals(secondaryItem.getId())) {
                    idAndVersionStreamVerifierListener.onVersionMisMatch(type, primaryItem, secondaryItem);
                    primaryItem = verifiedNext(primaryIterator, primaryItem);
                    secondaryItem = next(secondaryIterator);
                } else if (primaryItem.compareTo(secondaryItem) < 0) {
                    idAndVersionStreamVerifierListener.onMissingInSecondaryStream(type, primaryItem);
                    primaryItem = verifiedNext(primaryIterator, primaryItem);
                } else {
                    idAndVersionStreamVerifierListener.onMissingInPrimaryStream(type, secondaryItem);
                    secondaryItem = next(secondaryIterator);
                }
                numItems++;
            }

            while (primaryItem != null) {
                idAndVersionStreamVerifierListener.onMissingInSecondaryStream(type, primaryItem);
                primaryItem = verifiedNext(primaryIterator, primaryItem);
                numItems++;
            }

            while (secondaryItem != null) {
                idAndVersionStreamVerifierListener.onMissingInPrimaryStream(type, secondaryItem);
                secondaryItem = next(secondaryIterator);
                numItems++;
            }
        } finally {
            closeWithoutThrowingException(primaryStream);
            closeWithoutThrowingException(secondayStream);
        }
        LogUtils.infoTimeTaken(LOG, begin, numItems, "Completed verification");
    }

	private void parallelOpenStreamsAndWait(IdAndVersionStream primaryStream, IdAndVersionStream secondaryStream, String type) {
		try {
			ExecutorService executorService = Executors.newFixedThreadPool(1, new NamedDaemonThreadFactory("StreamOpener"));
			Future<?> secondaryStreamFuture = executorService.submit(new OpenStreamRunner(secondaryStream, type));

			primaryStream.open(type);
			secondaryStreamFuture.get();

			executorService.shutdown();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to open one or both of the streams in parallel", e);
		}
	}

	private IdAndVersion verifiedNext(Iterator<IdAndVersion> iterator, IdAndVersion previous) {
		IdAndVersion next = next(iterator);
		if (next != null && previous.compareTo(next) >= 0) {
			throw new IllegalStateException("primary stream not ordered as expected: " + next + " followed "
					+ previous);
		} else {
			return next;
		}
	}

	private IdAndVersion next(Iterator<IdAndVersion> iterator) {
		if (iterator.hasNext()) {
			IdAndVersion next = iterator.next();
			if (next == null) {
				throw new IllegalStateException("stream must not return null");
			} else {
				return next;
			}
		} else {
			return null;
		}
	}

    private void closeWithoutThrowingException(IdAndVersionStream idAndVersionStream) {
        try {
            idAndVersionStream.close();
        } catch (Exception e) {
            LogUtils.warn(LOG, "Unable to close IdAndVersionStream", e);
        }
    }

    private static class OpenStreamRunner implements Runnable {
        private final IdAndVersionStream stream;
		private final String type;

        public OpenStreamRunner(IdAndVersionStream stream, String type) {
            this.stream = stream;
            this.type = type;
        }

        @Override
        public void run() {
        	stream.open(type);
        }
    }

    private static class NamedDaemonThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadCount = new AtomicInteger();

        public NamedDaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable command) {
            Thread thread = new Thread(command, namePrefix + "-" + threadCount.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
