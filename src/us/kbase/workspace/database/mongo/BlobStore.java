package us.kbase.workspace.database.mongo;

import com.fasterxml.jackson.databind.JsonNode;

import us.kbase.typedobj.core.MD5;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreAuthorizationException;
import us.kbase.workspace.database.mongo.exceptions.BlobStoreCommunicationException;
import us.kbase.workspace.database.mongo.exceptions.NoSuchBlobException;

public interface BlobStore {
	
	public void saveBlob(MD5 md5, JsonNode data) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	/** 
	 * Retrieve a blob from the blob store.
	 * @param md5 - the md5 of the blob to retrive.
	 * @param size - the size of the object. This is used as a hint to set the
	 * buffer size when reading the object for speed purposes, and does not need
	 * to be exact.
	 * @return the object stored in the blob store.
	 * @throws BlobStoreAuthorizationException if the connection to the blob
	 * storage system could not be authorized.
	 * @throws BlobStoreCommunicationException - if there was a communication
	 * error with the blob store.
	 * @throws NoSuchBlobException - if there is no blob corresponding to the
	 * md5.
	 */
	public JsonNode getBlob(MD5 md5, long size) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException, NoSuchBlobException;
	
	/**
	 * Do not call removeBlob when saveBlob could be run by other threads or
	 * applications. Doing so could result in an inconsistent state in the
	 * database.
	 * 
	 * @param md5
	 * @throws BlobStoreAuthorizationException
	 * @throws BlobStoreCommunicationException
	 */
	public void removeBlob(MD5 md5) throws BlobStoreAuthorizationException,
		BlobStoreCommunicationException;
	
	public String getExternalIdentifier(MD5 md5) throws
		BlobStoreCommunicationException, NoSuchBlobException;
	
	public String getStoreType();
}
