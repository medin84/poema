package helpdesk.services.demand;

import com.exponentus.common.model.ACL;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.dataengine.jpa.ViewPage;
import com.exponentus.env.EnvConst;
import com.exponentus.exception.SecureException;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.ServiceDescriptor;
import com.exponentus.rest.ServiceMethod;
import com.exponentus.rest.outgoingpojo.Outcome;
import com.exponentus.scripting._ColumnOptions;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._SortParams;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;
import com.exponentus.user.IUser;
import helpdesk.dao.DemandDAO;
import helpdesk.model.Demand;
import projects.dao.ProjectDAO;
import reference.dao.DemandTypeDAO;
import staff.dao.OrganizationDAO;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("demands")
public class DemandService extends RestProvider<Demand> {

    public DemandService() {
    }

    /* ===========================================
     * Get view
     * ---------------------------------------- */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getViewPage() {
        _Session session = getSession();
        int pageSize = session.pageSize;

        _SortParams sortParams = getRequestParameter().getSortParams(_SortParams.desc("regDate"));
        DemandDAO dao = new DemandDAO(session);
        ViewPage<Demand> vp = dao.findViewPage(sortParams, getRequestParameter().getPage(), pageSize);

        _ActionBar actionBar = new _ActionBar(session);
        _Action newDocAction = new _Action("add_new", "", "new_demand");
        actionBar.addAction(newDocAction);

        _ColumnOptions colOpts = new _ColumnOptions();
        colOpts.add(Demand.class, "regNumber", "text", "vw-reg-number");
        colOpts.add(Demand.class, "title", "text", "vw-title");
        colOpts.add(Demand.class, "hasAttachments", "attachment", "vw-icon");
        colOpts.add(Demand.class, "status", "text", "vw-status");
        colOpts.add(Demand.class, "statusDate", "date", "vw-date");
        colOpts.add(Demand.class, "demandType", "localizedName", "vw-demand-type");
        colOpts.add(Demand.class, "customer", "localizedName", "vw-customer");
        colOpts.add(Demand.class, "tags", "localizedName", "vw-tags");

        //
        Outcome outcome = new Outcome();
        outcome.setId("demands");
        outcome.setTitle("demands");
        outcome.addPayload(actionBar);
        outcome.addPayload(colOpts);
        outcome.addPayload(vp);

        return Response.ok(outcome).build();
    }

    /*
     * delete all selected
     */
//    @DELETE
//    @Produces(MediaType.APPLICATION_JSON)
//    public Response delete(_Session session, _WebFormData formData) {
//        DemandDAO dao = new DemandDAO(session);
//        for (String id : formData.getListOfValuesSilently("ids")) {
//            Demand m = dao.findById(id);
//            try {
//                dao.delete(m);
//            } catch (SecureException e) {
//                return Response.status(Response.Status.BAD_REQUEST).build();
//            }
//        }
//        return Response.ok().build();
//    }

    /* ===========================================
     * Get entity by id
     * ---------------------------------------- */
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getById(@PathParam("id") String id) {
        _Session session = getSession();
        Demand entity;

        boolean isNew = "new".equals(id);
        if (isNew) {
            entity = new Demand();
            entity.setAuthor(session.getUser());
            entity.setTitle("");
            entity.setBody("");
        } else {
            DemandDAO dao = new DemandDAO(session);
            entity = dao.findById(id);
        }

        Outcome outcome = new Outcome();
        outcome.setId(id);
        outcome.addPayload(entity);
        outcome.addPayload(getActionBar(session, entity));
        outcome.addPayload(EnvConst.FSID_FIELD_NAME, getRequestParameter().getFormSesId());
        if (!isNew) {
            outcome.addPayload(new ACL(entity));
        }

        return Response.ok(outcome).build();
    }

    /* ===========================================
     * Save entity
     * ---------------------------------------- */
    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("id") String id, Demand demandForm) {
        _Session session = getSession();

        _Validation validation = validate(demandForm);
        if (validation.hasError()) {
            // return error
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(validation).build();
        }

        ProjectDAO projectDAO = new ProjectDAO(session);
        OrganizationDAO organizationDAO = new OrganizationDAO(session);
        DemandTypeDAO demandTypeDAO = new DemandTypeDAO(session);
        DemandDAO demandDAO = new DemandDAO(session);
        Demand demand;

        boolean isNew = "new".equals(id);
        if (isNew) {
            demand = new Demand();
        } else {
            demand = demandDAO.findById(id);
        }

        demand.setTitle(demandForm.getTitle());
        demand.setCustomer(organizationDAO.findById(demandForm.getCustomer().getId()));
        demand.setDemandType(demandTypeDAO.findById(demandForm.getDemandType().getId()));
        demand.setProject(projectDAO.findById(demandForm.getProject().getId()));
        demand.setStatus(demandForm.getStatus());
        demand.setStatusDate(demandForm.getStatusDate());
        demand.setBody(demandForm.getBody());
        demand.setTags(demandForm.getTags());
        demand.setAttachments(getActualAttachments(demand.getAttachments()));

        try {
            if (isNew) {
                IUser<Long> user = session.getUser();
                demand.addReaderEditor(user);
                demand = demandDAO.add(demand);
            } else {
                demand = demandDAO.update(demand);
            }
        } catch (SecureException | DAOException e) {
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
        }

        Outcome outcome = new Outcome();
        outcome.setId(id);
        outcome.addPayload(demand);

        return Response.ok(outcome).build();
    }

    /* ===========================================
     * Delete entity
     * ---------------------------------------- */
    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        _Session ses = getSession();
        DemandDAO dao = new DemandDAO(ses);
        Demand entity = dao.findById(id);
        if (entity != null) {
            try {
                dao.delete(entity);
            } catch (SecureException e) {
                return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
            }
        }
        return Response.noContent().build();
    }

    /* ===========================================
     * Get entity attachment or _thumbnail
     * ---------------------------------------- */
    @GET
    @Path("{id}/attachments/{attachId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachment(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        DemandDAO demandDAO = new DemandDAO(getSession());
        Demand demand = demandDAO.findById(id);

        return getAttachment(demand, attachId);
    }

    /* ===========================================
     * Action bar
     * ---------------------------------------- */
    private _ActionBar getActionBar(_Session session, Demand entity) {
        _ActionBar actionBar = new _ActionBar(session);
        // if (incoming.isEditable()) {
        actionBar.addAction(new _Action("close", "", _ActionType.CLOSE));
        actionBar.addAction(new _Action("save_close", "", _ActionType.SAVE_AND_CLOSE));
        if (!entity.isNew() && entity.isEditable()) {
            actionBar.addAction(new _Action("delete_document", "", _ActionType.DELETE_DOCUMENT));
        }
        // }

        return actionBar;
    }

    /* ===========================================
     * Validate entity
     * ---------------------------------------- */
    private _Validation validate(Demand demandForm) {
        _Validation ve = new _Validation();

        if (demandForm.getTitle() == null || demandForm.getTitle().isEmpty()) {
            ve.addError("title", "required", "field_is_empty");
        }

        return ve;
    }

    @Override
    public ServiceDescriptor updateDescription(ServiceDescriptor sd) {
        sd.setName(getClass().getName());
        ServiceMethod m = new ServiceMethod();
        m.setMethod(HttpMethod.GET);
        m.setURL("/" + sd.getAppName() + sd.getUrlMapping() + "/demands");
        sd.addMethod(m);
        return sd;
    }
}