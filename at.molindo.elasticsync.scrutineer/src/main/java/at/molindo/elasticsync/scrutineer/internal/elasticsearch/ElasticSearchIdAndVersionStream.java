package at.molindo.elasticsync.scrutineer.internal.elasticsearch;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import at.molindo.elasticsync.api.ElasticsearchIndex;
import at.molindo.elasticsync.api.IdAndVersion;
import at.molindo.elasticsync.scrutineer.internal.IdAndVersionStream;


public class ElasticSearchIdAndVersionStream implements IdAndVersionStream {

    private static final String ELASTIC_SEARCH_UNSORTED_FILE = "elastic-search-unsorted.dat";

    private static final String ELASTIC_SEARCH_SORTED_FILE = "elastic-search-sorted.dat";

    private final ElasticsearchIndex index;
    private final ElasticSearchSorter elasticSearchSorter;
    private final IteratorFactory iteratorFactory;
    private final File unsortedFile;
    private final File sortedFile;

    public ElasticSearchIdAndVersionStream(ElasticsearchIndex index, ElasticSearchSorter elasticSearchSorter, IteratorFactory iteratorFactory, String workingDirectory) {
        this.index = index;
        this.elasticSearchSorter = elasticSearchSorter;
        this.iteratorFactory = iteratorFactory;
        unsortedFile = new File(workingDirectory, ELASTIC_SEARCH_UNSORTED_FILE);
        sortedFile = new File(workingDirectory, ELASTIC_SEARCH_SORTED_FILE);
    }

    @Override
    public void open(String type) {
        index.downloadTo(type, createUnsortedOutputStream());
        elasticSearchSorter.sort(createUnSortedInputStream(), createSortedOutputStream());
    }

    @Override
    public Iterator<IdAndVersion> iterator() {
        return iteratorFactory.forFile(sortedFile);
    }

    @Override
    public void close() {
        unsortedFile.delete();
        sortedFile.delete();
    }

    OutputStream createUnsortedOutputStream() {
        try {
            return new BufferedOutputStream(new FileOutputStream(unsortedFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    InputStream createUnSortedInputStream() {
        try {
            return new BufferedInputStream(new FileInputStream(unsortedFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    OutputStream createSortedOutputStream() {
        try {
            return new BufferedOutputStream(new FileOutputStream(sortedFile));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
