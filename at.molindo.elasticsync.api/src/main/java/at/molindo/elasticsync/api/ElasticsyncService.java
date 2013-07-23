package at.molindo.elasticsync.api;

import java.util.List;

public interface ElasticsyncService {

	public void verify(Index source, Index target, List<String> types, String query, boolean update);
	
}
