package workflow.dto;

import workflow.init.AppConst;
import workflow.model.embedded.Control;

import java.util.UUID;

public class AssignmentViewDTO {

    public UUID id;
    public String kind = "assignment";

    public String appliedAuthor;
    public String body;
    public ControlDTO control;

    public AssignmentViewDTO(UUID id, String appliedAuthor, String body, Control control) {
        this.id = id;
        this.appliedAuthor = appliedAuthor;
        this.body = body;
        this.control = new ControlDTO(control);
    }

    public String getURL() {
        return AppConst.BASE_URL + "assignments/" + id;
    }
}
