package at.molindo.elasticsync.api;

public class Document {
	private final String _type;
	private final String _id;

	private Long _version;
	private String _source;
	private boolean _deleted;

	public Document(String type, String id, long version) {
		if (type == null) {
			throw new NullPointerException("type");
		}
		if (id == null) {
			throw new NullPointerException("id");
		}
		_type = type;
		_id = id;
		_version = version;
	}

	public Document setVersion(long version) {
		_version = version;
		return this;
	}
	
	public Document setDeleted() {
		_source = null;
		_deleted = true;
		return this;
	}

	public Document setSource(String source) {
		_source = source;
		_deleted = false;
		return this;
	}

	public String getType() {
		return _type;
	}

	public String getId() {
		return _id;
	}

	public Long getVersion() {
		return _version;
	}

	public String getSource() {
		return _source;
	}

	public boolean isDeleted() {
		return _deleted;
	}

	@Override
	public String toString() {
		return "Document(" + _type + "/" + _id + "){version=" + _version
				+ ", source=" + (_source != null) + ", deleted=" + _deleted
				+ "}";
	}

	
}
