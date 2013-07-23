package at.molindo.elasticsync.api;


public interface ElasticSearchIndexFactory {

	public static final String ELASTICSEARCH_VERSION_PROPERTY = "elasticsearch.version";
	
	ElasticsearchIndex createDownloader(Index index, String query,
			IdAndVersionFactory factory);
	
	String getVersion();
	
	boolean isVersionSupported(String version);
}
