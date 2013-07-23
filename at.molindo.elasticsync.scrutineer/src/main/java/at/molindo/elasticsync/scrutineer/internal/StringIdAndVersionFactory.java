package at.molindo.elasticsync.scrutineer.internal;

import java.io.IOException;
import java.io.ObjectInputStream;

import at.molindo.elasticsync.api.IdAndVersionFactory;

public enum StringIdAndVersionFactory implements IdAndVersionFactory {

	INSTANCE;

	@Override
	public StringIdAndVersion create(Object id, long version) {
		return new StringIdAndVersion(toString(id), version);
	}

	private String toString(Object id) {
		if (id instanceof Number) {
			return ((Number) id).toString();
		} else {
			return id.toString();
		}
	}

	@Override
	public StringIdAndVersion readFromStream(ObjectInputStream inputStream) throws IOException {
		return new StringIdAndVersion(inputStream.readUTF(), inputStream.readLong());
	}
}