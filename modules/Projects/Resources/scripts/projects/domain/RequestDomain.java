package projects.domain;

import administrator.model.User;
import com.exponentus.common.domain.CommonDomain;
import com.exponentus.common.domain.IValidation;
import com.exponentus.common.ui.ACL;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.env.Environment;
import com.exponentus.rest.outgoingdto.Outcome;
import com.exponentus.rest.validation.exception.DTOException;
import com.exponentus.rest.validation.exception.DTOExceptionType;
import com.exponentus.scripting._Session;
import com.exponentus.util.StringUtil;
import projects.model.Request;
import projects.model.Task;
import projects.model.constants.ResolutionType;

import java.util.Date;

import static projects.model.constants.ResolutionType.ACCEPTED;
import static projects.model.constants.ResolutionType.DECLINED;

public class RequestDomain extends CommonDomain<Request> {

    public RequestDomain(_Session ses) {
        super(ses);
    }

    public Request composeNew(User author, Task task) {
        Request request = new Request();

        request.setAuthor(author);
        request.setTask(task);

        return request;
    }

    @Override
    public Request fillFromDto(Request dto, IValidation<Request> validation, String formSesId) throws DTOException, DAOException {
        return null;
    }

    public void fillFromDto(Request request, Request dto) {
        request.setAuthor(dto.getAuthor());
        request.setLastModifier(dto.getAuthor());
        request.setRequestType(dto.getRequestType());
        request.setComment(dto.getComment());
        request.setAttachments(dto.getAttachments());
        request.setTitle(dto.getRequestType().getTitle() + " " + dto.getComment());
        if (request.isNew()) {
            request.setTask(dto.getTask());
            request.resetReadersEditors();
            request.addReaderEditor(request.getAuthor());
            request.addReadersList(dto.getTask().getEditors());
        }
    }

    public boolean userCanDoResolution(Request request, User user) {
        if (request.isNew()) {
            return false;
        }

        ResolutionType rt = request.getResolution();
        long taskAuthorId = request.getTask().getAuthor().getId();

        return (rt != ACCEPTED && rt != DECLINED) && taskAuthorId == user.getId();
    }

//    public void doResolution(Request request, User user, ResolutionType resolutionType, String decisionComment) throws DTOException {
//        if (!userCanDoResolution(request, user)) {
//            throw new DTOException(DTOExceptionType.IMPROPER_CONDITION, "User " + user.getLogin() + " can not do resolution or request already resolved. Current resolution: " + request.getResolution());
//        }
//
//        request.setResolution(resolutionType);
//        request.setResolutionTime(new Date());
//        request.setDecisionComment(decisionComment);
//    }

    public void doAcceptRequest(Request request) throws DTOException {
        if (!userCanDoResolution(request, (User) ses.getUser())) {
            throw new DTOException(DTOExceptionType.IMPROPER_CONDITION, "User " + ses.getUser().getLogin() + " can not do resolution or request already resolved. Current resolution: " + request.getResolution());
        }

        request.setResolution(ResolutionType.ACCEPTED);
        request.setResolutionTime(new Date());
    }

    public void doDeclineRequest(Request request, String decisionComment) throws DTOException {
        if (!userCanDoResolution(request, (User) ses.getUser())) {
            throw new DTOException(DTOExceptionType.IMPROPER_CONDITION, "User " + ses.getUser().getLogin() + " can not do resolution or request already resolved. Current resolution: " + request.getResolution());
        }

        request.setResolution(ResolutionType.DECLINED);
        request.setResolutionTime(new Date());
        request.setDecisionComment(decisionComment);
    }

    public Outcome getOutcome(Request request) {
        Outcome outcome = new Outcome();

        if (StringUtil.isEmpty(request.getTitle())) {
            outcome.setTitle(Environment.vocabulary.getWord("task_request", ses.getLang()) + " " + request.getTitle());
        } else {
            outcome.setTitle(request.getTitle());
        }
        outcome.setModel(request);
        outcome.addPayload(request.getTask());
        outcome.addPayload("contentTitle", "task_request");

        if (!request.isNew()) {
            outcome.addPayload(new ACL(request));
        }

        return outcome;
    }
}