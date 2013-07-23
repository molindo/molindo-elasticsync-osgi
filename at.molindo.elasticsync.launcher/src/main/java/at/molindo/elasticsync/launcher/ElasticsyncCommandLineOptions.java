package at.molindo.elasticsync.launcher;

import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

// CHECKSTYLE:OFF This is the standard JCommander pattern
@Parameters(separators = "=")
public class ElasticsyncCommandLineOptions {
    @Parameter(names = "--source", description = "ElasticSearch source host(s)", required = true)
    public List<String> sourceHosts;

    @Parameter(names = "--sourceVersion", description = "ElasticSearch library version for source cluster")
    public String sourceVersion = "0.20.*";
    
    @Parameter(names = "--target", description = "ElasticSearch target host(s)", required = true)
    public List<String> targetHosts;
    
    @Parameter(names = "--targetVersion", description = "ElasticSearch library version for target cluster")
    public String targetVersion = "0.90.*";

    @Parameter(names = "--index", description = "ElasticSearch index name to Verify", required = true)
    public String indexName;
    
    @Parameter(names = "--type", description = "ElasticSearch type name to verify", required = true)
    public List<String> types;
    
    @Parameter(names = "--query", description = "ElasticSearch query to create Secondary stream.  Not required to be ordered")
    public String query = "*";
    
    @Parameter(names = "--update", description = "update target")
    public boolean update;
    
}
// CHECKSTYLE:ON
