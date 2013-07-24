package at.molindo.elasticsync.api;

import java.io.OutputStream;
import java.util.List;

public interface ElasticsearchIndex extends AutoCloseable {

	IdAndVersionFactory getIdAndVersionFactory();
	
	void downloadTo(String type, OutputStream outputStream);

	void load(List<Document> documents, Runnable listener);
	
	void index(List<Document> documents, Runnable listener);
	
	void close();

}
