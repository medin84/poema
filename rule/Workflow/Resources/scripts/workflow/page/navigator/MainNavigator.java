package workflow.page.navigator;

import com.exponentus.scripting._Session;
import com.exponentus.scripting._WebFormData;
import com.exponentus.scripting.event._DoPage;
import com.exponentus.scripting.outline._Outline;
import com.exponentus.scripting.outline._OutlineEntry;
import com.exponentus.scriptprocessor.page.IOutcomeObject;

import java.util.Collection;
import java.util.LinkedList;

public class MainNavigator extends _DoPage {

    @Override
    public void doGET(_Session session, _WebFormData formData) {
        Collection<IOutcomeObject> list = new LinkedList<>();

        _Outline common_outline = new _Outline(getLocalizedWord("workflow", session.getLang()), "common");
        common_outline.addEntry(new _OutlineEntry(getLocalizedWord("office_memo_plural", session.getLang()), "", "office-memos", "office-memos"));
        common_outline.addEntry(new _OutlineEntry(getLocalizedWord("incoming_documents", session.getLang()), "", "incomings", "incomings"));
        common_outline.addEntry(new _OutlineEntry(getLocalizedWord("outgoing_documents", session.getLang()), "", "outgoings", "outgoings"));

        list.add(common_outline);

        addContent(list);
    }
}
