package at.molindo.elasticsync.scrutineer.internal;

import java.util.List;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.molindo.elasticsync.api.ElasticsearchIndexFactory;
import at.molindo.elasticsync.api.ElasticsearchIndex;
import at.molindo.elasticsync.api.ElasticsyncService;
import at.molindo.elasticsync.api.Index;

import com.google.common.base.Function;

public class ElasticsyncServiceImpl implements ElasticsyncService {

	private static final Logger log = LoggerFactory
			.getLogger(ElasticsyncServiceImpl.class);
	
	private ServiceTracker<ElasticsearchIndexFactory, ElasticsearchIndexFactory> _tracker;

	private BundleContext _context;
	
	public ElasticsyncServiceImpl(BundleContext context) {
		if (context == null) {
			throw new NullPointerException("context");
		}
		_context = context;
	}

	@Override
	public void verify(Index source, Index target, List<String> types, String query, boolean update) {

		ServiceTracker<ElasticsearchIndexFactory, ElasticsearchIndexFactory> sourceFactoryTracker = findFactoryTracker(source.getVersion());
		ServiceTracker<ElasticsearchIndexFactory, ElasticsearchIndexFactory> targetFactoryTracker = findFactoryTracker(target.getVersion());

		try {
			ElasticsearchIndexFactory sourceFactory = getService(sourceFactoryTracker, source.getVersion());
			ElasticsearchIndexFactory targetFactory = getService(targetFactoryTracker, target.getVersion());
			
			try (
				ElasticsearchIndex sourceIndex = sourceFactory.createElasticsearchIndex(source, query, StringIdAndVersionFactory.INSTANCE);
				ElasticsearchIndex targetIndex = targetFactory.createElasticsearchIndex(target, query, StringIdAndVersionFactory.INSTANCE);
			){
				log.info("starting verification");
				try (
					IdAndVersionStreamVerifierListener verifierListener = newVerifierListener(sourceIndex, targetIndex, update);
				){
					new Elasticsync().verify(sourceIndex, targetIndex, types, verifierListener);
				}
				log.info("verification finished");
			} catch (Exception e) {
				log.error("verification failed", e);
			}
		} finally {
			sourceFactoryTracker.close();
			targetFactoryTracker.close();
		}
	}

	private ElasticsearchIndexFactory getService(ServiceTracker<ElasticsearchIndexFactory, ElasticsearchIndexFactory> tracker, String version) {

		ElasticsearchIndexFactory factory = tracker.getService();
		if (factory == null) {
			log.info("waiting on factory for Elasticsearch version {}", version);
			try {
				factory = tracker.waitForService(30000);
				if (factory == null) {
					throw new RuntimeException("no factory available for Elasticsearch version " + version);
				}
			} catch (InterruptedException e) {
				throw new RuntimeException("waiting interrupted", e);
			}

		}
		
		return factory;
	}

	private IdAndVersionStreamVerifierListener newVerifierListener(ElasticsearchIndex sourceIndex, ElasticsearchIndex targetIndex, boolean update) {
		if (update) {
			return new UpdatingVersionStreamVerifierListener(sourceIndex, targetIndex);
		} else {
	        Function<Long, Object> formatter = PrintStreamOutputVersionStreamVerifierListener.DEFAULT_FORMATTER;
	        return new PrintStreamOutputVersionStreamVerifierListener(System.err, formatter);
		}
	}

	private ServiceTracker<ElasticsearchIndexFactory, ElasticsearchIndexFactory> findFactoryTracker(String version) {
		try {
			String filter = "(&(" + Constants.OBJECTCLASS + "="+ElasticsearchIndexFactory.class.getName()+")("+ElasticsearchIndexFactory.ELASTICSEARCH_VERSION_PROPERTY+"="+version+"))";
			ServiceTracker<ElasticsearchIndexFactory, ElasticsearchIndexFactory> tracker = new ServiceTracker<>(_context, _context.createFilter(filter), null);
			tracker.open();
			return tracker;
		} catch (InvalidSyntaxException e) {
			// TODO handle malformed user input
			throw new RuntimeException(e);
		}
	}
	
	public void close() {
		_tracker.close();
	}
}
