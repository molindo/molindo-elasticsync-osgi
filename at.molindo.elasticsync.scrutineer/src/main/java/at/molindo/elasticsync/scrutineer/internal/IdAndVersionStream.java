package at.molindo.elasticsync.scrutineer.internal;

import java.util.Iterator;

import at.molindo.elasticsync.api.IdAndVersion;

public interface IdAndVersionStream {

    void open(String type);

    Iterator<IdAndVersion> iterator();

    void close();
}
