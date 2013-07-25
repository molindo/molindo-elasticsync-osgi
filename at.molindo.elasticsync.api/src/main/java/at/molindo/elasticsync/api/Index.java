package at.molindo.elasticsync.api;

import java.util.ArrayList;
import java.util.List;

public class Index {

	public static final int DEFAULT_PORT = 9300;
	
	private List<String> _hosts;
	private String _indexName;
	private String _version;
	
	public Index(List<String> hosts, String indexName, String version) {
		if (hosts == null) {
			throw new NullPointerException("hosts");
		}
		if (indexName == null) {
			throw new NullPointerException("indexName");
		}
		if (version == null) {
			throw new NullPointerException("version");
		}
		
		if (hosts.isEmpty()) {
			throw new IllegalArgumentException("no hosts defined");
		}
		
		_hosts = new ArrayList<>(hosts);
		_indexName = indexName;
		_version = version;
	}
	
	public List<String> getHosts() {
		return new ArrayList<>(_hosts);
	}
	public String getIndexName() {
		return _indexName;
	}
	public String getVersion() {
		return _version;
	}
	
	
}
