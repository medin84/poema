package projects.page.form;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.persistence.exceptions.DatabaseException;
import org.joda.time.LocalDate;

import com.exponentus.common.dao.AttachmentDAO;
import com.exponentus.common.model.ACL;
import com.exponentus.common.model.Attachment;
import com.exponentus.common.service.AttachmentThumbnailService;
import com.exponentus.dataengine.jpa.TempFile;
import com.exponentus.env.EnvConst;
import com.exponentus.exception.MsgException;
import com.exponentus.exception.SecureException;
import com.exponentus.localization.LanguageCode;
import com.exponentus.messaging.email.MailAgent;
import com.exponentus.messaging.email.Memo;
import com.exponentus.scripting.IPOJOObject;
import com.exponentus.scripting._Exception;
import com.exponentus.scripting._FormAttachments;
import com.exponentus.scripting._POJOListWrapper;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._Validation;
import com.exponentus.scripting._WebFormData;
import com.exponentus.scripting.event._DoForm;
import com.exponentus.user.IUser;
import com.exponentus.user.SuperUser;
import com.exponentus.util.TimeUtil;
import com.exponentus.webserver.servlet.UploadedFile;

import administrator.dao.UserDAO;
import administrator.model.User;
import projects.dao.ProjectDAO;
import projects.dao.TaskDAO;
import projects.model.Task;
import projects.model.constants.TaskPriorityType;
import projects.model.constants.TaskStatusType;
import projects.other.Messages;
import reference.dao.TagDAO;
import reference.dao.TaskTypeDAO;
import reference.model.Tag;
import reference.model.TaskType;

public class TaskForm extends _DoForm {

	@Override
	public void doGET(_Session session, _WebFormData formData) {
		IUser<Long> user = session.getUser();
		Task task;
		String id = formData.getValueSilently("taskId");

		if (!id.isEmpty()) {
			TaskDAO dao = new TaskDAO(session);
			task = dao.findById(id);

			if (formData.containsField("attachment")) {
				doGetAttachment(session, formData, task);
				return;
			}

			addContent(task.getAttachments());
			addContent(new ACL(task));
		} else {
			task = new Task();
			task.setAuthor(user);
			TaskTypeDAO tDao = new TaskTypeDAO(session);
			task.setTaskType(tDao.findByName("Programming"));
			task.setStatus(TaskStatusType.OPEN);
			task.setStartDate(new Date());
			task.setDueDate(LocalDate.now().plusDays(10).toDate());
			String fsId = formData.getValueSilently(EnvConst.FSID_FIELD_NAME);

			List<String> formFiles = null;
			Object obj = session.getAttribute(fsId);
			if (obj == null) {
				formFiles = new ArrayList<>();
			} else {
				// formFiles = (List<String>) obj;
				_FormAttachments fAtts = (_FormAttachments) obj;
				formFiles = fAtts.getFiles().stream().map(TempFile::getRealFileName).collect(Collectors.toList());
			}

			List<IPOJOObject> filesToPublish = new ArrayList<>();

			for (String fn : formFiles) {
				UploadedFile uf = (UploadedFile) session.getAttribute(fsId + "_file" + fn);
				if (uf == null) {
					uf = new UploadedFile();
					uf.setName(fn);
					session.setAttribute(fsId + "_file" + fn, uf);
				}
				filesToPublish.add(uf);
			}
			addContent(new _POJOListWrapper<>(filesToPublish, session));
		}

		addContent(task);
	}

