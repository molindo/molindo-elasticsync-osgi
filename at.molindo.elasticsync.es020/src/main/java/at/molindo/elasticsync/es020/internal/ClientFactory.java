package at.molindo.elasticsync.es020.internal;


import java.util.List;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

public class ClientFactory {

	private ClientFactory() {}
	
	public static TransportClient newTransportClient(List<String> hosts) {
		return newTransportClient(hosts, createSettings());
	}
	
    public static TransportClient newTransportClient(List<String> hosts, Settings settings) {
    	TransportClient client = new TransportClient(settings);
    	for (String host : hosts) {
    		String[] parts = host.split(":");
    		
    		if (parts.length > 2) {
    			throw new IllegalArgumentException("illegal host definition: " + host);
    		}
    		
    		String hostPart = parts[0];
    		int portPart =  parts.length == 1 ? 9200 : Integer.parseInt(parts[1]);
    		
    		client.addTransportAddress(new InetSocketTransportAddress(hostPart, portPart));
    	}
    	
    	return client;
	}

    private static Settings createSettings() {
        return ImmutableSettings.settingsBuilder()
        		.classLoader(Settings.class.getClassLoader())
                .put("client.transport.ignore_cluster_name", true)
                .put("client.transport.ping_timeout", "10s")
                .put("client.transport.nodes_sampler_interval", "10s")
//                
//                .put("transport.tcp.port", 9399)
//                .put("network.publish_host", "localhost")
//                
                .build();
    }

}
