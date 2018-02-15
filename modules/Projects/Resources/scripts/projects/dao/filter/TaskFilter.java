package projects.dao.filter;

import administrator.model.User;
import com.exponentus.common.model.constants.PriorityType;
import com.exponentus.common.model.constants.StatusType;
import com.exponentus.dataengine.IFilter;
import projects.model.Project;
import projects.model.Task;
import reference.model.Tag;
import reference.model.TaskType;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class TaskFilter implements IFilter<Task> {

    private Project project;
    private Task parentTask;
    private TaskType taskType;
    private StatusType status = StatusType.UNKNOWN;
    private PriorityType priority = PriorityType.UNKNOWN;
    private String search;
    private User author;
    private Long assigneeUserId;
    private Date startDate;
    private Date dueDate;
    private List<Tag> tags;
    private boolean isParentOnly;
    private Boolean isInitiative = null;
    private boolean treeMode = false;
    private boolean isModerate;

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public TaskFilter setProject(String projectId) {
        if (projectId != null && !projectId.isEmpty()) {
            Project project = new Project();
            project.setId(UUID.fromString(projectId));
            setProject(project);
        }
        return this;
    }

    public Task getParentTask() {
        return parentTask;
    }

    public TaskFilter setParentTask(Task parentTask) {
        this.parentTask = parentTask;
        return this;
    }

    public TaskFilter setParentTask(String parentTaskId) {
        if (parentTaskId != null && !parentTaskId.isEmpty()) {
            Task task = new Task();
            task.setId(UUID.fromString(parentTaskId));
            setParentTask(task);
        }
        return this;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TaskFilter setTaskType(TaskType taskType) {
        this.taskType = taskType;
        return this;
    }

    public TaskFilter setTaskType(String id) {
        if (id != null && !id.isEmpty()) {
            TaskType tt = new TaskType();
            tt.setId(UUID.fromString(id));
            setTaskType(tt);
        }
        return this;
    }

    public StatusType getStatus() {
        return status;

    }

    public TaskFilter setStatus(StatusType status) {
        this.status = status;
        return this;
    }

    public PriorityType getPriority() {
        return priority;
    }

    public TaskFilter setPriority(PriorityType priority) {
        this.priority = priority;
        return this;
    }

    public boolean hasSearch() {
        return search != null && !search.isEmpty();
    }

    public String getSearch() {
        return search;
    }

    public TaskFilter setSearch(String search) {
        this.search = search;
        return this;
    }

    public User getAuthor() {
        return author;
    }

    public TaskFilter setAuthor(User author) {
        this.author = author;
        return this;
    }

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public TaskFilter setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
        return this;
    }

    public Date getStartDate() {
        return startDate;
    }

    public TaskFilter setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    public Date getDueDate() {
        return dueDate;
    }

    public TaskFilter setDueDate(Date dueDate) {
        this.dueDate = dueDate;
        return this;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public TaskFilter setTags(List<Tag> tags) {
        this.tags = tags;
        return this;
    }

    public boolean isParentOnly() {
        return isParentOnly;
    }

    public TaskFilter setParentOnly(boolean parentOnly) {
        isParentOnly = parentOnly;
        return this;
    }

    public Boolean isInitiative() {
        return isInitiative;
    }

    public TaskFilter setInitiative(boolean initiative) {
        isInitiative = initiative;
        return this;
    }

    public boolean isTreeMode() {
        return treeMode;
    }

    public TaskFilter setTreeMode(boolean treeMode) {
        this.treeMode = treeMode;
        return this;
    }

    public boolean isModerate() {
        return isModerate;
    }

    public void setModerate(boolean moderate) {
        isModerate = moderate;
    }

    @Override
    public Predicate collectPredicate(Root<Task> root, CriteriaBuilder cb, Predicate condition) {
        return condition;
    }
}