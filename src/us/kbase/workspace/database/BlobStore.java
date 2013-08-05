package us.kbase.workspace.database;

import us.kbase.workspace.database.exceptions.DBAuthorizationException;
import us.kbase.workspace.database.exceptions.WorkspaceBackendException;

public interface BlobStore {
	
	public void saveBlob(TypeData td) throws DBAuthorizationException,
		WorkspaceBackendException;
	
	public String getBlob(TypeData td) throws DBAuthorizationException;
	
	public String getExternalIdentifier(TypeData td); 
}
