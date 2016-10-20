package workflow.services.incoming;

import com.exponentus.dataengine.jpa.ViewPage;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.ServiceDescriptor;
import com.exponentus.rest.ServiceMethod;
import com.exponentus.rest.outgoingpojo.Outcome;
import com.exponentus.scripting._ColumnOptions;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._WebFormData;
import com.exponentus.scripting.actions._Action;
import com.exponentus.scripting.actions._ActionBar;
import com.exponentus.scripting.actions._ActionType;
import workflow.dao.IncomingDAO;

import javax.ws.rs.GET;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@Path("incomings")
public class IncomingsService extends RestProvider {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@Context UriInfo uriInfo) {
        _Session session = getSession();
        _WebFormData formData = new _WebFormData(uriInfo.getQueryParameters(), httpRequest.getHeader("referer"));
        int pageSize = session.pageSize;
        int pageNum = formData.getNumberValueSilently("page", 0);

        IncomingDAO incomingDAO = new IncomingDAO(session);
        ViewPage vp = incomingDAO.findViewPage(pageNum, pageSize); // formData.getSortParams(_SortParams.desc("regDate")),

        //
        _ActionBar actionBar = new _ActionBar(session);
        _Action newDocAction = new _Action("add_new", "", "new_incoming");
        newDocAction.setURL("new");
        actionBar.addAction(newDocAction);
        actionBar.addAction(new _Action("del_document", "", _ActionType.DELETE_DOCUMENT));

        // column options
        _ColumnOptions colOpts = new _ColumnOptions();
        colOpts.add("reg_number", "regNumber", "text", "both", "vw-reg-number");
        colOpts.add("title", "title", "text", "", "vw-title");
        colOpts.add("", "attachment", "icon", "", "vw-icon");
        colOpts.add("sender", "sender", "localizedName", "both", "vw-sender");
        colOpts.add("sender_applied_reg_date", "senderAppliedRegDate", "date", "both", "vw-date");
        colOpts.add("doc_type", "docType", "localizedName", "both", "vw-doc-type");
        colOpts.add("doc_language", "docLanguage", "localizedName", "both", "vw-name");
        colOpts.add("applied_reg_date", "appliedRegDate", "date", "both", "vw-reg-date");

        Outcome outcome = new Outcome();
        outcome.setId("incomings");
        outcome.addPayload("title", "incoming_documents");
        outcome.addPayload("actionBar", actionBar);
        outcome.addPayload("columnOptions", colOpts);
        outcome.addPayload("view", vp);

        return Response.ok(outcome).build();
    }

    @Override
    public ServiceDescriptor updateDescription(ServiceDescriptor sd) {
        sd.setName(getClass().getName());
        ServiceMethod m = new ServiceMethod();
        m.setMethod(HttpMethod.GET);
        m.setURL("/" + sd.getAppName() + sd.getUrlMapping() + "/incomings");
        sd.addMethod(m);
        return sd;
    }
}