	@Override
	public void doPOST(_Session session, _WebFormData formData) {
		devPrint(formData);
		LanguageCode lang = session.getLang();
		try {
			String parentTaskId = formData.getValueSilently("parentTaskId");
			boolean isSubTask = !parentTaskId.isEmpty();

			_Validation ve = validate(formData, lang, isSubTask);
			if (ve.hasError()) {
				setBadRequest();
				setValidation(ve);
				return;
			}

			UserDAO userDAO = new UserDAO(session);
			ProjectDAO projectDAO = new ProjectDAO(session);
			TaskTypeDAO taskTypeDAO = new TaskTypeDAO(session);
			TaskDAO dao = new TaskDAO(session);
			Task parentTask = null;
			Task task;
			TaskType taskType = null;
			String id = formData.getValueSilently("taskId");
			boolean isNew = id.isEmpty();

			if (isNew) {
				task = new Task();
				if (isSubTask) {
					parentTask = dao.findById(parentTaskId);
					task.setParent(parentTask);
					taskType = parentTask.getTaskType();
					task.setProject(parentTask.getProject());
				}
				task.setAuthor(session.getUser());
				task.setStatus(TaskStatusType.OPEN);
			} else {
				task = dao.findById(id);
			}

			if (!isSubTask) {
				task.setProject(projectDAO.findById(formData.getValue("projectId")));
				taskType = taskTypeDAO.findById(formData.getValue("taskTypeId"));
			}
			task.setTitle(formData.getValue("title"));
			task.setTaskType(taskType);
			task.setPriority(TaskPriorityType.valueOf(formData.getValueSilently("priority")));
			task.setStartDate(TimeUtil.convertStringToDate(formData.getValueSilently("startDate")));
			task.setDueDate(TimeUtil.convertStringToDate(formData.getValueSilently("dueDate")));
			if (task.getStatus() == TaskStatusType.OPEN || task.getStatus() == TaskStatusType.WAITING) {
				if (new Date().before(task.getStartDate())) {
					task.setStatus(TaskStatusType.WAITING);
				} else {
					task.setStatus(TaskStatusType.PROCESSING);
				}
			}
			task.setBody(formData.getValueSilently("body"));
			IUser<Long> assigneeUser = userDAO.findById(formData.getNumberValueSilently("assigneeUserId", 0));
			task.setAssignee(assigneeUser.getId());
			task.setAttachments(getActualAttachments(task.getAttachments()));
			task.setCustomerObservation(Boolean.valueOf(formData.getValue("customerObservation")));

			if (formData.containsField("tagIds")) {
				if (formData.getValueSilently("tagIds").isEmpty()) {
					task.setTags(new ArrayList<>());
				} else {
					String[] tagIds = formData.getValueSilently("tagIds").split(",");
					if (tagIds.length > 0) {
						TagDAO tagDAO = new TagDAO(session);
						List<UUID> tagUIds = Arrays.stream(tagIds).map(UUID::fromString).collect(Collectors.toList());
						List<Tag> tags = tagDAO.findAllByIds(tagUIds, 0, 0).getResult();
						task.setTags(tags);
					}
				}
			}

			if (isNew) {
				IUser<Long> user = session.getUser();
				task.addReaderEditor(user);
				task.addReader(assigneeUser);
				task.addReaders(task.getProject().getObservers());
				RegNum rn = new RegNum();
				task.setRegNumber(taskType.getPrefix() + rn.getRegNumber(taskType.prefix));
				task = dao.add(task);
				rn.post();
			} else {
				task = dao.update(task);
			}

			if (isNew && task.getStatus() == TaskStatusType.PROCESSING) {
				Messages.sendToAssignee(session, task);
			}
		} catch (SecureException e) {
			setBadRequest();
			logError(e);
		} catch (_Exception | DatabaseException e) {
			setBadRequest();
			logError(e);
		} catch (MsgException e) {
			logError(e);
		}
	}

	@Override
	public void doPUT(_Session session, _WebFormData formData) {
		String taskId = formData.getValueSilently("taskId");
		String action = formData.getValueSilently("_action");

		if (taskId.isEmpty() || action.isEmpty()) {
			addContent("error", "taskId or _action empty");
			setBadRequest();
			return;
		}

		switch (action) {
		case "complete":
			doTaskComplete(session, taskId);
			break;
		case "cancel":
			doTaskCancel(session, taskId, formData.getValueSilently("comment"));
			break;
		case "acknowledged":
			doAcknowledged(session, taskId);
			break;
		default:
			addValue("error", "unknown action");
			break;
		}
	}

	@Override
	public void doDELETE(_Session session, _WebFormData formData) {
		String id = formData.getValueSilently("taskId");
		String attachmentId = formData.getValueSilently("attachmentId");

		if (id.isEmpty() || attachmentId.isEmpty()) {
			addContent("error", "taskId or attachmentId empty");
			return;
		}

		TaskDAO dao = new TaskDAO(session);
		Task task = dao.findById(id);

		AttachmentDAO attachmentDAO = new AttachmentDAO(session);
		Attachment attachment = attachmentDAO.findById(attachmentId);
		task.getAttachments().remove(attachment);

		try {
			dao.update(task);
		} catch (SecureException e) {
			setBadRequest();
			logError(e);
		}
	}

