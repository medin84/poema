package resourcereservations.services;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.dataengine.jpa.ViewPage;
import com.exponentus.env.EnvConst;
import com.exponentus.exception.SecureException;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.outgoingdto.Outcome;
import com.exponentus.runtimeobj.RegNum;
import com.exponentus.scripting.SortParams;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;

import staff.dao.EmployeeDAO;
import staff.model.Employee;
import workflow.dao.ApplicationForVehicleDAO;
import workflow.domain.impl.ApplicationForVehicleDomain;
import workflow.model.ApplicationForVehicle;

@Path("applications_for_vehicle")
public class ApplicationForVehicleService extends RestProvider {

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getView() {
		_Session session = getSession();
		int pageSize = session.pageSize;
		SortParams sortParams = getWebFormData().getSortParams(SortParams.desc("regDate"));

		try {
			ApplicationForVehicleDAO avDAO = new ApplicationForVehicleDAO(session);
			ViewPage vp = avDAO.findAll(getWebFormData().getPage(), pageSize);

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
		ApplicationForVehicle entity;
		ApplicationForVehicleDomain domain = new ApplicationForVehicleDomain();

		try {
			EmployeeDAO employeeDAO = new EmployeeDAO(ses);
			boolean isNew = "new".equals(id);

			if (isNew) {
				entity = domain.composeNew(employeeDAO.findByUser(ses.getUser()));
			} else {
				ApplicationForVehicleDAO incomingDAO = new ApplicationForVehicleDAO(ses);
				entity = incomingDAO.findById(id);
			}

			EmployeeDAO empDao = new EmployeeDAO(ses);
			Map<Long, Employee> emps = empDao.findAll(false).getResult().stream()
					.collect(Collectors.toMap(Employee::getUserID, Function.identity(), (e1, e2) -> e1));

			Outcome outcome = domain.getOutcome(entity);
			outcome.addPayload("employees", emps);
			outcome.addPayload(getActionBar(ses, entity));
			outcome.addPayload(EnvConst.FSID_FIELD_NAME, getWebFormData().getFormSesId());

			return Response.ok(outcome).build();
		} catch (DAOException e) {
			return responseException(e);
		}
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response add(ApplicationForVehicle dto) {
		dto.setId(null);
		return save(dto);
	}

	@PUT
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response update(@PathParam("id") String id, ApplicationForVehicle dto) {
		dto.setId(UUID.fromString(id));
		return save(dto);
	}

	public Response save(ApplicationForVehicle dto) {
		_Session ses = getSession();
		ApplicationForVehicle entity;
		ApplicationForVehicleDomain domain = new ApplicationForVehicleDomain();

		try {
			validate(dto);

			EmployeeDAO employeeDAO = new EmployeeDAO(ses);
			ApplicationForVehicleDAO avDAO = new ApplicationForVehicleDAO(ses);

			if (dto.isNew()) {
				entity = new ApplicationForVehicle();
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
			ApplicationForVehicleDAO dao = new ApplicationForVehicleDAO(ses);
			ApplicationForVehicle entity = dao.findById(id);
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
			ApplicationForVehicleDAO dao = new ApplicationForVehicleDAO(getSession());
			ApplicationForVehicle entity = dao.findById(id);

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

	private _ActionBar getActionBar(_Session session, ApplicationForVehicle entity) {
		_ActionBar actionBar = new _ActionBar(session);

		actionBar.addAction(new _Action("close", "", _ActionType.CLOSE));
		if (entity.isEditable()) {
			actionBar.addAction(new _Action("save_close", "", "save_and_close", "", "btn-primary"));
		}

		// actionBar.addAction(new _Action("sign", "", "sign"));
		if (!entity.isNew() && entity.isEditable()) {
			actionBar.addAction(new _Action("delete", "", _ActionType.DELETE_DOCUMENT));
		}

		return actionBar;
	}

	private void validate(ApplicationForVehicle model) throws _Validation.VException {
		_Validation ve = new _Validation();

		if (model.getTitle() == null || model.getTitle().isEmpty()) {
			ve.addError("title", "required", "field_is_empty");
		}

		ve.assertValid();
	}
}
