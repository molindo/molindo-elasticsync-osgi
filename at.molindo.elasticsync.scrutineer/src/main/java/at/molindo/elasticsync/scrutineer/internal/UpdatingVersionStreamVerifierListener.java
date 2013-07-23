package at.molindo.elasticsync.scrutineer.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.molindo.elasticsync.api.Document;
import at.molindo.elasticsync.api.ElasticsearchIndex;
import at.molindo.elasticsync.api.IdAndVersion;

public class UpdatingVersionStreamVerifierListener implements IdAndVersionStreamVerifierListener {

	private static final Logger log = LoggerFactory
			.getLogger(UpdatingVersionStreamVerifierListener.class);
	
	private static final int MAX_IN_FLIGHT = 16;
	private static final int DEFAULT_BATCH_SIZE = 512;
	
	private ElasticsearchIndex _sourceIndex;
	private ElasticsearchIndex _targetIndex;

	// access must be synchronized
	private final List<Document> _load = new ArrayList<>(DEFAULT_BATCH_SIZE);
	private final List<Document> _index = new ArrayList<>(DEFAULT_BATCH_SIZE);
	
	private final AtomicInteger _loaded = new AtomicInteger();
	private final AtomicInteger _indexed = new AtomicInteger();
	private final AtomicInteger _errors = new AtomicInteger();
	
	private final Semaphore _sourceInFlight = new Semaphore(MAX_IN_FLIGHT);
	private final Semaphore _targetInFlight = new Semaphore(MAX_IN_FLIGHT);

	private static List<Document> add(List<Document> list, Document document) {
		List<Document> batch = null;
		synchronized (list) {
			list.add(document);
			if (list.size() == DEFAULT_BATCH_SIZE) {
				batch = flush(list);
			}
		}
		return batch;
	}

	private static List<Document> flush(List<Document> list) {
		List<Document> batch = null;
		synchronized (list) {
			if (!list.isEmpty()) {
				batch = new ArrayList<>(list);
				list.clear();
			}
		}
		return batch;
	}
	
	public UpdatingVersionStreamVerifierListener(ElasticsearchIndex sourceIndex, ElasticsearchIndex targetIndex) {
		if (sourceIndex == null) {
			throw new NullPointerException("sourceIndex");
		}
		if (targetIndex == null) {
			throw new NullPointerException("targetIndex");
		}
		_sourceIndex = sourceIndex;
		_targetIndex = targetIndex;
	}

	private void load(Document document) {
		List<Document> batch = add(_load, document);
		if (batch != null) {
			loadBatch(batch);
		}
	}

	private void index(Document document) {
		List<Document> batch = add(_index, document);
		if (batch != null) {
			indexBatch(batch);
		}
	}

	/**
	 * caller must synchronize
	 */
	private void loadBatch(final List<Document> load) {
			
			try {
				_sourceInFlight.acquire();
				_sourceIndex.load(load, new Runnable() {
					
					@Override
					public void run() {
						int loaded = 0;
						int errors = 0;
						try {
							for(Document doc : load) {
								if (doc.getSource() != null || doc.isDeleted()) {
									loaded++;
									index(doc);
								} else {
									errors++;
									log.warn("document without source ant not deleted: " + doc);
								}
							}
						} finally {
							_sourceInFlight.release();
							_loaded.addAndGet(loaded);
							_errors.addAndGet(errors);
						}
					}
				});
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
	}
	
	/**
	 * caller must synchronize
	 */
	private void indexBatch(final List<Document> index) {
		try {
			_targetInFlight.acquire();
			_targetIndex.index(index, new Runnable() {
				
				@Override
				public void run() {
					_targetInFlight.release();
					_indexed.addAndGet(index.size());
				}
			});
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void onMissingInSecondaryStream(String type, IdAndVersion idAndVersion) {
		load(new Document(type, idAndVersion.getId(), idAndVersion.getVersion()));
	}

	@Override
	public void onMissingInPrimaryStream(String type, IdAndVersion idAndVersion) {
		index(new Document(type, idAndVersion.getId(), idAndVersion.getVersion()).setDeleted());
	}

	@Override
	public void onVersionMisMatch(String type, IdAndVersion primaryItem,
			IdAndVersion secondaryItem) {
		onMissingInSecondaryStream(type, primaryItem);
	}

	@Override
	public void close() throws Exception {
		List<Document> batch = flush(_load);
		if (batch != null) {
			loadBatch(batch);
			waitForAll(_sourceInFlight);
		}
		
		batch = flush(_index);
		if (batch != null) {
			indexBatch(batch);
			waitForAll(_targetInFlight);
		}
		
		log.info("sync finished: loaded {}, indexed {}, errors {}", _loaded, _indexed, _errors);
	}

	private void waitForAll(Semaphore inFlight) {
		// wait for all to complete
		try {
			inFlight.acquire(MAX_IN_FLIGHT);
			inFlight.release(MAX_IN_FLIGHT);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
	
}
