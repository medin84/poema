package workflow.dao;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import com.exponentus.dataengine.jpa.DAO;
import com.exponentus.dataengine.jpa.SecureAppEntity;
import com.exponentus.dataengine.jpa.ViewPage;
import com.exponentus.runtimeobj.IAppEntity;
import com.exponentus.scripting._Session;
import com.exponentus.scripting._SortParams;

import workflow.model.Assignment;
import workflow.model.Incoming;
import workflow.model.Report;

public class IncomingDAO extends DAO<Incoming, UUID> {
	
	public IncomingDAO(_Session session) {
		super(Incoming.class, session);
	}
	
	public ViewPage<Incoming> findAllWithResponses(_SortParams sortParams, int pageNum, int pageSize,
			List<UUID> expandedIds) {
		ViewPage<Incoming> vp = findViewPage(sortParams, pageNum, pageSize);
		
		if (vp.getResult().isEmpty()) {
			return vp;
		}
		
		EntityManager em = getEntityManagerFactory().createEntityManager();
		try {
			for (Incoming incoming : vp.getResult()) {
				List<IAppEntity> responses = findIncomingResponses(incoming, expandedIds, em);
				if (responses != null && responses.size() > 0) {
					incoming.setResponsesCount((long) responses.size());
					incoming.setResponses(responses);
				}
			}
		} finally {
			em.close();
		}
		
		return vp;
	}
	
	private List<IAppEntity> findIncomingResponses(Incoming incoming, List<UUID> expandedIds, EntityManager em) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Assignment> cq = cb.createQuery(Assignment.class);
		Root<Assignment> root = cq.from(Assignment.class);
		cq.select(root).distinct(true);
		
		Predicate condition = cb.equal(root.get("incoming"), incoming);
		condition = cb.and(cb.isEmpty(root.get("parent")), condition);
		
		if (!user.isSuperUser() && SecureAppEntity.class.isAssignableFrom(Assignment.class)) {
			condition = cb.and(root.get("readers").in(user.getId()), condition);
		}
		
		cq.where(condition);
		cq.orderBy(cb.desc(root.get("regDate")));
		
		TypedQuery<Assignment> typedQuery = em.createQuery(cq);
		List<Assignment> assignments = typedQuery.getResultList();
		
		if (assignments.size() > 0) {
			for (Assignment assignment : assignments) {
				List<IAppEntity> responses = findAssignmentResponses(assignment, expandedIds, em);
				if (responses != null && responses.size() > 0) {
					assignment.setResponsesCount((long) responses.size());
					assignment.setResponses(responses);
				}
			}
			return new ArrayList<>(assignments);
		}
		return null;
	}
	
	private List<IAppEntity> findAssignmentResponses(Assignment assignment, List<UUID> expandedIds, EntityManager em) {
		// --- Assignment
		CriteriaBuilder cba = em.getCriteriaBuilder();
		CriteriaQuery<Assignment> cqa = cba.createQuery(Assignment.class);
		Root<Assignment> rootA = cqa.from(Assignment.class);
		cqa.select(rootA).distinct(true);
		
		Predicate conditionA = cba.equal(rootA.get("parent"), assignment);
		
		if (!user.isSuperUser() && SecureAppEntity.class.isAssignableFrom(Assignment.class)) {
			conditionA = cba.and(rootA.get("readers").in(user.getId()), conditionA);
		}
		
		cqa.where(conditionA);
		cqa.orderBy(cba.desc(rootA.get("regDate")));
		
		TypedQuery<Assignment> typedQueryA = em.createQuery(cqa);
		List<Assignment> assignments = typedQueryA.getResultList();
		
		// --- Report
		CriteriaBuilder cbr = em.getCriteriaBuilder();
		CriteriaQuery<Report> cqr = cbr.createQuery(Report.class);
		Root<Report> rootR = cqr.from(Report.class);
		cqr.select(rootR).distinct(true);
		
		Predicate conditionR = cbr.equal(rootR.get("parent"), assignment);
		
		if (!user.isSuperUser() && SecureAppEntity.class.isAssignableFrom(Report.class)) {
			conditionR = cbr.and(rootR.get("readers").in(user.getId()), conditionR);
		}
		
		cqr.where(conditionR);
		cqr.orderBy(cbr.desc(rootR.get("regDate")));
		
		TypedQuery<Report> typedQueryR = em.createQuery(cqr);
		List<Report> reports = typedQueryR.getResultList();
		
		// --- concat & sort by reg date
		List<IAppEntity> result = new LinkedList<>(assignments);
		result.addAll(reports);
		
		Supplier<List<IAppEntity>> supplier = LinkedList::new;
		result = result.stream().sorted((m1, m2) -> m1.getRegDate().after(m2.getRegDate()) ? 1 : -1)
				.collect(Collectors.toCollection(supplier));
		
		if (assignments.size() > 0) {
			for (Assignment a : assignments) {
				List<IAppEntity> responses = findAssignmentResponses(a, expandedIds, em);
				if (responses != null && responses.size() > 0) {
					a.setResponsesCount((long) responses.size());
					a.setResponses(responses);
				}
			}
		}
		
		return result;
	}
	
}
