package projects.services;

import administrator.dao.UserDAO;
import administrator.model.User;
import com.exponentus.common.domain.ApprovalLifecycle;
import com.exponentus.common.domain.IValidation;
import com.exponentus.common.domain.exception.ApprovalException;
import com.exponentus.common.dto.ActionPayload;
import com.exponentus.common.model.constants.ApprovalResultType;
import com.exponentus.common.model.constants.ApprovalStatusType;
import com.exponentus.common.model.constants.PriorityType;
import com.exponentus.common.model.constants.StatusType;
import com.exponentus.common.model.embedded.Approver;
import com.exponentus.common.model.embedded.Block;
import com.exponentus.common.service.EntityService;
import com.exponentus.common.ui.BaseReferenceModel;
import com.exponentus.common.ui.ViewPage;
import com.exponentus.common.ui.actions.Action;
import com.exponentus.common.ui.actions.ActionBar;
import com.exponentus.common.ui.actions.constants.ActionPayloadType;
import com.exponentus.common.ui.timeline.Milestones;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.env.EnvConst;
import com.exponentus.env.Environment;
import com.exponentus.exception.SecureException;
import com.exponentus.log.Lg;
import com.exponentus.messaging.MessagingHelper;
import com.exponentus.messaging.MessagingType;
import com.exponentus.messaging.email.Memo;
import com.exponentus.messaging.exception.MsgException;
import com.exponentus.rest.exception.RestServiceException;
import com.exponentus.rest.outgoingdto.Outcome;
import com.exponentus.rest.services.Defended;
import com.exponentus.rest.validation.exception.DTOException;
import com.exponentus.scripting.SortParams;
import com.exponentus.scripting.WebFormData;
import com.exponentus.scripting._Session;
import com.exponentus.server.Server;
import com.exponentus.user.IUser;
import com.exponentus.user.SuperUser;
import helpdesk.dao.DemandDAO;
import helpdesk.model.Demand;
import monitoring.dao.DocumentActivityDAO;
import org.eclipse.persistence.exceptions.DatabaseException;
import projects.dao.ProjectDAO;
import projects.dao.TaskDAO;
import projects.dao.filter.TaskFilter;
import projects.domain.TaskDomain;
import projects.dto.converter.ProjectDtoConverter;
import projects.init.ModuleConst;
import projects.model.Project;
import projects.model.Task;
import projects.other.Messages;
import projects.ui.ActionFactory;
import projects.ui.ViewOptions;
import reference.dao.TaskTypeDAO;
import reference.model.Tag;
import reference.model.TaskType;
import staff.dao.EmployeeDAO;
import staff.dto.converter.EmployeeDtoConverter;
import staff.dto.converter.EmployeeToBaseRefUserDtoConverter;
import staff.model.Employee;

import javax.persistence.Tuple;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.stream.Collectors.toList;
import static projects.domain.TaskDomain.MODERATOR_ROLE_NAME;

@Path("tasks")
@Produces(MediaType.APPLICATION_JSON)
public class TaskService extends EntityService<Task, TaskDomain> {

    private static final String REMINDER_MSG_TEMPLATE = "task_reminder";

