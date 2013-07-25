package at.molindo.elasticsync.launcher;

import java.util.List;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

// CHECKSTYLE:OFF This is the standard JCommander pattern
@Parameters(separators = "=")
public class ElasticsyncCommandLineOptions {
    @Parameter(names = "--source", description = "Elasticsearch source host(s) [host[:port]]", required = true)
    public List<String> sourceHosts;

    @Parameter(names = "--sourceVersion", description = "Elasticsearch library version for source cluster")
    public String sourceVersion = "0.20.*";
    
    @Parameter(names = "--target", description = "Elasticsearch target host(s) [host[:port]]", required = true)
    public List<String> targetHosts;
    
    @Parameter(names = "--targetVersion", description = "Elasticsearch library version for target cluster")
    public String targetVersion = "0.90.*";

    @Parameter(names = "--index", description = "Elasticsearch index name to Verify", required = true)
    public String indexName;
    
    @Parameter(names = "--type", description = "Elasticsearch type name to verify", required = true)
    public List<String> types;
    
    @Parameter(names = "--query", description = "Elasticsearch query")
    public String query = "*";
    
    @Parameter(names = "--update", description = "update target")
    public boolean update;

    @Parameter(names = { "--help", "-h" }, description = "Print usage", help = true)
    public boolean help;
    
}
// CHECKSTYLE:ON
