package at.molindo.elasticsync.scrutineer.internal.elasticsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;

import at.molindo.elasticsync.api.IdAndVersion;
import at.molindo.elasticsync.api.IdAndVersionFactory;

import com.fasterxml.sort.DataReader;
import com.fasterxml.sort.DataReaderFactory;

public class IdAndVersionDataReaderFactory extends DataReaderFactory<IdAndVersion> {

	private final IdAndVersionFactory factory;

	public IdAndVersionDataReaderFactory(IdAndVersionFactory factory) {
		this.factory = factory;
	}

    @Override
    public DataReader<IdAndVersion> constructReader(final InputStream inputStream) throws IOException {
        return new IdAndVersionDataReader(factory, new ObjectInputStream(inputStream));

    }
}
