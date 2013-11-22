package us.kbase.workspace.database.mongo;

import java.util.Date;

import us.kbase.workspace.database.ObjectInformation;
import us.kbase.workspace.database.WorkspaceUser;

public class MongoObjectInfo implements ObjectInformation {
	
	final private long id;
	final private String name;
	final private String type;
	final private Date createdDate;
	final private int version;
	final private WorkspaceUser creator;
	final private long workspaceId;
	final private String workspaceName;
	final private String chksum;
	final private long size;
	
	MongoObjectInfo(final long id, final String name,
			final String typeString, final Date createdDate, final int version,
			final WorkspaceUser creator, final ResolvedMongoWSID workspaceid,
			final String chksum,
			final long size) {
		//no error checking for now, add if needed
		this.id = id;
		this.name = name;
		this.type = typeString;
		this.createdDate = createdDate;
		this.version = version;
		this.creator = creator;
		this.workspaceId = workspaceid.getID();
		this.workspaceName = workspaceid.getName();
		this.chksum = chksum;
		this.size = size;
		
	}

	@Override
	public long getObjectId() {
		return id;
	}

	@Override
	public String getObjectName() {
		return name;
	}

	@Override
	public String getTypeString() {
		return type;
	}

	@Override
	public Date getCreatedDate() {
		return createdDate;
	}

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public WorkspaceUser getCreator() {
		return creator;
	}

	@Override
	public long getWorkspaceId() {
		return workspaceId;
	}
	
	@Override
	public String getWorkspaceName() {
		return workspaceName;
	}

	@Override
	public String getCheckSum() {
		return chksum;
	}
	
	
	@Override
	public long getSize() {
		return size;
	}

	@Override
	public String toString() {
		return "MongoObjectInfo [id=" + id + ", name=" + name + ", type="
				+ type + ", createdDate=" + createdDate + ", version="
				+ version + ", creator=" + creator + ", workspaceId="
				+ workspaceId + ", workspaceName=" + workspaceName
				+ ", chksum=" + chksum + ", size=" + size + "]";
	}

}
