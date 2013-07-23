package at.molindo.elasticsync.es090.internal;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import at.molindo.elasticsync.api.Document;
import at.molindo.elasticsync.api.ElasticsearchIndex;
import at.molindo.elasticsync.api.IdAndVersionFactory;
import at.molindo.elasticsync.api.LogUtils;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkItemResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.get.MultiGetItemResponse;
import org.elasticsearch.action.get.MultiGetRequestBuilder;
import org.elasticsearch.action.get.MultiGetResponse;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.VersionType;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.slf4j.Logger;

public class ElasticSearch090Index implements ElasticsearchIndex {

	private static final Logger LOG = LogUtils.loggerForThisClass();

	static final int BATCH_SIZE = 100000;
	static final int SCROLL_TIME_IN_MINUTES = 10;
	private long numItems = 0;

	private final Client client;
	private final String indexName;
	private final String query;
	private final IdAndVersionFactory idAndVersionFactory;

	public ElasticSearch090Index(Client client, String indexName, String query, IdAndVersionFactory idAndVersionFactory) {
		this.client = client;
		this.indexName = indexName;
		this.query = query;
		this.idAndVersionFactory = idAndVersionFactory;
	}

    @Override
	public IdAndVersionFactory getIdAndVersionFactory() {
		return idAndVersionFactory;
	}
	
	public void downloadTo(String type, OutputStream outputStream) {
		long begin = System.currentTimeMillis();
		doDownloadTo(type, outputStream);
		LogUtils.infoTimeTaken(LOG, begin, numItems, "Scan & Download completed");
	}

	private void doDownloadTo(String type, OutputStream outputStream) {
		try {
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream);
			consumeBatches(objectOutputStream, startScroll(type).getScrollId());
			objectOutputStream.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	void consumeBatches(ObjectOutputStream objectOutputStream, String initialScrollId) throws IOException {

		String scrollId = initialScrollId;
		SearchResponse batchSearchResponse = null;
		do {
			batchSearchResponse = client.prepareSearchScroll(scrollId).setScroll(TimeValue.timeValueMinutes(SCROLL_TIME_IN_MINUTES)).execute().actionGet();
			scrollId = batchSearchResponse.getScrollId();
		} while (writeSearchResponseToOutputStream(objectOutputStream, batchSearchResponse));
	}

    boolean writeSearchResponseToOutputStream(ObjectOutputStream objectOutputStream, SearchResponse searchResponse) throws IOException {
		SearchHit[] hits = searchResponse.getHits().hits();
		for (SearchHit hit : hits) {
        	idAndVersionFactory.create(hit.getId(), hit.getVersion()).writeToStream(objectOutputStream);
			numItems++;
		}
		return hits.length > 0;
	}

	QueryStringQueryBuilder createQuery() {
        return QueryBuilders.queryString(query).defaultOperator(QueryStringQueryBuilder.Operator.AND).defaultField("_all");
	}

	SearchResponse startScroll(String type) {
        SearchRequestBuilder searchRequestBuilder = client.prepareSearch(indexName);
		searchRequestBuilder.setSearchType(SearchType.SCAN);
        searchRequestBuilder.setTypes(type);
		searchRequestBuilder.setQuery(createQuery());
		searchRequestBuilder.setSize(BATCH_SIZE);
		searchRequestBuilder.setExplain(false);
		searchRequestBuilder.setNoFields();
		searchRequestBuilder.setVersion(true);
        searchRequestBuilder.setScroll(TimeValue.timeValueMinutes(SCROLL_TIME_IN_MINUTES));

		return searchRequestBuilder.execute().actionGet();
	}

	@Override
	public void load(List<Document> documents, final Runnable listener) {
		
		final Map<String, Map<String, Document>> map = new HashMap<>();
		
		MultiGetRequestBuilder builder = client.prepareMultiGet();
		for (Document doc : documents) {
			builder.add(indexName, doc.getType(), doc.getId());
			
			Map<String, Document> typeMap = map.get(doc.getType());
			if (typeMap == null) {
				map.put(doc.getType(), typeMap = new HashMap<>());
			}
			typeMap.put(doc.getId(), doc);
		}
		
		builder.execute(new ActionListener<MultiGetResponse>() {
			
			@Override
			public void onResponse(MultiGetResponse response) {

				Iterator<MultiGetItemResponse> iter = response.iterator();
				
				while(iter.hasNext()) {
					MultiGetItemResponse r = iter.next();
					if (!r.isFailed()) {
						GetResponse item = r.getResponse();

						Map<String, Document> typeMap = map.get(item.getType());
						if (typeMap == null) {
							LOG.warn("unexpected type returned: " + item.getType());
							continue;
						}
						
						Document doc = typeMap.get(item.getId());
						if (doc == null) {
							LOG.warn("unexpected id returned: " + item.getType() + "/" + item.getId());
							continue;
						}
						
						if (item.isExists()) {
							doc.setVersion(item.getVersion()).setSource(item.getSourceAsString());
						} else {
							doc.setDeleted();
						}
					}
				}
				
				if (listener != null) {
					listener.run();
				}
			}
			
			@Override
			public void onFailure(Throwable e) {
				LOG.error("loading documents failed", e);
				if (listener != null) {
					listener.run();
				}
			}
		});
		
	}

	@Override
	public void index(final List<Document> documents, final Runnable listener) {
		BulkRequestBuilder builder = client.prepareBulk();
		for (Document doc : documents) {
			if (doc.isDeleted()) {
				builder.add(client.prepareDelete(indexName, doc.getType(), doc.getId())
						.setVersionType(VersionType.EXTERNAL).setVersion(doc.getVersion() + 1));
			} else {
				builder.add(client.prepareIndex(indexName, doc.getType(), doc.getId())
						.setVersionType(VersionType.EXTERNAL).setVersion(doc.getVersion())
						.setSource(doc.getSource()));
			}
		}
		builder.execute(new ActionListener<BulkResponse>() {

			@Override
			public void onResponse(BulkResponse response) {
				
				BulkItemResponse[] itemResponses = response.getItems();
				for (int i = 0; i < itemResponses.length; i++) {
					BulkItemResponse itemResponse = itemResponses[i];
					Document document = documents.get(i);
					
					if (itemResponse.isFailed()) {
						LOG.warn("document {} failed {} ({})", document.isDeleted() ? "delete" : "index",  document, itemResponse.getFailureMessage());
					}
				}
				
				if (listener != null) {
					listener.run();
				}
			}

			@Override
			public void onFailure(Throwable e) {
				LOG.error("indexing documents failed", e);
				if (listener != null) {
					listener.run();
				}
			}
			
		});
		
	}

	
	@Override
	public void close() {
		if (client != null) {
			client.close();
		}
	}

}
