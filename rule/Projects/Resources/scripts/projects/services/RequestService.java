package projects.services;

import com.exponentus.common.model.ACL;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.env.EnvConst;
import com.exponentus.exception.SecureException;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.ServiceDescriptor;
import com.exponentus.rest.ServiceMethod;
import com.exponentus.rest.outgoingpojo.Outcome;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;
import com.exponentus.user.SuperUser;
import com.exponentus.util.TimeUtil;
import projects.dao.RequestDAO;
import projects.dao.TaskDAO;
import projects.model.Request;
import projects.model.Task;
import projects.model.constants.ResolutionType;
import projects.model.constants.TaskStatusType;
import projects.other.Messages;
import reference.dao.RequestTypeDAO;
import reference.model.RequestType;
import staff.dao.EmployeeDAO;
import staff.model.Employee;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Path("requests")
public class RequestService extends RestProvider {

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getById(@PathParam("id") String id) {
        Outcome outcome = new Outcome();
        outcome.setId(id);

        _Session session = getSession();

        try {
            boolean isNew = "new".equals(id);
            RequestDAO requestDAO = new RequestDAO(session);
            Request request;
            EmployeeDAO empDao = new EmployeeDAO(session);

            if (!isNew) {
                request = requestDAO.findById(id);
                if (request == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }

                outcome.addPayload(new ACL(request));
            } else {
                request = new Request();
                request.setAuthor(session.getUser());

                String taskId = getWebFormData().getValueSilently("task");
                TaskDAO taskDAO = new TaskDAO(session);
                Task task = taskDAO.findById(taskId);
                request.setTask(task);
            }

            Map<Long, Employee> emps = new HashMap<>();
            emps.put(request.getAuthorId(), empDao.findByUserId(request.getAuthorId()));

            outcome.addPayload(EnvConst.FSID_FIELD_NAME, getWebFormData().getFormSesId());
            outcome.addPayload(getActionBar(session, request));
            outcome.addPayload(request);
            outcome.addPayload("task", request.getTask());
            outcome.addPayload("employees", emps);

            return Response.ok(outcome).build();
        } catch (DAOException e) {
            logError(e);
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(outcome.setMessage(e.toString())).build();
        }
    }

    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("id") String id, Request requestForm) {

        if (requestForm.getTask() == null || requestForm.getRequestType() == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("task or requestType empty").build();
        }

        return addRequest(getSession(), requestForm);
    }

    private Response addRequest(_Session session, Request requestForm) {
        Outcome outcome = new Outcome();

        try {
            RequestDAO requestDAO = new RequestDAO(session);
            TaskDAO taskDAO = new TaskDAO(new _Session(new SuperUser()));
            Task task = taskDAO.findById(requestForm.getTask().getId());
            if (task == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            if (requestDAO.findUnResolvedRequest(task) != null) {
                return Response.status(Response.Status.BAD_REQUEST).entity("task has unresolved request").build();
            } else if (task.getRequests() != null && task.getRequests().size() > 42) {
                return Response.status(Response.Status.BAD_REQUEST).entity("task: too more request?! Bad game bro").build();
            }

            RequestTypeDAO requestTypeDAO = new RequestTypeDAO(session);
            RequestType requestType = requestTypeDAO.findById(requestForm.getRequestType().getId());

            Request request = new Request();
            request.setTask(task);
            request.setRequestType(requestType);
            request.setComment(requestForm.getComment());
            request.setAttachments(getActualAttachments(request.getAttachments()));

            request.setEditors(task.getEditors());
            request.addReaderEditor(session.getUser());

            requestDAO.add(request);

            task.setStatus(TaskStatusType.PENDING);
            taskDAO.update(task);

            new Messages(getAppEnv()).sendOfNewRequest(request, task);

            return Response.ok(outcome).build();
        } catch (SecureException | DAOException e) {
            logError(e);
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(outcome.setMessage(e.toString())).build();
        }
    }

    @POST
    @Path("{id}/action/accept")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRequestAccept(@PathParam("id") String id, Request requestForm) {
        return doResolution(id, ResolutionType.ACCEPTED, requestForm);
    }

    @POST
    @Path("{id}/action/decline")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doRequestDecline(@PathParam("id") String id, Request requestForm) {
        return doResolution(id, ResolutionType.DECLINED, requestForm);
    }

    private Response doResolution(String requestId, ResolutionType resolutionType, Request requestForm) {
        Outcome outcome = new Outcome();

        try {
            RequestDAO requestDAO = new RequestDAO(new _Session(new SuperUser()));
            Request request = requestDAO.findById(requestId);

            if (request == null || resolutionType == ResolutionType.UNKNOWN) {
                if (request == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
                if (resolutionType == ResolutionType.UNKNOWN) {
                    return Response.status(Response.Status.NOT_FOUND).entity("ResolutionType.UNKNOWN").build();
                }
            }

            TaskDAO taskDAO = new TaskDAO(new _Session(new SuperUser()));
            Task task = request.getTask();
            if (resolutionType == ResolutionType.ACCEPTED) {
                switch (request.getRequestType().getName()) {
                    case "implement":
                        task.setStatus(TaskStatusType.COMPLETED);
                        break;
                    case "prolong":
                        // prolong new due date
                        Date newDueDate = TimeUtil.stringToDate(getWebFormData().getValueSilently("dueDate"));
                        if (newDueDate == null) {
                            _Validation ve = new _Validation();
                            ve.addError("dueDate", "date", "field_is_empty");
                            return Response.status(Response.Status.BAD_REQUEST).entity(ve).build();
                        }
                        task.setDueDate(newDueDate);
                        task.setStatus(TaskStatusType.PROCESSING);
                        break;
                    case "cancel":
                        task.setStatus(TaskStatusType.CANCELLED);
                        break;
                    default:
                        outcome.setMessage("I don't know what you want. Unknown requestType.name: "
                                + request.getRequestType().getName());
                        return Response.status(Response.Status.BAD_REQUEST).entity(outcome).build();
                }
            } else {
                task.setStatus(TaskStatusType.PROCESSING);
            }
            taskDAO.update(task);

            request.setResolution(resolutionType);
            request.setResolutionTime(new Date());
            request.setDecisionComment(getWebFormData().getValueSilently("comment"));
            requestDAO.update(request);

            new Messages(getAppEnv()).sendMessageOfRequestDecision(request);

            return Response.ok(outcome).build();
        } catch (SecureException | DAOException e) {
            logError(e);
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).entity(outcome.setMessage(e.toString())).build();
        }
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        try {
            RequestDAO dao = new RequestDAO(getSession());
            Request entity = dao.findById(id);
            if (entity != null) {
                entity.setAttachments(null); // if no on delete cascade
                dao.delete(entity);
            }
            return Response.noContent().build();
        } catch (SecureException | DAOException e) {
            logError(e);
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
        }
    }

    @GET
    @Path("{id}/attachments/{attachId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachment(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        try {
            RequestDAO dao = new RequestDAO(getSession());
            Request entity = dao.findById(id);

            return getAttachment(entity, attachId);
        } catch (Exception e) {
            logError(e);
            return Response.status(HttpServletResponse.SC_BAD_REQUEST).build();
        }
    }

    @DELETE
    @Path("{id}/attachments/{attachmentId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteAttachment(@PathParam("id") String id, @PathParam("attachmentId") String attachmentId) {
        return deleteAttachmentFromSessionFormAttachments(attachmentId);
    }

    private _ActionBar getActionBar(_Session session, Request request) {
        _ActionBar actionBar = new _ActionBar(session);
        if (request.isNew()) {
            actionBar.addAction(new _Action("", "", _ActionType.SAVE_AND_CLOSE));
        } else if (request.isEditable()) {
            actionBar.addAction(new _Action("", "", _ActionType.DELETE_DOCUMENT));
        }

        if (!request.isNew()) {
            if (request.getTask().getAuthor().getId().equals(session.getUser().getId())
                    && (request.getResolution() != ResolutionType.ACCEPTED
                    && request.getResolution() != ResolutionType.DECLINED)) {
                actionBar.addAction(new _Action("", "", "resolution"));
            }
        }
        return actionBar;
    }

    @Override
    public ServiceDescriptor updateDescription(ServiceDescriptor sd) {
        sd.setName(getClass().getName());
        ServiceMethod m = new ServiceMethod();
        m.setMethod(HttpMethod.GET);
        m.setURL("/" + sd.getAppName() + sd.getUrlMapping() + "/requests");
        sd.addMethod(m);
        return sd;
    }
}