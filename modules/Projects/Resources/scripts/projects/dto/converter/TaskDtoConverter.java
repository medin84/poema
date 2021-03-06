package projects.dto.converter;

import com.exponentus.common.dto.converter.ExtConverter;
import com.exponentus.user.IUser;
import helpdesk.model.Demand;
import projects.model.Project;
import projects.model.Task;

public class TaskDtoConverter extends ExtConverter<Task, Task> {

    public TaskDtoConverter(IUser user) {
        super(user);
    }

    @Override
    public Task apply(Task task) {

        Task result = new Task();

        result.setId(task.getId());
        result.setAuthor(task.getAuthor());
        result.setTitle(task.getTitle());
        result.setRegNumber(task.getRegNumber());
        result.setRegDate(task.getRegDate());
        result.setResult(task.getApprovalResult());
        result.setApprovalStatus(task.getApprovalStatus());
        result.setStatus(task.getStatus());
        result.setPriority(task.getPriority());
        result.setAssignee(task.getAssignee());
        result.setStartDate(task.getStartDate());
        result.setDueDate(task.getDueDate());
        result.setWasRead(checkReadingState(task.getReaders()));

        if (task.getProject() != null) {
            Project project = new Project();
            project.setId(task.getProject().getId());
            project.setName(task.getProject().getName());
            result.setProject(project);
        }

        if (task.getDemand() != null) {
            Demand demand = new Demand();
            demand.setId(task.getDemand().getId());
            demand.setTitle(task.getDemand().getTitle());
            demand.setStatus(task.getDemand().getStatus());
            task.setDemand(demand);
        }

        result.setTags(task.getTags());
        result.setHasAttachments(task.getHasAttachments());

        return result;
    }
}
