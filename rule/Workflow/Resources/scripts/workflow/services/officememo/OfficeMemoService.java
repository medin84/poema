package workflow.services.officememo;

import com.exponentus.common.model.ACL;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.dataengine.jpa.ViewPage;
import com.exponentus.env.EnvConst;
import com.exponentus.exception.SecureException;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.ServiceDescriptor;
import com.exponentus.rest.ServiceMethod;
import com.exponentus.rest.outgoingpojo.Outcome;
import com.exponentus.runtimeobj.RegNum;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._SortParams;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;
import com.exponentus.user.IUser;
import workflow.dao.OfficeMemoDAO;
import workflow.model.OfficeMemo;
import workflow.model.constants.ApprovalStatusType;
import workflow.model.constants.ApprovalType;
import workflow.model.embedded.Approval;
import workflow.model.embedded.Block;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

@Path("office-memos")
public class OfficeMemoService extends RestProvider {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getView() {
        System.out.println(getWebFormData());

        _Session session = getSession();
        int pageSize = session.pageSize;
        _SortParams sortParams = getWebFormData().getSortParams(_SortParams.desc("regDate"));

        OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(session);
        ViewPage vp = officeMemoDAO.findViewPage(sortParams, getWebFormData().getPage(), pageSize);

        //
        _ActionBar actionBar = new _ActionBar(session);
        _Action newDocAction = new _Action("add_new", "", "new_incoming");
        newDocAction.setURL("new");
        actionBar.addAction(newDocAction);
        actionBar.addAction(new _Action("del_document", "", _ActionType.DELETE_DOCUMENT));

        Outcome outcome = new Outcome();
        outcome.setId("office-memos");
        outcome.setTitle("office_memo_documents");
        outcome.addPayload(actionBar);
        outcome.addPayload(vp);

        return Response.ok(outcome).build();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getById(@PathParam("id") String id) {
        _Session ses = getSession();
        OfficeMemo entity;

        boolean isNew = "new".equals(id);
        if (isNew) {
            entity = new OfficeMemo();
            Approval approval = new Approval();
            approval.setStatus(ApprovalStatusType.DRAFT);
            Block block = new Block();
            block.setStatus(ApprovalStatusType.DRAFT);
            block.setType(ApprovalType.SERIAL);
            List<Block> blocks = new ArrayList<Block>();
            blocks.add(block);
            approval.setBlocks(blocks);
            entity.setApproval(approval);
        } else {
            OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(ses);
            entity = officeMemoDAO.findById(id);
        }

        Outcome outcome = new Outcome();
        outcome.setId(id);
        outcome.addPayload(entity);
        outcome.addPayload(getActionBar(ses, entity));
        outcome.addPayload(EnvConst.FSID_FIELD_NAME, getWebFormData().getFormSesId());
        if (!isNew) {
            outcome.addPayload(new ACL(entity));
        }

        return Response.ok(outcome).build();
    }

    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("id") String id, OfficeMemo form) {
        _Session ses = getSession();

        _Validation validation = validate(form);
        if (validation.hasError()) {
            // return error
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(validation).build();
        }

        // ApprovalDAO approvalDAO = new ApprovalDAO(ses);
        OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(ses);
        OfficeMemo entity;

        boolean isNew = "new".equals(id);
        if (isNew) {
            entity = new OfficeMemo();
        } else {
            entity = officeMemoDAO.findById(id);
        }

        //
        entity.setAppliedRegDate(form.getAppliedRegDate());
        if (form.getApproval() != null) {
            // entity.setApproval(approvalDAO.findById(form.getApproval().getId()));
        } else {
            entity.setApproval(null);
        }
        entity.setTitle(form.getTitle());
        entity.setBody(form.getBody());
        entity.setAttachments(getActualAttachments(entity.getAttachments()));

        try {
            if (isNew) {
                RegNum rn = new RegNum();
                entity.setRegNumber(Integer.toString(rn.getRegNumber(entity.getDefaultFormName())));

                IUser<Long> user = ses.getUser();
                entity.addReaderEditor(user);
                entity = officeMemoDAO.add(entity);
            } else {
                entity = officeMemoDAO.update(entity);
            }

        } catch (SecureException | DAOException e) {
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
        }
        return Response.ok(entity).build();
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        _Session ses = getSession();
        OfficeMemoDAO dao = new OfficeMemoDAO(ses);
        OfficeMemo entity = dao.findById(id);
        if (entity != null) {
            try {
                dao.delete(entity);
            } catch (SecureException | DAOException e) {
                return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
            }
        }
        return Response.noContent().build();
    }

    /*
     * Get entity attachment or _thumbnail
     */
    @GET
    @Path("{id}/attachments/{attachId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachment(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        OfficeMemoDAO dao = new OfficeMemoDAO(getSession());
        OfficeMemo entity = dao.findById(id);

        return getAttachment(entity, attachId);
    }

    @DELETE
    @Path("{id}/attachments/{attachmentId}")
    public Response deleteAttachment(@PathParam("id") String id, @PathParam("attachmentId") String attachmentId) {
        return deleteAttachmentFromSessionFormAttachments(attachmentId);
    }

    /*
     *
     */
    private _ActionBar getActionBar(_Session session, OfficeMemo entity) {
        _ActionBar actionBar = new _ActionBar(session);

        actionBar.addAction(new _Action("close", "", _ActionType.CLOSE));
        actionBar.addAction(new _Action("save_close", "", _ActionType.SAVE_AND_CLOSE));
        if (!entity.isNew() && entity.isEditable()) {
            actionBar.addAction(new _Action("delete_document", "", _ActionType.DELETE_DOCUMENT));
        }

        return actionBar;
    }

    private _Validation validate(OfficeMemo entity) {
        _Validation ve = new _Validation();

        if (entity.getTitle() == null || entity.getTitle().isEmpty()) {
            ve.addError("title", "required", "field_is_empty");
        }

        return ve;
    }

    @Override
    public ServiceDescriptor updateDescription(ServiceDescriptor sd) {
        sd.setName(getClass().getName());
        ServiceMethod m = new ServiceMethod();
        m.setMethod(HttpMethod.GET);
        m.setURL("/" + sd.getAppName() + sd.getUrlMapping());
        sd.addMethod(m);
        return sd;
    }
}
