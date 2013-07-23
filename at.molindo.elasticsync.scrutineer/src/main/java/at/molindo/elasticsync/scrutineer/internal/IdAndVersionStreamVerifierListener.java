package at.molindo.elasticsync.scrutineer.internal;

import at.molindo.elasticsync.api.IdAndVersion;

public interface IdAndVersionStreamVerifierListener extends AutoCloseable {

    void onMissingInSecondaryStream(String type, IdAndVersion idAndVersion);

    void onMissingInPrimaryStream(String type, IdAndVersion idAndVersion);

    void onVersionMisMatch(String type, IdAndVersion primaryItem, IdAndVersion secondaryItem);
}
