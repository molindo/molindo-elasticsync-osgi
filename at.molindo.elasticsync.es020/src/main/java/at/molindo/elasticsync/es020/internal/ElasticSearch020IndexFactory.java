package at.molindo.elasticsync.es020.internal;

import at.molindo.elasticsync.api.ElasticsearchIndex;
import at.molindo.elasticsync.api.ElasticSearchIndexFactory;
import at.molindo.elasticsync.api.IdAndVersionFactory;
import at.molindo.elasticsync.api.Index;

public class ElasticSearch020IndexFactory implements
		ElasticSearchIndexFactory {

	@Override
	public ElasticsearchIndex createDownloader(Index index, String query,
			IdAndVersionFactory factory) {

		if (!isVersionSupported(index.getVersion())) {
			throw new IllegalArgumentException(
					"factory not suited for version " + index.getVersion());
		}

		return new Elasticsearch020Index(
				ClientFactory.newTransportClient(index.getHosts()),
				index.getIndexName(), query, factory);
	}

	public String getVersion() {
		return org.elasticsearch.Version.CURRENT.toString();
	}

	@Override
	public boolean isVersionSupported(String version) {
		// TODO support osgi filter syntax
		if (version.endsWith("*")) {
			version = version.substring(0, version.lastIndexOf('*'));
		}
		return getVersion().startsWith(version);
	}
}