	private _Validation validate(_WebFormData formData, LanguageCode lang, boolean isSubTask) {
		_Validation ve = new _Validation();

		if (!isSubTask && formData.getValueSilently("projectId").isEmpty()) {
			ve.addError("projectId", "required", getLocalizedWord("field_is_empty", lang));
		}
		if (!isSubTask && formData.getValueSilently("taskTypeId").isEmpty()) {
			ve.addError("taskTypeId", "required", getLocalizedWord("field_is_empty", lang));
		}
		if (formData.getValueSilently("title").isEmpty()) {
			ve.addError("title", "required", getLocalizedWord("field_is_empty", lang));
		} else if (formData.getValueSilently("title").length() > 140) {
			ve.addError("title", "maxlen_140", getLocalizedWord("field_is_too_long", lang));
		}
		if (formData.getValueSilently("body").length() > 10000) {
			ve.addError("body", "maxlen_10000", getLocalizedWord("field_is_too_long", lang));
		}
		if (formData.getValueSilently("status").isEmpty()) {
			ve.addError("status", "required", getLocalizedWord("field_is_empty", lang));
		}
		if (formData.getValueSilently("priority").isEmpty()) {
			ve.addError("priority", "required", getLocalizedWord("field_is_empty", lang));
		}

		String sDate = formData.getValueSilently("startDate");
		if (sDate.isEmpty()) {
			ve.addError("startDate", "required", getLocalizedWord("field_is_empty", lang));
		} else if (TimeUtil.convertStringToDate(sDate) == null) {
			ve.addError("startDate", "date", getLocalizedWord("date_format_does_not_match_to", lang) + " dd.MM.YYYY");
		}

		String dDate = formData.getValueSilently("startDate");
		if (dDate.isEmpty()) {
			ve.addError("dueDate", "required", getLocalizedWord("field_is_empty", lang));
		} else if (TimeUtil.convertStringToDate(dDate) == null) {
			ve.addError("dueDate", "date", getLocalizedWord("date_format_does_not_match_to", lang) + " dd.MM.YYYY");
		}

		if (formData.getNumberValueSilently("assigneeUserId", 0) == 0) {
			ve.addError("assigneeUserId", "required", getLocalizedWord("field_is_empty", lang));
		}

		return ve;
	}

	private void doGetAttachment(_Session session, _WebFormData formData, Task task) {
		String attachmentId = formData.getValueSilently("attachment");
		Attachment att = task.getAttachments().stream().filter(it -> it.getIdentifier().equals(attachmentId)).findFirst().get();

		if (formData.containsField("_thumbnail")) {
			File tf = AttachmentThumbnailService.createThumbnailFileIfSupported(session, att);
			if (tf != null) {
				showFile(tf.getAbsolutePath(), att.getRealFileName());
			} else {
				setBadRequest();
				addContent("error", "Unsupported format, try without _thumbnail");
			}
		} else if (showAttachment(attachmentId, task)) {
			//
		} else {
			setBadRequest();
		}
	}

	private void doTaskComplete(_Session session, String taskId) {
		TaskDAO dao = new TaskDAO(session);
		Task task = dao.findById(taskId);

		try {
			if (task.getStatus() == TaskStatusType.COMPLETED) {
				addContent("info", "task status is finished");
				return;
			}

			task.setStatus(TaskStatusType.COMPLETED);
			dao.update(task);

			Messages.sendOfTaskCompleted(session, task);

		} catch (SecureException e) {
			setBadRequest();
			logError(e);
		} catch (DatabaseException e) {
			setBadRequest();
			logError(e);
		} catch (MsgException e) {
			setBadRequest();
			logError(e);
		}
	}

	private void doTaskCancel(_Session session, String taskId, String comment) {
		TaskDAO dao = new TaskDAO(new _Session(session.getAppEnv(), new SuperUser()));
		Task task = dao.findById(taskId);

		try {
			if (task.getStatus() == TaskStatusType.CANCELLED) {
				addContent("info", "task status is cancelled");
				return;
			}

			task.setStatus(TaskStatusType.CANCELLED);
			task.setCancellationComment(comment);
			dao.update(task);

			LanguageCode lang = session.getLang();
			UserDAO userDAO = new UserDAO(session);
			IUser<Long> assigneeUser = userDAO.findById(task.getAssignee());

			List<String> recipients = new ArrayList<>();
			recipients.add(((User) assigneeUser).getEmail());

			MailAgent ma = new MailAgent();
			Memo memo = new Memo();
			memo.addVar("taskTitle", task.getTitle());
			memo.addVar("url", session.getAppEnv().getURL() + "/" + task.getURL());
			if (ma.sendMеssage(recipients, getLocalizedWord("notify_about_cancel_task", lang),
			        memo.getBody(getLocalizedEmailTemplate("task_cancel", lang)))) {
				addValue("notify", "ok");
			}
		} catch (SecureException e) {
			setBadRequest();
			logError(e);
		} catch (DatabaseException e) {
			setBadRequest();
			logError(e);
		} catch (MsgException e) {
			setBadRequest();
			logError(e);
		}
	}

	private void doAcknowledged(_Session session, String taskId) {
		TaskDAO dao = new TaskDAO(new _Session(session.getAppEnv(), new SuperUser()));
		Task task = dao.findById(taskId);

		try {
			if (!task.getAssignee().equals(session.getUser().getId())) {
				addContent("error", "not_assignee_user");
				setBadRequest();
				return;
			} else if (task.getStatus() != TaskStatusType.OPEN && task.getStatus() != TaskStatusType.WAITING) {
				addContent("error", "task_status_is_not_open");
				setBadRequest();
				return;
			}

			task.setStatus(TaskStatusType.PROCESSING);
			dao.update(task);

			Messages.sendOfNewAcknowledging(session, task);

		} catch (SecureException e) {
			setBadRequest();
			logError(e);
		} catch (DatabaseException e) {
			setBadRequest();
			logError(e);
		} catch (MsgException e) {
			setBadRequest();
			logError(e);
		}
	}
}