    @GET
    @Path("preferredAssignees")
    public Response getPreferredAssignees() {
        try {
            _Session ses = getSession();
            TaskDAO taskDAO = new TaskDAO(ses);
            EmployeeDAO empDao = new EmployeeDAO(ses);
            ViewPage<Employee> vp = new ViewPage<>();

            EmployeeDtoConverter employeeDtoConverter = new EmployeeDtoConverter();
            List<Employee> preferredAssignees = new ArrayList<>();
            if (ses.getEmployee().hasRole(ModuleConst.ROLES[2])) {
                preferredAssignees.addAll(empDao.findAll());
            } else {
                List<Tuple> tasks = taskDAO.findAssigneeByPreference(getSession().getUser().getId(), 100);
                for (Tuple tuple : tasks) {
                    Employee employee = empDao.findByUserId((long) tuple.get(1));
                    if (employee != null) {
                        preferredAssignees.add(employee);
                    }
                }
            }
            vp.setResult(employeeDtoConverter.convert(preferredAssignees));

            Outcome outcome = new Outcome();
            outcome.addPayload(vp);

            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @GET
    public Response getViewPage() {
        try {
            _Session session = getSession();
            WebFormData params = getWebFormData();
            String[] expandedIds = params.getListOfValuesSilently("expandedIds");
            List<UUID> expandedIdList = Arrays.stream(expandedIds).map(UUID::fromString).collect(toList());
            int pageSize = session.getPageSize();
            int pageNum = params.getPage();
            String slug = params.getValueSilently("slug");
            TaskDAO taskDAO = new TaskDAO(session);
            TaskFilter taskFilter = setUpTaskFilter(session, params, new TaskFilter());
            SortParams sortParams = SortParams.valueOf(params.getStringValueSilently("sort", "-regDate"));
            ViewPage<Task> vp;
            if (params.getBoolSilently("execution")) {
                Task parentTask = taskDAO.findById(taskFilter.getParentTask().getId());
                vp = taskDAO.findTaskExecution(parentTask);
            } else {
                vp = taskDAO.findAllWithResponses(taskFilter, sortParams, pageNum, pageSize, expandedIdList);
            }
            ViewOptions viewOptions = new ViewOptions();
            vp.setViewPageOptions(viewOptions.getTaskViewOptions());
            vp.setFilter(viewOptions.getTaskFilter(session, slug));

            ActionFactory action = new ActionFactory();
            ActionBar actionBar = new ActionBar(session);
            actionBar.addAction(action.newTask());
            actionBar.addAction(action.refreshVew);

            EmployeeToBaseRefUserDtoConverter converter = new EmployeeToBaseRefUserDtoConverter();
            EmployeeDAO empDao = new EmployeeDAO(session);
            List<BaseReferenceModel<Long>> empsResult = converter.convert(empDao.findAll(false).getResult());
            Map<Long, BaseReferenceModel> emps = empsResult.stream().collect(Collectors.toMap(BaseReferenceModel::getId, Function.identity(), (e1, e2) -> e1));

            String title;
            switch (slug) {
                case "inbox":
                    title = "tasks_assigned_to_me";
                    break;
                case "my":
                    title = "my_tasks";
                    break;
                case "moderate":
                    title = slug;
                    break;
                default:
                    title = "tasks";
                    break;
            }

            Outcome outcome = new Outcome();
            outcome.setId(title);
            outcome.setTitle(title);
            outcome.addPayload(vp);
            outcome.addPayload(actionBar);
            outcome.addPayload("employees", emps);

            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @GET
    @Path("{id}")
    public Response getById(@PathParam("id") String id) {
        _Session session = getSession();
        WebFormData params = getWebFormData();

        String fsId = params.getFormSesId();
        String projectId = params.getValueSilently("projectId");
        String parentTaskId = params.getValueSilently("parentTaskId");
        String demandId = params.getValueSilently("demand");
        boolean initiative = params.getBoolSilently("initiative");
        boolean isNew = "new".equals(id);

        try {
            EmployeeDAO empDao = new EmployeeDAO(session);
            ProjectDAO prjDao = new ProjectDAO(session);
            TaskDAO taskDAO = new TaskDAO(session);
            IUser user = session.getUser();
            Task task;
            TaskDomain taskDomain = new TaskDomain(session);

            if (isNew) {
                Project project = null;
                Demand demand = null;
                Task parentTask = null;

                if (!parentTaskId.isEmpty()) {
                    parentTask = taskDAO.findById(parentTaskId);
                }

                if (!projectId.isEmpty()) {
                    ProjectDAO projectDAO = new ProjectDAO(session);
                    project = projectDAO.findById(projectId);
                } else if (parentTask != null) {
                    project = parentTask.getProject();
                }

                if (!demandId.isEmpty()) {
                    DemandDAO demandDAO = new DemandDAO(session);
                    demand = demandDAO.findById(demandId);
                } else if (parentTask != null) {
                    demand = parentTask.getDemand();
                }

                TaskTypeDAO taskTypeDAO = new TaskTypeDAO(session);
                TaskType taskType = null;
                try {
                    taskType = taskTypeDAO.findByName(ModuleConst.DEFAULT_TASK_TYPE);
                } catch (DAOException e) {
                    Server.logger.exception(e);
                }

                task = taskDomain.composeNew((User) user, project, parentTask, demand, taskType, initiative, ModuleConst.DEFAULT_DUE_DATE_RANGE);
            } else {
                task = taskDAO.findById(id);
                if (task == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }

                if (!user.getRoles().contains(MODERATOR_ROLE_NAME)) {
                    task.setActualExecTimeInHours(0);
                } else {
                    task.calcPlannedTimeInHours();
                }

                Environment.database.markAsRead(session.getUser(), task);
            }

            Map<Long, Employee> emps = empDao.findAll(false).getResult().stream()
                    .collect(Collectors.toMap(Employee::getUserID, Function.identity(), (e1, e2) -> e1));

            Outcome outcome = taskDomain.getOutcome(task);
            outcome.setId(id);
            outcome.setFSID(fsId);
            outcome.addPayload(getActionBar(session, taskDomain, task));
            outcome.addPayload(new Milestones(session, task.getStages(), task.getEstimateInHours()));
            outcome.addPayload("employees", emps);
            outcome.addPayload("activity", new DocumentActivityDAO(session).findByEntityIdSilently(task.getId()).getDetails());
            outcome.addPayload("priorityTypes", Arrays.stream(PriorityType.values()).filter(it -> it != PriorityType.UNKNOWN).collect(toList()));

            //
            EmployeeDtoConverter employeeDtoConverter = new EmployeeDtoConverter();
            List<Employee> preferredAssignees = new ArrayList<>();
            List<Tuple> tasks = taskDAO.findAssigneeByPreference(user.getId(), 5);
            for (Tuple tuple : tasks) {
                Employee employee = empDao.findByUserId((long) tuple.get(1));
                if (employee != null) {
                    preferredAssignees.add(employee);
                }
            }
            outcome.addPayload("preferredAssignees", employeeDtoConverter.convert(preferredAssignees));

            ProjectDtoConverter projectDtoConverter = new ProjectDtoConverter();
            List<Project> preferredProjects = new ArrayList<>();
            List<Tuple> projects = prjDao.findProjectByPreference(user.getId(), 5);
            for (Tuple tuple : projects) {
                Project project = (Project) tuple.get(1);
                if (project != null) {
                    preferredProjects.add(project);
                }
            }
            outcome.addPayload("preferredProjects", projectDtoConverter.convert(preferredProjects));

            return Response.ok(outcome).build();
        } catch (DAOException | RestServiceException e) {
            return responseException(e);
        }
    }

    @Override
    public Response saveForm(Task taskDto) {
        _Session session = getSession();

        try {
            TaskDAO taskDAO = new TaskDAO(session);
            TaskDomain taskDomain = new TaskDomain(session);
            Task task = taskDomain.fillFromDto(taskDto, new Validation(getSession()), getWebFormData().getFormSesId());
            taskDomain.saveTask(task);
            if (task.getParent() != null) {
                Task parent = taskDAO.findById(task.getParent().getId());
                parent.addReader(task.getAssignee());
                taskDAO.update(parent);
            }
            return Response.ok(taskDomain.getOutcome(taskDAO.findById(task.getId()))).build();
        } catch (SecureException | DatabaseException | DAOException e) {
            return responseException(e);
        } catch (DTOException e) {
            return responseValidationError(e);
        } catch (Exception e) {
            return responseException(e);
        }
    }

    @DELETE
    @Path("{id}")
    public Response delete(@PathParam("id") String id) {
        try {
            TaskDAO dao = new TaskDAO(getSession());
            Task entity = dao.findById(id);
            if (entity != null) {
                entity.setBlocks(null);
                entity.setAttachments(null); // if no on delete cascade
                dao.delete(entity);
            }
            return Response.noContent().build();
        } catch (SecureException | DAOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("saveAsDraft")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response saveAsDraft(Task taskDto) {
        _Session session = getSession();
        try {
            TaskDAO taskDAO = new TaskDAO(session);
            TaskDomain taskDomain = new TaskDomain(session);
            Task task = taskDomain.fillFromDto(taskDto, new EmptyValidation(), getWebFormData().getFormSesId());
            taskDomain.saveTask(task);
            return Response.ok(taskDomain.getOutcome(taskDAO.findById(task.getId()))).build();
        } catch (SecureException | DatabaseException | DAOException e) {
            return responseException(e);
        } catch (DTOException e) {
            return responseValidationError(e);
        } catch (Exception e) {
            return responseException(e);
        }
    }

    @POST
    @Path("acknowledged")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doTaskAcknowledged(ActionPayload<Task, Float> action) {
        try {
            TaskDAO dao = new TaskDAO(getSession());
            Task task = dao.findById(action.getTarget().getId());
            TaskDomain taskDomain = new TaskDomain(getSession());

            task.setEstimateInHours(action.getPayload());
            taskDomain.acknowledgedTask(task, (User) getSession().getUser());
            dao.update(task, false);
            new Messages(getAppEnv()).sendOfNewAcknowledging(task);
            return Response.ok(taskDomain.getOutcome(task)).build();
        } catch (SecureException | DAOException | DTOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("complete")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doTaskComplete(ActionPayload<Task, Object> action) {
        try {
            TaskDAO dao = new TaskDAO(getSession());
            Task task = dao.findById(action.getTarget().getId());
            TaskDomain taskDomain = new TaskDomain(getSession());
            taskDomain.completeTask(task);
            dao.update(task, false);
            new Messages(getAppEnv()).sendOfTaskCompleted(task);
            return Response.ok(taskDomain.getOutcome(task)).build();
        } catch (SecureException | DAOException | DatabaseException | DTOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response doTaskCancel(ActionPayload<Task, String> action) {
        try {
            TaskDAO dao = new TaskDAO(getSession());
            Task task = dao.findById(action.getTarget().getId());
            TaskDomain taskDomain = new TaskDomain(getSession());
            taskDomain.cancelTask(task, action.getPayload());
            dao.update(task, false);
            new Messages(getAppEnv()).sendOfTaskCancelled(task);

            return Response.ok(taskDomain.getOutcome(task)).build();
        } catch (SecureException | DAOException | DTOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("acceptApprovalBlock")
    public Response acceptApprovalBlock(ActionPayload<Task, Object> action) {
        try {
            _Session ses = getSession();
            TaskDomain domain = new TaskDomain(ses);
            Task task = domain.getEntity(action.getTarget().getId());
            domain.acceptApprovalBlock(task, ses.getUser());
            domain.superUpdate(task);

            Outcome outcome = domain.getOutcome(task);
            if (task.getApprovalStatus() == ApprovalStatusType.FINISHED) {
                if (task.getApprovalResult() == ApprovalResultType.ACCEPTED) {
                    new Messages(getAppEnv()).sendToAssignee(task);
                    domain.postCalendarEvent(task);
                    outcome.setTitle("approval_block_accepted");
                    outcome.setMessage("approval_block_accepted");
                }
            }

            return Response.ok(outcome).build();
        } catch (DTOException e) {
            return responseValidationError(e);
        } catch (DAOException | SecureException | ApprovalException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("declineApprovalBlock")
    public Response declineApprovalBlock(ActionPayload<Task, String> action) {
        try {
            _Session ses = getSession();
            TaskDomain domain = new TaskDomain(ses);
            Task entity = domain.getEntity(action.getTarget());
            domain.declineApprovalBlock(entity, ses.getUser(), action.getPayload());
            domain.superUpdate(entity);

            if (entity.getApprovalStatus() == ApprovalStatusType.FINISHED) {
                if (entity.getApprovalResult() == ApprovalResultType.REJECTED) {
                    new Messages(getAppEnv()).sendModeratorRejection(entity);
                    if (entity.isVersionsSupport()) {
                        entity = domain.backToRevise(entity);
                        domain.superUpdate(entity);
                    }
                }
            }

            Outcome outcome = domain.getOutcome(entity);
            outcome.setTitle("approval_block_declined");
            outcome.setMessage("approval_block_declined");

            return Response.ok(outcome).build();
        } catch (DTOException e) {
            return responseValidationError(e);
        } catch (DAOException | SecureException | ApprovalException e) {
            return responseException(e);
        }
    }

    @GET
    @Path("reminder/{taskId}")
    public Response getDefaultReminderText(@PathParam("taskId") String taskId) {
        try {
            _Session ses = getSession();
            TaskDAO taskDAO = new TaskDAO(ses);
            Task task = taskDAO.findById(taskId);

            Outcome outcome = new Outcome();
            if (task.getStatus() == StatusType.OPEN || task.getStatus() == StatusType.PROCESSING) {
                Memo memo = new Memo();
                memo.addVar("regNumber", task.getRegNumber());
                memo.addVar("title", task.getTitle());
                IUser assigneeUser = new UserDAO().findById(task.getAssignee());
                memo.addVar("assignee", assigneeUser.getUserName());
                memo.addVar("author", task.getAuthor().getUserName());
                String reminderText = memo.getPlainBody(getAppEnv().templates.getTemplate(MessagingType.SLACK, REMINDER_MSG_TEMPLATE, ses.getLang()));
                outcome.addPayload("text", reminderText);
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(outcome.setError("WRONG_STATUS")).type(MediaType.APPLICATION_JSON).build();
            }
            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }

    @POST
    @Path("reminder")
    public Response sendReminder(ActionPayload<Task, String> action) {
        try {
            _Session ses = getSession();
            TaskDAO taskDAO = new TaskDAO(ses);
            Task task = taskDAO.findById(action.getTarget().getId());

            Outcome outcome = new Outcome();
            if (task.getStatus() == StatusType.OPEN || task.getStatus() == StatusType.PROCESSING) {
                String subject = Environment.vocabulary.getWord("reminder", ses.getLang());
                UserDAO userDAO = new UserDAO();
                MessagingHelper.sendInAnyWay((User) userDAO.findById(task.getAssignee()), action.getPayload(), task, subject);
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(outcome.setError("WRONG_STATUS")).type(MediaType.APPLICATION_JSON).build();
            }
            outcome.setMessage("ok");
            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        } catch (MsgException e) {
            return responseException(e);
        }
    }

    //
    private ActionBar getActionBar(_Session session, TaskDomain taskDomain, Task task) {
        ActionBar actionBar = new ActionBar(session);
        ActionFactory action = new ActionFactory();

        actionBar.addAction(action.close);
        if (taskDomain.taskIsEditable(task)) {
            if (task.getStatus() == StatusType.DRAFT) {
                actionBar.addAction(action.sendForExecution());
            } else {
                actionBar.addAction(action.saveAndClose);
            }

            if (task.getStatus() == StatusType.DRAFT) {
                actionBar.addAction(new Action().id("SAVE_AS_DRAFT").caption("save_as_draft").payloadType(ActionPayloadType.MODEL).url(ModuleConst.BASE_URL + "api/tasks/saveAsDraft").hidden());
            }
        }

        if (task.getApprovalStatus() == ApprovalStatusType.PENDING) {
            ApprovalLifecycle lifecycle = new ApprovalLifecycle(task);
            Block processingBlock = lifecycle.getProcessingBlock();
            if (processingBlock != null) {
                List<Approver> approvers = processingBlock.getCurrentApprovers();
                if (approvers.size() > 0 && approvers.contains(session.getUser())) {
                    actionBar.addAction(action.acceptApprovalBlock());
                    actionBar.addAction(action.declineApprovalBlock());
                    //actionBar.addAction(new Action(ActionType.CUSTOM_ACTION).id("task_cancel").caption("cancel_task").icon("fa fa-ban"));
                }
            }
        } else if (task.getStatus() != StatusType.DRAFT) {
            if (taskDomain.userCanDoRequest(task, (User) session.getUser())) {
                actionBar.addAction(action.newRequest(task));
                actionBar.addAction(action.sendImplementRequest());
            }
            if (taskDomain.userCanDoAcknowledged(task, (User) session.getUser())) {
                actionBar.addAction(action.acknowledgedTask());
            }
            if (taskDomain.userCanDoResolution(task, (User) session.getUser())) {
                actionBar.addAction(action.completeTask());
                if (task.getApprovalStatus() != ApprovalStatusType.PENDING) {
                    actionBar.addAction(action.cancelTask());
                }
            }
            if (taskDomain.userCanAddSubTask(task, (User) session.getUser())) {
                actionBar.addAction(action.newSubTask(task));
            }
        }

        boolean canReminderStatus = (task.getStatus() == StatusType.OPEN || task.getStatus() == StatusType.PROCESSING);
        boolean isCurrentUserTaskAuthor = session.getUser().getId().equals(task.getAuthor().getId());
        boolean isAuthorNotAssignee = !task.getAuthor().getId().equals(task.getAssignee());
        if (canReminderStatus && isCurrentUserTaskAuthor && isAuthorNotAssignee) {
            actionBar.addAction(action.sendReminder());
        }

        if (taskDomain.taskCanBeDeleted(task)) {
            actionBar.addAction(action.deleteDocument);
        }

        return actionBar;
    }

    public static class Validation implements IValidation<Task> {
        private _Session session;

        public Validation(_Session session) {
            this.session = session;
        }

        @Override
        public void check(Task taskDto) throws DTOException {
            DTOException ve = new DTOException();
            UserDAO userDAO = new UserDAO(session);

            if (taskDto.getParent() == null && taskDto.getProject() == null) {
                ve.addError("project", "required", "field_is_empty");
            }
            if (taskDto.getParent() == null && taskDto.getTaskType() == null) {
                ve.addError("taskType", "required", "field_is_empty");
            }
            if (taskDto.getBody() == null || taskDto.getBody().isEmpty()) {
                ve.addError("body", "required", "field_is_empty");
            } else if (taskDto.getBody().length() > 5000) {
                ve.addError("body", "maxlen:5000", "field_is_too_long");
            }
            if (taskDto.getStatus() == null) {
                ve.addError("status", "required", "field_is_empty");
            }
            if (taskDto.getPriority() == null) {
                ve.addError("priority", "required", "field_is_empty");
            }
            if (taskDto.getStartDate() == null) {
                ve.addError("startDate", "date", "field_is_empty");
            }
            if (taskDto.getDueDate() == null) {
                ve.addError("dueDate", "date", "field_is_empty");
            }

            LocalDate today = LocalDate.now();
            LocalDate startDate = taskDto.getStartDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            long daysBetweenStartAndToday = DAYS.between(today, startDate);

            if (taskDto.isNew() && daysBetweenStartAndToday < 0) {
                ve.addError("startDate", "date_gt:" + new Date().getTime(), "field_date_is_incorrect");
            }

            LocalDate dueDate = taskDto.getDueDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            long daysBetweenStarAndDeadline = DAYS.between(startDate, dueDate);

            if (daysBetweenStarAndDeadline < 0) {
                ve.addError("dueDate", "date_gt:" + taskDto.getStartDate().getTime(), "field_date_is_incorrect");
            }

            if (!taskDto.isInitiative() && (taskDto.getAssignee() == null || taskDto.getAssignee() <= 0)) {
                ve.addError("assignee", "required", "field_is_empty");
            } else if (userDAO.findById(taskDto.getAssignee()) == null) {
                ve.addError("assignee", "required", "user_not_found");
            }

            if (taskDto.getObservers() != null && taskDto.getObservers().size() > 0) {
                for (long uid : taskDto.getObservers()) {
                    IUser ou = userDAO.findById(uid);
                    if (ou == null) {
                        ve.addError("observers", "required", "observer user not found: id=" + uid);
                    }
                }
            }

            if (ve.hasError()) {
                throw ve;
            }
        }
    }

    public TaskFilter setUpTaskFilter(_Session session, WebFormData formData, TaskFilter filter) {

        filter.setProject(formData.getValueSilently("project"));
        filter.setParentTask(formData.getValueSilently("parentTaskId"));
        filter.setTaskType(formData.getValueSilently("taskType"));
        filter.setSearch(formData.getValueSilently("keyWord").toLowerCase());
        filter.setStartDate(formData.getDateSilently("startDate"));
        filter.setDueDate(formData.getDateSilently("dueDate"));

        String taskStatus = formData.getValueSilently("status");
        if (!taskStatus.isEmpty()) {
            filter.setStatus(StatusType.valueOf(taskStatus));
        }

        String taskPriority = formData.getValueSilently("priority");
        if (!taskPriority.isEmpty()) {
            filter.setPriority(PriorityType.valueOf(taskPriority));
        }

        long assigneeUserId = (long) formData.getNumberDoubleValueSilently("assigneeUser", 0);
        if (assigneeUserId > 0) {
            filter.setAssigneeUserId(assigneeUserId);
        }
        long authorUserId = (long) formData.getNumberDoubleValueSilently("author", 0);
        if (authorUserId > 0) {
            User author = new User();
            author.setId(authorUserId);
            filter.setAuthor(author);
        }

        String slug = formData.getValueSilently("slug");
        switch (slug) {
            case "inbox":
                filter.setAssigneeUserId(session.getUser().getId());
                break;
            case "my":
                filter.setAuthor((User) session.getUser());
                break;
            case "initiative":
                filter.setInitiative(true);
                break;
            case "moderate":
                filter.setModerate(true);
                break;
            default:
                break;
        }

        if (formData.containsField("tags")) {
            List<Tag> tags = new ArrayList<>();
            String[] tagIds = formData.getListOfValuesSilently("tags");
            for (String tid : tagIds) {
                Tag tag = new Tag();
                tag.setId(UUID.fromString(tid));
                tags.add(tag);
            }
            filter.setTags(tags);
        }

        filter.setParentOnly(formData.getBoolSilently("parentOnly"));
        filter.setTreeMode(formData.getBoolSilently("isTreeMode"));

        return filter;
    }

    @POST
    @Path("action/im/slack/{command}")
    @Defended(false)
    @Produces({"application/json"})
    public Response processSlackCommandService(@PathParam("command") String command) {
        _Session ses = new _Session(new SuperUser());
        UserDAO userDAO = new UserDAO(ses);
        Outcome outcome = new Outcome();
        outcome.setId(command);
        List<Task> tasks = new ArrayList<>();

        try {
            TaskDAO dao = new TaskDAO(ses);
            TaskFilter filter = new TaskFilter();
            filter.setAssigneeUserId(userDAO.findByLogin("test9").getId());
            tasks.addAll(dao.findAllByTaskFilter(filter.setStatus(StatusType.PROCESSING)));
            tasks.addAll(dao.findAllByTaskFilter(filter.setStatus(StatusType.OPEN)));

        } catch (DAOException e) {
            Lg.exception(e);
        }

        StringJoiner tasksAsText = new StringJoiner("\n");
        for (Task t : tasks) {
            tasksAsText.add(t.getTitle());
        }

        String val = EnvConst.FRAMEWORK_NAME + " ver." + EnvConst.SERVER_VERSION + "(" + Server.compilationTime + ")";

        String result = "{\"text\": \"Projects\"," +
                "\"mrkdwn\":true," +
                "\"attachments\": [{\"text\":\"" + val + "\"}]" +
                "}";

        return Response.status(200).entity(result).build();
    }
}
