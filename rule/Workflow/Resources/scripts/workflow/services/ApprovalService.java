package workflow.services;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.exponentus.common.domain.IDTODomain;
import com.exponentus.common.service.EntityService;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.runtimeobj.IAppEntity;
import com.exponentus.scripting.actions.Action;
import com.exponentus.user.IUser;

import reference.model.constants.ApprovalType;
import staff.dao.EmployeeDAO;
import workflow.domain.ApprovalLifecycle;
import workflow.model.constants.ApprovalStatusType;
import workflow.model.embedded.IApproval;
import workflow.ui.ActionFactory;

public abstract class ApprovalService<A extends IApproval, T extends IAppEntity<UUID>, D extends IDTODomain<T>>
		extends EntityService<T, D> {

	protected List<Action> getApprovalKeySet(IUser<Long> user, A entity) throws DAOException {
		ActionFactory actionFactory = new ActionFactory();
		List<Action> keySet = new ArrayList<Action>();

		if (entity.getStatus() == ApprovalStatusType.DRAFT && user.equals(entity.getAuthor())) {
			keySet.add(actionFactory.startApproving);
		}

		EmployeeDAO employeeDAO = new EmployeeDAO(getSession());

		if (entity.userCanDoDecision(employeeDAO.findByUser(user))) {
			if (ApprovalLifecycle.getProcessingBlock(entity).getType() == ApprovalType.SIGNING) {
				keySet.add(actionFactory.signApprovalBlock);
			} else {
				keySet.add(actionFactory.acceptApprovalBlock);
			}
			keySet.add(actionFactory.declineApprovalBlock);
		}
		return keySet;
	}

}
