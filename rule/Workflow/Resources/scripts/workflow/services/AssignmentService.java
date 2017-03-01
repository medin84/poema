package workflow.services;

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
import staff.dao.EmployeeDAO;
import staff.model.Employee;
import workflow.dao.AssignmentDAO;
import workflow.dao.IncomingDAO;
import workflow.domain.impl.AssignmentDomain;
import workflow.model.Assignment;
import workflow.model.Incoming;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("assignments")
public class AssignmentService extends RestProvider {

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getById(@PathParam("id") String id) {
        try {
            _Session ses = getSession();
            EmployeeDAO employeeDAO = new EmployeeDAO(ses);
            Employee employee = employeeDAO.findByUserId(ses.getUser().getId());
            AssignmentDAO assignmentDAO = new AssignmentDAO(ses);
            Assignment entity;
            AssignmentDomain ad;
            boolean isNew = "new".equals(id);

            if (isNew) {
                String incomingId = getWebFormData().getAnyValueSilently("incoming");
                String assignmentId = getWebFormData().getAnyValueSilently("assignment");

                Incoming incoming = null;
                Assignment parent = null;

                if (!incomingId.isEmpty()) {
                    incoming = (new IncomingDAO(ses)).findById(incomingId);
                } else if (!assignmentId.isEmpty()) {
                    parent = assignmentDAO.findById(assignmentId);
                } else {
                    throw new IllegalArgumentException("No parent document");
                }

                entity = new Assignment();
                ad = new AssignmentDomain(entity);
                ad.compose(employee, incoming, parent);
            } else {
                entity = assignmentDAO.findById(id);
                ad = new AssignmentDomain(entity);
            }

            //
            EmployeeDAO empDao = new EmployeeDAO(ses);
            Map<Long, Employee> emps = empDao.findAll(false).getResult().stream()
                    .collect(Collectors.toMap(Employee::getUserID, Function.identity(), (e1, e2) -> e1));
            //

            Outcome outcome = ad.getOutcome();
            outcome.addPayload("employees", emps);
            outcome.addPayload(getActionBar(ses, entity));
            outcome.addPayload(EnvConst.FSID_FIELD_NAME, getWebFormData().getFormSesId());

            return Response.ok(outcome).build();
        } catch (Exception e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("id") String id, Assignment dto) {
        _Validation validation = validate(dto);
        if (validation.hasError()) {
            return responseValidationError(validation);
        }

        _Session ses = getSession();
        Assignment entity;
        AssignmentDomain domain;

        try {
            EmployeeDAO employeeDAO = new EmployeeDAO(ses);
            Employee employee = employeeDAO.findByUserId(ses.getUser().getId());
            AssignmentDAO assignmentDAO = new AssignmentDAO(ses);
            boolean isNew = "new".equals(id);

            if (isNew) {
                entity = new Assignment();
            } else {
                entity = assignmentDAO.findById(id);
            }

            dto.setAttachments(getActualAttachments(entity.getAttachments(), dto.getAttachments()));

            domain = new AssignmentDomain(entity);
            domain.fillFromDto(employee, dto);

            if (isNew) {
                entity = assignmentDAO.add(entity);
            } else {
                entity = assignmentDAO.update(entity);
            }

            return Response.ok(domain.getOutcome()).build();
        } catch (SecureException | DAOException e) {
            return responseException(e);
        }
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        try {
            _Session ses = getSession();
            AssignmentDAO dao = new AssignmentDAO(ses);
            Assignment entity = dao.findById(id);
            if (entity != null) {
                dao.delete(entity);
            }
            return Response.noContent().build();
        } catch (SecureException | DAOException e) {
            return responseException(e);
        }
    }

    @GET
    @Path("{id}/attachments/{attachId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachment(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        try {
            AssignmentDAO dao = new AssignmentDAO(getSession());
            Assignment entity = dao.findById(id);

            return getAttachment(entity, attachId);
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    private _ActionBar getActionBar(_Session session, Assignment entity) {
        _ActionBar actionBar = new _ActionBar(session);

        actionBar.addAction(new _Action("close", "", "close", "fa fa-chevron-left", "btn-back"));
        if (entity.isNew() || entity.isEditable()) {
            actionBar.addAction(new _Action("save_close", "", _ActionType.SAVE_AND_CLOSE));
        }
        if (!entity.isNew()) {
            actionBar.addAction(new _Action("assignment", "", "new_assignment"));
        }
        if (entity.getControl().assigneesContainsUser(session.getUser())) {
            actionBar.addAction(new _Action("report", "", "new_report", "", ""));
        }
        // actionBar.addAction(new _Action("sign", "", "sign"));
        if (!entity.isNew() && entity.isEditable()) {
            actionBar.addAction(new _Action("delete", "", _ActionType.DELETE_DOCUMENT));
        }

        return actionBar;
    }

    private _Validation validate(Assignment assignment) {
        _Validation ve = new _Validation();

        if (assignment.getTitle() == null || assignment.getTitle().isEmpty()) {
            ve.addError("title", "required", "field_is_empty");
        }

        if (assignment.getControl().getStartDate() == null) {
            ve.addError("control.startDate", "required", "field_is_empty");
        }

        if (assignment.getControl().getDueDate() == null) {
            ve.addError("control.dueDate", "required", "field_is_empty");
        }

        if (assignment.getControl().getAssigneeEntries() == null || assignment.getControl().getAssigneeEntries().isEmpty()) {
            ve.addError("control.assigneeEntries", "required", "field_is_empty");
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
