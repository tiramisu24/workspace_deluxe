package us.kbase.workspace.kbase;

import static us.kbase.common.utils.ServiceUtils.checkAddlArgs;
import static us.kbase.workspace.kbase.KBasePermissions.translatePermission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import us.kbase.common.service.Tuple10;
import us.kbase.common.service.Tuple6;
import us.kbase.common.service.Tuple9;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.UTCDateFormat;
import us.kbase.auth.AuthToken;
import us.kbase.workspace.ObjectData;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.database.ObjectMetaData;
import us.kbase.workspace.database.ObjectUserMetaData;
import us.kbase.workspace.database.WorkspaceMetaData;
import us.kbase.workspace.database.WorkspaceObjectData;
import us.kbase.workspace.database.WorkspaceUser;
import us.kbase.workspace.workspaces.Provenance;

/**
 * not thread safe
 * @author gaprice@lbl.gov
 *
 */
public class ArgUtils {
	
	//simple date formats aren't synchronized
	private final UTCDateFormat dateFormat = new UTCDateFormat();
	
	public ArgUtils() {}
	
	public static Provenance processProvenance(final String user,
			final List<ProvenanceAction> actions) {
		
		Provenance p = new Provenance(user);
		if (actions == null) {
			return p;
		}
		for (ProvenanceAction a: actions) {
			checkAddlArgs(a.getAdditionalProperties(), a.getClass());
			Provenance.ProvenanceAction pa = new Provenance.ProvenanceAction();
			if (a.getService() != null) {
				pa = pa.withServiceName(a.getService());
			}
			//TODO remainder of provenance actions
			//TODO parse provenance date 
		}
		
		return p;
	}
	
	public Tuple6<Long, String, String, String, String, String>
			wsMetaToTuple (final WorkspaceMetaData meta) {
		return new Tuple6<Long, String, String, String, String, String>()
				.withE1(meta.getId())
				.withE2(meta.getName())
				.withE3(meta.getOwner().getUser())
				.withE4(dateFormat.formatDate(meta.getModDate()))
				.withE5(translatePermission(meta.getUserPermission())) 
				.withE6(translatePermission(meta.isGloballyReadable()));
	}
	
	public List<Tuple9<Long, String, String, String, Long, String,
			Long, String, Long>>
			objMetaToTuple (final List<ObjectMetaData> meta) {
		
		//oh the humanity
		final List<Tuple9<Long, String, String, String, Long, String,
				Long, String, Long>> ret = 
			new ArrayList<Tuple9<Long, String, String, String, Long,
			String, Long, String, Long>>();
		
		for (ObjectMetaData m: meta) {
			ret.add(new Tuple9<Long, String, String, String, Long,
					String, Long, String, Long>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(dateFormat.formatDate(m.getCreatedDate()))
					.withE5(new Long(m.getVersion()))
					.withE6(m.getCreator().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize()));
		}
		return ret;
}
	
	public Tuple10<Long, String, String, String, Long, String,
			Long, String, Long, Map<String, String>>
			objUserMetaToTuple (final ObjectUserMetaData meta) {
		final List<ObjectUserMetaData> m = new ArrayList<ObjectUserMetaData>();
		m.add(meta);
		return objUserMetaToTuple(m).get(0);
	}
	
	public List<Tuple10<Long, String, String, String, Long, String,
			Long, String, Long, Map<String, String>>>
			objUserMetaToTuple (final List<ObjectUserMetaData> meta) {
		
		//oh the humanity
		final List<Tuple10<Long, String, String, String, Long, String,
			Long, String, Long, Map<String, String>>> ret = 
			new ArrayList<Tuple10<Long, String, String, String, Long,
			String, Long, String, Long, Map<String, String>>>();
		
		for (ObjectUserMetaData m: meta) {
			ret.add(new Tuple10<Long, String, String, String, Long,
					String, Long, String, Long, Map<String, String>>()
					.withE1(m.getObjectId())
					.withE2(m.getObjectName())
					.withE3(m.getTypeString())
					.withE4(dateFormat.formatDate(m.getCreatedDate()))
					.withE5(new Long(m.getVersion()))
					.withE6(m.getCreator().getUser())
					.withE7(m.getWorkspaceId())
					.withE8(m.getCheckSum())
					.withE9(m.getSize())
					.withE10(m.getUserMetaData()));
		}
		return ret;
	}
	
	public static WorkspaceUser getUser(final AuthToken token) {
		if (token == null) {
			return null;
		}
		return new WorkspaceUser(token.getUserName());
	}
	
	public List<ObjectData> translateObjectData(
			final List<WorkspaceObjectData> objects) {
		final List<ObjectData> ret = new ArrayList<ObjectData>();
		for (final WorkspaceObjectData o: objects) {
			ret.add(new ObjectData()
					.withData(new UObject(o.getData()))
					.withMeta(objUserMetaToTuple(o.getMeta())));
		}
		return ret;
	}
}
