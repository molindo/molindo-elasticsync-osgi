package at.molindo.elasticsync.scrutineer.internal;

import java.io.File;
import java.util.List;

import org.apache.commons.lang.SystemUtils;
import org.slf4j.Logger;

import at.molindo.elasticsync.api.ElasticsearchIndex;
import at.molindo.elasticsync.api.IdAndVersion;
import at.molindo.elasticsync.api.IdAndVersionFactory;
import at.molindo.elasticsync.api.LogUtils;
import at.molindo.elasticsync.scrutineer.internal.elasticsearch.ElasticSearchIdAndVersionStream;
import at.molindo.elasticsync.scrutineer.internal.elasticsearch.ElasticSearchSorter;
import at.molindo.elasticsync.scrutineer.internal.elasticsearch.IdAndVersionDataReaderFactory;
import at.molindo.elasticsync.scrutineer.internal.elasticsearch.IdAndVersionDataWriterFactory;
import at.molindo.elasticsync.scrutineer.internal.elasticsearch.IteratorFactory;

import com.fasterxml.sort.DataReaderFactory;
import com.fasterxml.sort.DataWriterFactory;
import com.fasterxml.sort.SortConfig;
import com.fasterxml.sort.Sorter;
import com.fasterxml.sort.util.NaturalComparator;

public class Elasticsync {

	private static final Logger LOG = LogUtils.loggerForThisClass();
	
    private static final int DEFAULT_SORT_MEM = 256 * 1024 * 1024;
	
    public void verify(ElasticsearchIndex sourceIndex, ElasticsearchIndex targetIndex, List<String> types, IdAndVersionStreamVerifierListener verifierListener) {
        File sourceWorkDir = new File(SystemUtils.getJavaIoTmpDir(), "elasticsync-source");
        sourceWorkDir.mkdirs();
        
        File targetWorkDir = new File(SystemUtils.getJavaIoTmpDir(), "elasticsync-target");
        targetWorkDir.mkdirs();
        
        ElasticSearchIdAndVersionStream sourceStream = createElasticSearchIdAndVersionStream(sourceIndex, sourceWorkDir);
        ElasticSearchIdAndVersionStream targetStream = createElasticSearchIdAndVersionStream(targetIndex, targetWorkDir);
    
        IdAndVersionStreamVerifier verifier = new IdAndVersionStreamVerifier();
        for (String type : types) {
        	LOG.info("verifying " + type);
        	verifier.verify(sourceStream, targetStream, type, verifierListener);
        }
    }

    ElasticSearchIdAndVersionStream createElasticSearchIdAndVersionStream(ElasticsearchIndex index, File workDir) {
        return new ElasticSearchIdAndVersionStream(index, new ElasticSearchSorter(createSorter(index.getIdAndVersionFactory())), new IteratorFactory(index.getIdAndVersionFactory()), workDir.getAbsolutePath());
    }
    
    private Sorter<IdAndVersion> createSorter(IdAndVersionFactory idAndVersionFactory) {
        SortConfig sortConfig = new SortConfig().withMaxMemoryUsage(DEFAULT_SORT_MEM);
        DataReaderFactory<IdAndVersion> dataReaderFactory = new IdAndVersionDataReaderFactory(idAndVersionFactory);
        DataWriterFactory<IdAndVersion> dataWriterFactory = new IdAndVersionDataWriterFactory();
        return new Sorter<IdAndVersion>(sortConfig, dataReaderFactory, dataWriterFactory, new NaturalComparator<IdAndVersion>());
    }


}
