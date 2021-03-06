package workflow.model;

import com.exponentus.common.model.Attachment;
import com.exponentus.common.model.embedded.ExtendedAttachment;
import com.exponentus.common.model.embedded.TimeLine;
import com.exponentus.common.ui.lifecycle.ILifeCycle;
import com.exponentus.common.ui.lifecycle.LifeCycleNode;
import com.exponentus.common.ui.lifecycle.LifeCycleNodeType;
import com.exponentus.dataengine.jpadatabase.ftengine.FTSearchable;
import com.exponentus.user.IUser;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.persistence.annotations.CascadeOnDelete;
import reference.model.DocumentLanguage;
import reference.model.DocumentSubject;
import reference.model.DocumentType;
import reference.model.Tag;
import staff.model.Employee;
import staff.model.Organization;
import staff.model.embedded.Observer;
import workflow.init.ModuleConst;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = ModuleConst.CODE + "__incomings")
@Inheritance(strategy = InheritanceType.JOINED)
public class Incoming extends ActionableDocument implements ILifeCycle {

    @FTSearchable
    @Column(name = "reg_number", unique = true, length = 64)
    private String regNumber;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "applied_reg_date")
    private Date appliedRegDate;

    private Organization sender;

    @FTSearchable
    @Column(name = "sender_reg_number", length = 64)
    private String senderRegNumber;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "sender_applied_reg_date")
    private Date senderAppliedRegDate;

    private Employee addressee;

    private DocumentLanguage docLanguage;

    private DocumentType docType;

    private DocumentSubject docSubject;

    @FTSearchable
    @Column(columnDefinition = "TEXT")
    private String body;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(name = ModuleConst.CODE + "__incoming_attachments", joinColumns = {@JoinColumn(name = "incoming_id")}, inverseJoinColumns = {
            @JoinColumn(name = "attachment_id")}, indexes = {
            @Index(columnList = "incoming_id, attachment_id")}, uniqueConstraints = @UniqueConstraint(columnNames = {
            "incoming_id", "attachment_id"}))
    @CascadeOnDelete
    private List<Attachment> attachments = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = ModuleConst.CODE + "__incoming_observers", joinColumns = @JoinColumn(referencedColumnName = "id"))
    private List<Observer> observers = new ArrayList<Observer>();

    @ElementCollection
    //@MapKeyColumn(name = "real_file_name", length = 140)
    //@Column(name = "ext_attachment")
    @CollectionTable(name = ModuleConst.CODE + "__incoming_ext_attachments", joinColumns = @JoinColumn(referencedColumnName = "id"))
    private List<ExtendedAttachment> extAttachments = new ArrayList<ExtendedAttachment>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = ModuleConst.CODE + "__incoming_tags")
    private List<Tag> tags;

    public String getRegNumber() {
        return regNumber;
    }

    public void setRegNumber(String regNumber) {
        this.regNumber = regNumber;
    }

    public Date getAppliedRegDate() {
        return appliedRegDate;
    }

    public void setAppliedRegDate(Date appliedRegDate) {
        this.appliedRegDate = appliedRegDate;
    }

    public Organization getSender() {
        return sender;
    }

    public void setSender(Organization sender) {
        this.sender = sender;
    }

    public String getSenderRegNumber() {
        return senderRegNumber;
    }

    public void setSenderRegNumber(String senderRegNumber) {
        this.senderRegNumber = senderRegNumber;
    }

    public Date getSenderAppliedRegDate() {
        return senderAppliedRegDate;
    }

    public void setSenderAppliedRegDate(Date senderAppliedRegDate) {
        this.senderAppliedRegDate = senderAppliedRegDate;
    }

    public Employee getAddressee() {
        return addressee;
    }

    public void setAddressee(Employee addressee) {
        this.addressee = addressee;
    }

    @Override
    public List<Attachment> getAttachments() {
        return attachments;
    }

    @Override
    public void setAttachments(List<Attachment> attachments) {
        this.attachments = attachments;
    }

    public DocumentLanguage getDocLanguage() {
        return docLanguage;
    }

    public void setDocLanguage(DocumentLanguage docLanguage) {
        this.docLanguage = docLanguage;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public void setDocType(DocumentType docType) {
        this.docType = docType;
    }

    public DocumentSubject getDocSubject() {
        return docSubject;
    }

    public void setDocSubject(DocumentSubject docSubject) {
        this.docSubject = docSubject;
    }

    public List<Observer> getObservers() {
        return observers;
    }

    public void setObservers(List<Observer> observers) {
        this.observers = observers;
    }

    @Override
    public List<ExtendedAttachment> getExtAttachments() {
        return extAttachments;
    }

    public void setExtAttachments(List<ExtendedAttachment> extAttachments) {
        this.extAttachments = extAttachments;
    }

    public List<Tag> getTags() {
        return tags;
    }

    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @Override
    public String getURL() {
        return ModuleConst.BASE_URL + "incomings/" + getId();
    }

    @Override
    public LifeCycleNode getLifeCycle(IUser user, UUID id) {
        LifeCycleNode lc = getNode(user, id);
        List<Assignment> assignments = getAssignments();

        if (assignments != null) {
            for (Assignment a : assignments) {
                lc.addResponse(a.getNode(user, id));
            }
        }
        return lc;
    }

    @Override
    public LifeCycleNode getNode(IUser user, UUID id) {
        LifeCycleNode lc = new LifeCycleNode();
        lc.setType(LifeCycleNodeType.ACTIONABLE);
        if (id.equals(this.id)) {
            lc.setCurrent(true);
        }

        if (user.isSuperUser() || getReaders().containsKey(user.getId())) {
            lc.setAvailable(true);
            lc.setTitle(getTitle());
            lc.setStatus(getApprovalStatus().name());
        }
        lc.setUrl(getURL());
        return lc;
    }

    @Override
    public TimeLine getTimeLine() {
        return null;
    }
}
