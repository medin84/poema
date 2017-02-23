package workflow.services;

import administrator.dao.UserDAO;
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
import com.exponentus.scripting.SortParams;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;
import com.exponentus.user.IUser;
import staff.dao.EmployeeDAO;
import staff.model.Employee;
import workflow.dao.OfficeMemoDAO;
import workflow.model.OfficeMemo;
import workflow.model.constants.ApprovalStatusType;
import workflow.model.constants.ApprovalType;
import workflow.model.embedded.Approval;
import workflow.model.embedded.Approver;
import workflow.model.embedded.Block;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("office-memos")
public class OfficeMemoService extends RestProvider {

    private Outcome outcome = new Outcome();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getView() {
        _Session session = getSession();
        int pageSize = session.pageSize;
        SortParams sortParams = getWebFormData().getSortParams(SortParams.desc("regDate"));

        try {
            OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(session);
            ViewPage vp = officeMemoDAO.findViewPage(sortParams, getWebFormData().getPage(), pageSize);

            _ActionBar actionBar = new _ActionBar(session);
            actionBar.addAction(new _Action("add_new", "", "new_office_memo"));
            actionBar.addAction(new _Action("", "", "refresh", "fa fa-refresh", ""));
            // actionBar.addAction(new _Action("del_document", "", _ActionType.DELETE_DOCUMENT));

            EmployeeDAO empDao = new EmployeeDAO(session);
            Map<Long, Employee> emps = empDao.findAll(false).getResult().stream()
                    .collect(Collectors.toMap(Employee::getUserID, Function.identity(), (e1, e2) -> e1));

            outcome.setId("office-memo");
            outcome.setTitle("office_memo_plural");
            outcome.addPayload(actionBar);
            outcome.addPayload(vp);
            outcome.addPayload("employees", emps);

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
        OfficeMemo entity;
        try {
            EmployeeDAO employeeDAO = new EmployeeDAO(ses);
            boolean isNew = "new".equals(id);
            if (isNew) {
                entity = new OfficeMemo();
                Approval approval = new Approval();
                approval.setStatus(ApprovalStatusType.DRAFT);
                //
                approval.setBlocks(new ArrayList<>());
                entity.setApproval(approval);
                entity.setAppliedAuthor(employeeDAO.findByUser(ses.getUser()));
            } else {
                OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(ses);
                entity = officeMemoDAO.findById(id);
            }

            EmployeeDAO empDao = new EmployeeDAO(ses);

            outcome.setId(id);
            outcome.addPayload("employees", empDao.findAll(false).getResult());
            outcome.addPayload(entity);
            outcome.addPayload(getActionBar(ses, entity));
            outcome.addPayload(EnvConst.FSID_FIELD_NAME, getWebFormData().getFormSesId());
            if (!isNew) {
                outcome.addPayload(new ACL(entity));
            }

            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response save(@PathParam("id") String id, OfficeMemo form) {
        _Validation validation = validate(form);
        if (validation.hasError()) {
            return responseValidationError(validation);
        }

        _Session ses = getSession();
        try {
            EmployeeDAO employeeDAO = new EmployeeDAO(ses);
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
                entity.setApproval(form.getApproval());
            } else {
                entity.setApproval(null);
            }
            entity.setTitle(form.getTitle());
            entity.setBody(form.getBody());
            entity.setAppliedAuthor(form.getAppliedAuthor());
            entity.setRecipient(form.getRecipient());
            entity.setAttachments(getActualAttachments(entity.getAttachments(), form.getAttachments()));

            if (isNew) {
                RegNum rn = new RegNum();
                entity.setRegNumber(Integer.toString(rn.getRegNumber(entity.getDefaultFormName())));

                IUser<Long> user = ses.getUser();
                entity.addReaderEditor(user);
                entity.setAppliedAuthor(employeeDAO.findByUser(ses.getUser()));
                entity = officeMemoDAO.add(entity, rn);
            } else {
                entity = officeMemoDAO.update(entity);
            }

            outcome.setId(id);
            outcome.setTitle(entity.getTitle());
            outcome.addPayload(entity);

            return Response.ok(outcome).build();
        } catch (SecureException | DAOException e) {
            return responseException(e);
        }
    }

    @DELETE
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response delete(@PathParam("id") String id) {
        _Session ses = getSession();
        try {
            OfficeMemoDAO dao = new OfficeMemoDAO(ses);
            OfficeMemo entity = dao.findById(id);
            if (entity != null) {
                try {
                    dao.delete(entity);
                } catch (SecureException | DAOException e) {
                    return responseException(e);
                }
            }
            return Response.noContent().build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    /*
     * Get entity attachment or _thumbnail
     */
    @GET
    @Path("{id}/attachments/{attachId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getAttachment(@PathParam("id") String id, @PathParam("attachId") String attachId) {
        try {
            OfficeMemoDAO dao = new OfficeMemoDAO(getSession());
            OfficeMemo entity = dao.findById(id);

            return getAttachment(entity, attachId);
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}/actions/startApproving")
    public Response startApproving(@PathParam("id") String id) {
    /*
    DRAFT>PROCESSING. При статусе DRAFT должна быть кнопка “start approving”(Начать согласование).
    Берем первый блок, и выдаем права, на чтение: если последовательно - Первому согласователю, если Паралелльно - то всем согласователям в блоке .
    Документ меняет статус на PROCESSING и текущий блок меняет статус на PROCESSING, остальные блоки переходят в AWAITING. Кнопка “start approving” исчезает.
    Согласователи получают уведомления и открыв документ видят кнопки Согласен, Отклонить (Accept, Decline).
     */
        try {
            UserDAO userDAO = new UserDAO(getSession());
            OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(getSession());
            OfficeMemo om = officeMemoDAO.findById(id);

            if (om.getApproval().getStatus() != ApprovalStatusType.DRAFT) {
                throw new IllegalStateException("status error: " + om.getApproval().getStatus());
            }

            Block block = om.getApproval().getNextBlock();

            if (block.getType() == ApprovalType.SERIAL) {
                Approver approver = block.getNextApprover();
                approver.setCurrent(true);
                om.addReader(approver.getEmployee().getUser());
            } else if (block.getType() == ApprovalType.PARALLEL) {
                om.addReaders(block.getApprovers().stream().map(approver -> approver.getEmployee().getUser().getId()).collect(Collectors.toList()));
            } else if (block.getType() == ApprovalType.SIGNING) {
                Approver approver = block.getNextApprover();
                approver.setCurrent(true);
                om.addReader(approver.getEmployee().getUser());
            } else {
                throw new IllegalStateException("Block type error: " + block.getType());
            }

            om.getApproval().setStatus(ApprovalStatusType.PROCESSING);
            block.setStatus(ApprovalStatusType.PROCESSING);

            om.getApproval().getBlocks().forEach(b -> {
                if (!block.getId().equals(b.getId())) {
                    b.setStatus(ApprovalStatusType.AWAITING);
                }
            });

            officeMemoDAO.update(om);

            outcome.setTitle("start_approving_ok");
            outcome.setMessage("start_approving_ok");
            outcome.addPayload(om);

            return Response.ok(outcome).build();
        } catch (DAOException | SecureException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}/actions/acceptApprovalBlock")
    public Response acceptApprovalBlock(@PathParam("id") String id) {
    /*
    При согласен (у workflow.model.embedded.Approver DecisionType = YES) при последовательном отбираем права на кнопки “Согласен, Отклонить”
    И даем права на чтение следующему согласователю, при паралелльном, просто отбираем права на кнопки “Согласен, Отклонить”.
    Если согласователи “закончились” то закрываем блок (ApprovalStatusType=FINISHED) и переходим к следующему.
    Если блоки закончились то согласование завершено (ApprovalStatusType=FINISHED) у Approval...
     */
        try {
            UserDAO userDAO = new UserDAO(getSession());
            EmployeeDAO employeeDAO = new EmployeeDAO(getSession());
            OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(getSession());
            OfficeMemo om = officeMemoDAO.findById(id);

            if (om.getApproval().getStatus() != ApprovalStatusType.PROCESSING) {
                throw new IllegalStateException("status error: " + om.getApproval().getStatus());
            }

            Block processBlock = om.getApproval().getProcessingBlock();
            if (processBlock == null) {
                throw new IllegalStateException("not found processing Block");
            }

            processBlock.getApprover(employeeDAO.findByUser(getSession().getUser())).agree();

            Approver nextApprover = processBlock.getNextApprover();
            if (nextApprover != null) {
                // add next approver for read
                if (processBlock.getType() == ApprovalType.SERIAL || processBlock.getType() == ApprovalType.SIGNING) {
                    nextApprover.setCurrent(true);
                    om.addReader(nextApprover.getEmployee().getUser());
                }
            } else {
                processBlock.setStatus(ApprovalStatusType.FINISHED);

                Block nextBlock = om.getApproval().getNextBlock();
                if (nextBlock != null) {
                    nextBlock.setStatus(ApprovalStatusType.PROCESSING);

                    if (nextBlock.getType() == ApprovalType.SERIAL) {
                        Approver _nextApprover = nextBlock.getNextApprover();
                        _nextApprover.setCurrent(true);
                        om.addReader(_nextApprover.getEmployee().getUser());
                    } else if (nextBlock.getType() == ApprovalType.PARALLEL) {
                        om.addReaders(nextBlock.getApprovers().stream().map(approver -> approver.getEmployee().getUser().getId()).collect(Collectors.toList()));
                    } else if (nextBlock.getType() == ApprovalType.SIGNING) {
                        Approver approver = nextBlock.getNextApprover();
                        approver.setCurrent(true);
                        om.addReader(approver.getEmployee().getUser());
                    } else {
                        throw new IllegalStateException("Block type error: " + nextBlock.getType());
                    }
                } else {
                    om.getApproval().setStatus(ApprovalStatusType.FINISHED);
                }
            }

            officeMemoDAO.update(om, false);

            outcome.setTitle("acceptApprovalBlock");
            outcome.setMessage("acceptApprovalBlock");
            outcome.addPayload(om);

            return Response.ok(outcome).build();
        } catch (DAOException | SecureException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("{id}/actions/declineApprovalBlock")
    public Response declineApprovalBlock(@PathParam("id") String id) {
        try {
            UserDAO userDAO = new UserDAO(getSession());
            EmployeeDAO employeeDAO = new EmployeeDAO(getSession());
            OfficeMemoDAO officeMemoDAO = new OfficeMemoDAO(getSession());
            OfficeMemo om = officeMemoDAO.findById(id);

            if (om.getApproval().getStatus() != ApprovalStatusType.PROCESSING) {
                throw new IllegalStateException("status error: " + om.getApproval().getStatus());
            }

            Block processBlock = om.getApproval().getProcessingBlock();
            if (processBlock == null) {
                throw new IllegalStateException("not found processing Block");
            }

            String decisionComment = getWebFormData().getValueSilently("comment");
            processBlock.getApprover(employeeDAO.findByUser(getSession().getUser())).disagree(decisionComment);

            Approver nextApprover = processBlock.getNextApprover();
            if (nextApprover != null) {
                // add next approver for read
                if (processBlock.getType() == ApprovalType.SERIAL || processBlock.getType() == ApprovalType.SIGNING) {
                    nextApprover.setCurrent(true);
                    om.addReader(nextApprover.getEmployee().getUser());
                }
            } else {
                processBlock.setStatus(ApprovalStatusType.FINISHED);

                Block nextBlock = om.getApproval().getNextBlock();
                if (nextBlock != null) {
                    nextBlock.setStatus(ApprovalStatusType.PROCESSING);

                    if (nextBlock.getType() == ApprovalType.SERIAL) {
                        Approver _nextApprover = nextBlock.getNextApprover();
                        _nextApprover.setCurrent(true);
                        om.addReader(_nextApprover.getEmployee().getUser());
                    } else if (nextBlock.getType() == ApprovalType.PARALLEL) {
                        om.addReaders(nextBlock.getApprovers().stream().map(approver -> approver.getEmployee().getUser().getId()).collect(Collectors.toList()));
                    } else if (nextBlock.getType() == ApprovalType.SIGNING) {
                        Approver approver = nextBlock.getNextApprover();
                        approver.setCurrent(true);
                        om.addReader(approver.getEmployee().getUser());
                    } else {
                        throw new IllegalStateException("Block type error: " + nextBlock.getType());
                    }
                } else {
                    om.getApproval().setStatus(ApprovalStatusType.FINISHED);
                }
            }

            officeMemoDAO.update(om, false);

            outcome.setTitle("declineApprovalBlock");
            outcome.setMessage("declineApprovalBlock");
            outcome.addPayload(om);

            return Response.ok(outcome).build();
        } catch (DAOException | SecureException e) {
            return responseException(e);
        }
    }

    private _ActionBar getActionBar(_Session session, OfficeMemo entity) throws DAOException {
        _ActionBar actionBar = new _ActionBar(session);

        actionBar.addAction(new _Action("close", "", _ActionType.CLOSE));
        actionBar.addAction(new _Action("save_close", "", _ActionType.SAVE_AND_CLOSE));
        if (entity.getApproval().getStatus() == ApprovalStatusType.DRAFT) {
            actionBar.addAction(new _Action("start_approving", "", "start_approving"));
        }

        EmployeeDAO employeeDAO = new EmployeeDAO(getSession());

        if (entity.getApproval().userCanDoDecision(employeeDAO.findByUser(session.getUser()))) {
            actionBar.addAction(new _Action("agree", "", "accept_approval_block"));
            actionBar.addAction(new _Action("disagree", "", "decline_approval_block"));
        }

        actionBar.addAction(new _Action("sign", "", "sign"));
        if (!entity.isNew() && entity.isEditable()) {
            actionBar.addAction(new _Action("delete", "", _ActionType.DELETE_DOCUMENT));
        }

        return actionBar;
    }

    private _Validation validate(OfficeMemo entity) {
        _Validation ve = new _Validation();

        if (entity.getTitle() == null || entity.getTitle().isEmpty()) {
            ve.addError("title", "required", "field_is_empty");
        }
        if (entity.getAppliedAuthor() == null) {
            ve.addError("appliedAuthor", "required", "field_is_empty");
        }
        if (entity.getRecipient() == null) {
            ve.addError("recipient", "required", "field_is_empty");
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