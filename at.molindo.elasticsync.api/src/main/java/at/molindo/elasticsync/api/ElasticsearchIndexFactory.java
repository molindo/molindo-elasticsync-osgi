package at.molindo.elasticsync.api;


public interface ElasticsearchIndexFactory {

	public static final String ELASTICSEARCH_VERSION_PROPERTY = "elasticsearch.version";
	
	ElasticsearchIndex createElasticsearchIndex(Index index, String query,
			IdAndVersionFactory factory);
	
	String getVersion();
	
	boolean isVersionSupported(String version);
	
}
