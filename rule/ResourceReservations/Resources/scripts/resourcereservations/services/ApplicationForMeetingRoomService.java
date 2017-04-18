package resourcereservations.services;

import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.dataengine.jpa.ViewPage;
import com.exponentus.env.EnvConst;
import com.exponentus.exception.SecureException;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.outgoingdto.Outcome;
import com.exponentus.runtimeobj.RegNum;
import com.exponentus.scripting.SortParams;
import com.exponentus.scripting.WebFormData;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;
import reference.model.constants.ApprovalType;
import resourcereservations.dao.ApplicationForMeetingRoomDAO;
import resourcereservations.domain.impl.ApplicationForMeetingRoomDomain;
import resourcereservations.model.ApplicationForMeetingRoom;
import staff.dao.EmployeeDAO;
import staff.model.Employee;
import workflow.model.exception.ApprovalException;
import workflow.model.util.ApprovalLifecycle;
import workflow.other.Messages;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("applications_for_meeting_room")
public class ApplicationForMeetingRoomService extends RestProvider {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getView() {
        _Session session = getSession();
        WebFormData params = getWebFormData();
        int pageSize = session.pageSize;
        SortParams sortParams = params.getSortParams(SortParams.desc("regDate"));

        try {
            //ApplicationForMeetingRoomFilter filter = new ApplicationForMeetingRoomFilter();

            // setup filter
//            String vehicleId = params.getValueSilently("vehicle");
//            if (!vehicleId.isEmpty()) {
//                Vehicle vehicle = new Vehicle();
//                vehicle.setId(UUID.fromString(vehicleId));
//                filter.setVehicle(vehicle);
//            }
//
//            String statusName = params.getValueSilently("status");
//            if (!statusName.isEmpty()) {
//                filter.setStatus(ApprovalStatusType.valueOf(statusName));
//            }
//
//            String resultName = params.getValueSilently("result");
//            if (!resultName.isEmpty()) {
//                filter.setResult(ApprovalResultType.valueOf(resultName));
//            }
//
//            if (params.containsField("tag")) {
//                List<Tag> tags = new ArrayList<>();
//                String[] tagIds = params.getListOfValuesSilently("tag");
//                for (String tid : tagIds) {
//                    Tag tag = new Tag();
//                    tag.setId(UUID.fromString(tid));
//                    tags.add(tag);
//                }
//                filter.setTags(tags);
//            }
            //

            ApplicationForMeetingRoomDAO avDAO = new ApplicationForMeetingRoomDAO(session);
            ViewPage vp = avDAO.findViewPage(sortParams, params.getPage(), pageSize);

            _ActionBar actionBar = new _ActionBar(session);
            actionBar.addAction(new _Action("add_new", "", "new_application_for_vehicle"));
            actionBar.addAction(new _Action("", "", "refresh", "fa fa-refresh", ""));

            Outcome outcome = new Outcome();
            outcome.setId("applications_for_vehicle");
            outcome.setTitle("applications_for_vehicle");
            outcome.addPayload(actionBar);
            outcome.addPayload(vp);
            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getById(@PathParam("id") String id) {
        _Session ses = getSession();
        ApplicationForMeetingRoom entity;
        ApplicationForMeetingRoomDomain domain = new ApplicationForMeetingRoomDomain();

        try {
            EmployeeDAO employeeDAO = new EmployeeDAO(ses);
            boolean isNew = "new".equals(id);

            if (isNew) {
                entity = domain.composeNew(employeeDAO.findByUser(ses.getUser()));
            } else {
                ApplicationForMeetingRoomDAO incomingDAO = new ApplicationForMeetingRoomDAO(ses);
                entity = incomingDAO.findByIdentefier(id);
            }

            EmployeeDAO empDao = new EmployeeDAO(ses);
            Map<Long, Employee> emps = empDao.findAll(false).getResult().stream()
                    .collect(Collectors.toMap(Employee::getUserID, Function.identity(), (e1, e2) -> e1));

            Outcome outcome = domain.getOutcome(entity);
            outcome.addPayload("employees", emps);
            outcome.addPayload(getActionBar(ses, entity, domain));
            outcome.addPayload(EnvConst.FSID_FIELD_NAME, getWebFormData().getFormSesId());

            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(ApplicationForMeetingRoom dto) {
        dto.setId(null);
        return save(dto);
    }

    @PUT
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathParam("id") String id, ApplicationForMeetingRoom dto) {
        dto.setId(UUID.fromString(id));
        return save(dto);
    }

    public Response save(ApplicationForMeetingRoom dto) {
        _Session ses = getSession();
        ApplicationForMeetingRoom entity;
        ApplicationForMeetingRoomDomain domain = new ApplicationForMeetingRoomDomain();

        try {
            validate(dto);

            EmployeeDAO employeeDAO = new EmployeeDAO(ses);
            ApplicationForMeetingRoomDAO avDAO = new ApplicationForMeetingRoomDAO(ses);

            if (dto.isNew()) {
                entity = new ApplicationForMeetingRoom();
            } else {
                entity = avDAO.findById(dto.getId());
            }

            dto.setAppliedAuthor(employeeDAO.findById(dto.getAppliedAuthor().getId()));
            dto.setAttachments(getActualAttachments(entity.getAttachments(), dto.getAttachments()));

            domain.fillFromDto(entity, dto, employeeDAO.findByUser(ses.getUser()));

            if (dto.isNew()) {
                RegNum rn = new RegNum();
                entity.setRegNumber(Integer.toString(rn.getRegNumber(entity.getDefaultFormName())));
                entity = avDAO.add(entity, rn);
            } else {
                entity = avDAO.update(entity);
            }

            entity = avDAO.findById(entity.getId());

            return Response.ok(domain.getOutcome(entity)).build();
        } catch (SecureException | DAOException e) {
            return responseException(e);
        } catch (_Validation.VException e) {
            return responseValidationError(e.getValidation());
        }
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        _Session ses = getSession();
        try {
            ApplicationForMeetingRoomDAO dao = new ApplicationForMeetingRoomDAO(ses);
            ApplicationForMeetingRoom entity = dao.findByIdentefier(id);
            if (entity != null) {
                dao.delete(entity);
            }
            return Response.noContent().build();
        } catch (DAOException | SecureException e) {
            return responseException(e);
        }
    }

    @GET
    @Path("{id}/attachments/{attachId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachment(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        try {
            ApplicationForMeetingRoomDAO dao = new ApplicationForMeetingRoomDAO(getSession());
            ApplicationForMeetingRoom entity = dao.findByIdentefier(id);

            return getAttachment(entity, attachId);
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @GET
    @Path("{id}/attachments/{attachId}/{fileName}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachmentFN(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        return getAttachment(id, attachId);
    }

    @POST
    @Path("{id}/action/startApproving")
    public Response startApproving(@PathParam("id") String id) {
        try {
            ApplicationForMeetingRoomDAO afvDAO = new ApplicationForMeetingRoomDAO(getSession());
            ApplicationForMeetingRoom entity = afvDAO.findByIdentefier(id);
            ApplicationForMeetingRoomDomain domain = new ApplicationForMeetingRoomDomain();

            domain.startApproving(entity);

            afvDAO.update(entity, false);
            new Messages(getAppEnv()).notifyApprovers(entity, entity.getTitle());
            Outcome outcome = domain.getOutcome(entity);
            outcome.setTitle("approving_started");
            outcome.setMessage("approving_started");
            outcome.addPayload("result", "approving_started");

            return Response.ok(outcome).build();
        } catch (DAOException | SecureException | ApprovalException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}/action/acceptApprovalBlock")
    public Response acceptApprovalBlock(@PathParam("id") String id) {
        try {
            ApplicationForMeetingRoomDAO dao = new ApplicationForMeetingRoomDAO(getSession());
            ApplicationForMeetingRoom entity = dao.findByIdentefier(id);
            ApplicationForMeetingRoomDomain domain = new ApplicationForMeetingRoomDomain();

            domain.acceptApprovalBlock(entity, getSession().getUser());

            dao.update(entity, false);
            new Messages(getAppEnv()).notifyApprovers(entity, entity.getTitle());
            Outcome outcome = domain.getOutcome(entity);
            outcome.setTitle("acceptApprovalBlock");
            outcome.setMessage("acceptApprovalBlock");

            return Response.ok(outcome).build();
        } catch (DAOException | SecureException | ApprovalException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}/action/declineApprovalBlock")
    public Response declineApprovalBlock(@PathParam("id") String id) {
        try {
            ApplicationForMeetingRoomDAO dao = new ApplicationForMeetingRoomDAO(getSession());
            ApplicationForMeetingRoom entity = dao.findByIdentefier(id);
            ApplicationForMeetingRoomDomain domain = new ApplicationForMeetingRoomDomain();

            String decisionComment = getWebFormData().getValueSilently("comment");

            domain.declineApprovalBlock(entity, getSession().getUser(), decisionComment);

            dao.update(entity, false);
            new Messages(getAppEnv()).notifyApprovers(entity, entity.getTitle());
            Outcome outcome = domain.getOutcome(entity);
            outcome.setTitle("declineApprovalBlock");
            outcome.setMessage("declineApprovalBlock");

            return Response.ok(outcome).build();
        } catch (DAOException | SecureException | ApprovalException e) {
            return responseException(e);
        }
    }

    private _ActionBar getActionBar(_Session session, ApplicationForMeetingRoom entity, ApplicationForMeetingRoomDomain domain)
            throws DAOException {
        _ActionBar actionBar = new _ActionBar(session);

        actionBar.addAction(new _Action("close", "", "close", "fa fa-chevron-left", "btn-back"));
        if (entity.isEditable()) {
            actionBar.addAction(new _Action("save_close", "", "save_and_close", "", "btn-primary"));
        }
        if (domain.approvalCanBeStarted(entity)) {
            actionBar.addAction(new _Action("start_approving", "", "start_approving"));
        }

        EmployeeDAO employeeDAO = new EmployeeDAO(getSession());

        if (domain.employeeCanDoDecisionApproval(entity, employeeDAO.findByUser(session.getUser()))) {
            if (ApprovalLifecycle.getProcessingBlock(entity).getType() == ApprovalType.SIGNING) {
                actionBar.addAction(new _Action("sign", "", "sign_approval_block"));
            } else {
                actionBar.addAction(new _Action("accept", "", "accept_approval_block"));
            }
            actionBar.addAction(new _Action("decline", "", "decline_approval_block"));
        }

        // actionBar.addAction(new _Action("sign", "", "sign"));
        if (!entity.isNew() && entity.isEditable()) {
            actionBar.addAction(new _Action("delete", "", _ActionType.DELETE_DOCUMENT));
        }

        return actionBar;
    }

    private void validate(ApplicationForMeetingRoom model) throws _Validation.VException {
        _Validation ve = new _Validation();

        if (model.getTitle() == null || model.getTitle().isEmpty()) {
            ve.addError("title", "required", "field_is_empty");
        }

        ve.assertValid();
    }
}