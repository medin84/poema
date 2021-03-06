package workflow.domain;

import administrator.model.User;
import com.exponentus.common.domain.ApprovalLifecycle;
import com.exponentus.common.domain.IValidation;
import com.exponentus.common.model.constants.ApprovalResultType;
import com.exponentus.common.model.constants.ApprovalSchemaType;
import com.exponentus.common.model.constants.ApprovalStatusType;
import com.exponentus.common.model.embedded.Block;
import com.exponentus.common.ui.ACL;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.env.Environment;
import com.exponentus.exception.SecureException;
import com.exponentus.rest.outgoingdto.Outcome;
import com.exponentus.rest.validation.exception.DTOException;
import com.exponentus.runtimeobj.RegNum;
import com.exponentus.scripting._Session;
import com.exponentus.user.IUser;
import com.exponentus.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import staff.dao.EmployeeDAO;
import staff.model.embedded.Observer;
import workflow.dao.OutgoingDAO;
import workflow.model.Outgoing;

import java.util.*;

@Deprecated
public class OutgoingDomain extends ApprovalDomain<Outgoing> {

    public OutgoingDomain(_Session session) throws DAOException {
        super(session);
        dao = new OutgoingDAO(ses);
    }

    public Outgoing composeNew(User user) {
        Outgoing entity = new Outgoing();
        entity.setAppliedRegDate(new Date());
        return entity;
    }

    @Override
    public Outgoing fillFromDto(Outgoing dto, IValidation<Outgoing> validation, String fsid) throws DTOException, DAOException {
        validation.check(dto);

        Outgoing entity = getEntity(dto);
        EmployeeDAO eDao = new EmployeeDAO(ses);
        entity.setAppliedRegDate(dto.getAppliedRegDate());
        String title = dto.getTitle();
        if (title == null || title.isEmpty()) {
            title = StringUtils.abbreviate(StringUtil.cleanFromMarkdown(dto.getBody()), 140);
        }
        entity.setTitle(title);
        entity.setDocSubject(dto.getDocSubject());
        entity.setDocLanguage(dto.getDocLanguage());
        entity.setDocType(dto.getDocType());
        entity.setRecipient(dto.getRecipient());
        entity.setBody(dto.getBody());
        entity.setRecipient(dto.getRecipient());
        entity.setBlocks(normalizeBlocks(eDao, dto.getBlocks()));
        IUser user = ses.getUser();
        if (user.getRoles().contains("chancellery")) {
            entity.setApprovalSchema(ApprovalSchemaType.WITHOUT_APPROVAL);
            entity.setApprovalStatus(ApprovalStatusType.REGISTERED);
            entity.setResult(ApprovalResultType.ACCEPTED);
        } else {
            entity.setApprovalSchema(dto.getApprovalSchema());
            entity.setApprovalStatus(ApprovalStatusType.DRAFT);
        }

        List<Observer> observers = new ArrayList<Observer>();
        for (Observer o : dto.getObservers()) {
            Observer observer = new Observer();
            observer.setEmployee(eDao.findById(o.getEmployee().getId()));
            observers.add(observer);
        }
        entity.setObservers(observers);

        if (entity.isNew()) {
            entity.setVersion(1);
            entity.setVersionsSupport(true);
            entity.setAuthor(user);
        }

        dto.setAttachments(getActualAttachments(entity.getAttachments(), dto.getAttachments(), fsid));
        calculateReadersEditors(entity);
        return entity;
    }

    @Override
    public void calculateReadersEditors(Outgoing entity) {
        if (entity.getApprovalStatus() == ApprovalStatusType.DRAFT) {
            entity.addReaderEditor(entity.getAuthor());
        } else {
            entity.withdrawEditor(entity.getAuthor());
        }
        List<Observer> observers = entity.getObservers();
        if (observers != null) {
            for (Observer observer : observers) {
                entity.addReader(observer.getEmployee().getUserID());
            }
        }
    }

    @Override
    public Outgoing save(Outgoing entity) throws SecureException, DAOException, DTOException {
        if (entity.isNew()) {
            RegNum rn = new RegNum();
            entity.setRegNumber(Integer.toString(rn.getRegNumber(entity.getDefaultFormName())));
            entity = dao.add(entity, rn);
        } else {
            entity = dao.update(entity);
        }
        return entity;
    }

    public Outgoing register(Outgoing dto, IValidation<Outgoing> validation) throws DAOException {
        Outgoing entity = getEntity(dto);
        entity.setApprovalStatus(ApprovalStatusType.REGISTERED);
        return entity;

    }

    @Override
    public Outcome getOutcome(Outgoing entity) {
        Outcome outcome = new Outcome();

        String entityKind = Environment.vocabulary.getWord("outgoing_document", ses.getLang());
        if (StringUtil.isEmpty(entity.getTitle())) {
            outcome.setTitle(entityKind);
        } else {
            outcome.setTitle(entityKind + " " + entity.getTitle());
        }
        outcome.setModel(entity);
        outcome.setPayloadTitle("outgoing_document");
        if (!entity.isNew()) {
            outcome.addPayload(new ACL(entity));
            Block block = ApprovalLifecycle.getProcessingBlock(entity);
            if (block != null) {
                Map<String, Boolean> flags = new HashMap<>();
                flags.put("approvalProcessingBlockRequireCommentIfNo", block.isRequireCommentIfNo());
                outcome.addPayload("flag", flags);
            }
        }

        return outcome;
    }
}
