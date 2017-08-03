package us.kbase.workspace.listener;

import java.util.List;

import com.google.common.base.Optional;

import us.kbase.workspace.database.Permission;
import us.kbase.workspace.database.WorkspaceUser;

/** A listener for workspace events.
 * @author gaprice@lbl.gov
 * 
 * @see WorkspaceEventListenerFactory
 *
 */
public interface WorkspaceEventListener {
	
	/** Notification that a workspace was created.
	 * @param id the workspace ID.
	 */
	void createWorkspace(long id);
	
	/** Notification that a workspace was cloned.
	 * @param id the workspace ID.
	 */
	void cloneWorkspace(long id);

	/** Notification that a workspace's meta data was altered.
	 * @param id the id of the workspace.
	 */
	void setWorkspaceMetadata(long id);
	
	/** Notification that a workspace has been locked.
	 * @param id the id of the workspace.
	 */
	void lockWorkspace(long id);

	/** Notification that a workspace has been renamed.
	 * @param id the id of the workspace.
	 * @param newname the new name of the workspace.
	 */
	void renameWorkspace(long id, final String newname);

	/** Notification that the global permission for a workspace has been altered.
	 * @param id the id of the workspace.
	 * @param permission the new global permission.
	 */
	void setGlobalPermission(long id, Permission permission);

	/** Notification that the permissions for a workspace have been altered.
	 * @param id the id of the workspace.
	 * @param permission the new permission.
	 * @param users the users that have been assigned the new permission.
	 */
	void setPermissions(long id, Permission permission, List<WorkspaceUser> users);

	/** Notification that the workspace description has been set or altered.
	 * @param id the id of the workspace.
	 */
	void setWorkspaceDescription(long id);

	/** Notification that the owner of a workspace has been changed.
	 * @param id the id of the workspace.
	 * @param newUser the new owner of the workspace.
	 * @param newName the new name for the workspace, if any.
	 */
	void setWorkspaceOwner(long id, WorkspaceUser newUser, Optional<String> newName);

	/** Notification that a workspace has been deleted or undeleted.
	 * @param id the id of the workspace.
	 * @param delete true for a delete event, false for an undelete event.
	 */
	void setWorkspaceDeleted(long id, boolean delete);

	/** Notification that an object has been renamed.
	 * @param workspaceId the id of the workspace containing the object.
	 * @param objectId the id of the object.
	 * @param newName the object's new name.
	 */
	void renameObject(long workspaceId, long objectId, String newName);

	/** Notification that an object has been reverted.
	 * @param workspaceId the id of the workspace containing the object.
	 * @param objectId the id of the object.
	 * @param version the version of the reverted object.
	 * @param type the type of the object.
	 * @param isPublic true if the object is in a public workspace, false otherwise.
	 */
	void revertObject(long workspaceId, long objectId, int version, String type, boolean isPublic);

	/** Notification that an object was deleted or undeleted.
	 * @param workspaceId the workspace id.
	 * @param objectId the object id.
	 * @param delete true if the object was deleted, false if it was undeleted.
	 */
	void setObjectDeleted(long workspaceId, long objectId, boolean delete);

	/** Notification that a single version of an object was copied.
	 * @param workspaceId the workspace id of the new object.
	 * @param objectId the object id of the new object.
	 * @param version the version of the new object.
	 * @param type the type of the new object.
	 * @param isPublic true if the new object is in a public workspace, false otherwise.
	 */
	void copyObject(long workspaceId, long objectId, int version, String type, boolean isPublic);

	/** Notification that all the versions of an object was copied.
	 * @param workspaceId the workspace id of the new object.
	 * @param objectId the object id of the new object.
	 * @param latestVersion the latest version of the new object.
	 */
	void copyObject(long workspaceId, long objectId, int latestVersion);
	
	/** Notification that an object has been saved.
	 * @param workspaceId the id of the workspace containing the object.
	 * @param objectId the id of the object.
	 * @param version the version of the object.
	 * @param type the type of the object.
	 * @param isPublic true if the object is in a public workspace, false otherwise.
	 */
	void saveObject(long workspaceId, long objectId, int version, String type, boolean isPublic);
}
