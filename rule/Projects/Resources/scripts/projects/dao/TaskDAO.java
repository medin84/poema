package projects.dao;

import administrator.model.User;
import com.exponentus.common.dao.DAO;
import com.exponentus.common.model.SecureAppEntity;
import com.exponentus.common.ui.ViewPage;
import com.exponentus.dataengine.RuntimeObjUtil;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.dataengine.jpa.IAppEntity;
import com.exponentus.scripting.SortParams;
import com.exponentus.scripting._Session;
import com.exponentus.user.IUser;
import org.eclipse.persistence.config.HintValues;
import org.eclipse.persistence.config.QueryHints;
import projects.dao.filter.TaskFilter;
import projects.dto.TaskViewEntry;
import projects.dto.stat.CountStat;
import projects.model.Request;
import projects.model.Task;
import projects.model.constants.TaskPriorityType;
import projects.model.constants.TaskStatusType;
import com.exponentus.common.model.constants.ApprovalStatusType;
import workflow.model.embedded.Block;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TaskDAO extends DAO<Task, UUID> {

    public TaskDAO(_Session session) throws DAOException {
        super(Task.class, session);
    }

    public List<Task> findAllByTaskFilter(TaskFilter filter) {
        return findViewPage(filter, SortParams.desc("regDate"), 0, 0).getResult();
    }

    public ViewPage<Task> findViewPage(TaskFilter filter, SortParams sortParams, int pageNum, int pageSize) {
        if (filter == null) {
            throw new IllegalArgumentException("filter is null");
        }

        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<Task> cq = cb.createQuery(Task.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> taskRoot = cq.from(Task.class);

            Predicate condition = null;

            if (filter.getProject() != null) {
                condition = cb.equal(taskRoot.get("project"), filter.getProject());
            }

            if (filter.getAuthor() != null) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("author"), filter.getAuthor());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("author"), filter.getAuthor()), condition);
                }
            }

            if (filter.getStatus() != TaskStatusType.UNKNOWN) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("status"), filter.getStatus());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("status"), filter.getStatus()), condition);
                }
            }

            if (filter.getPriority() != TaskPriorityType.UNKNOWN) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("priority"), filter.getPriority());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("priority"), filter.getStatus()), condition);
                }
            }

            if (filter.getTaskType() != null) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("taskType"), filter.getTaskType());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("taskType"), filter.getTaskType()), condition);
                }
            }

            if (filter.getAssigneeUserId() != null) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("assignee"), filter.getAssigneeUserId());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("assignee"), filter.getAssigneeUserId()), condition);
                }
            }

            if (filter.isInitiative() != null) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("initiative"), filter.isInitiative());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("initiative"), filter.isInitiative()), condition);
                }
            }

            if (filter.getTags() != null) {
                if (condition == null) {
                    condition = cb.and(taskRoot.get("tags").in(filter.getTags()));
                } else {
                    condition = cb.and(taskRoot.get("tags").in(filter.getTags()), condition);
                }
            }

            if (filter.isModerate()) {
                Expression<ApprovalStatusType> status = taskRoot.<Collection<Block>>get("blocks").<ApprovalStatusType>get("status");
                Predicate predicate = cb.equal(status, ApprovalStatusType.PENDING);
                if (condition == null) {
                    condition = cb.and(predicate);
                } else {
                    condition = cb.and(predicate, condition);
                }
            }

            if (filter.hasSearch()) {
                if (condition == null) {
                    condition = cb.like(cb.lower(taskRoot.get("title")), "%" + filter.getSearch() + "%");
                } else {
                    condition = cb.and(cb.like(cb.lower(taskRoot.get("title")), "%" + filter.getSearch() + "%"), condition);
                }
            }

            //
            if (filter.getParentTask() != null) {
                if (condition == null) {
                    condition = cb.equal(taskRoot.get("parent"), filter.getParentTask());
                } else {
                    condition = cb.and(cb.equal(taskRoot.get("parent"), filter.getParentTask()), condition);
                }
            } else if (filter.isTreeMode() || filter.isParentOnly()) {
                if (condition == null) {
                    condition = cb.isEmpty(taskRoot.get("parent"));
                } else {
                    condition = cb.and(cb.isEmpty(taskRoot.get("parent")), condition);
                }
            }
            //

            if (!user.isSuperUser()) {
                Path<Set<Long>> readers = taskRoot.join("readers", JoinType.LEFT);
                Path<Set<Long>> observers = taskRoot.join("observers", JoinType.LEFT);
                Predicate readCondition = cb.or(readers.in(user.getId()), observers.in(user.getId()));
                if (condition == null) {
                    condition = readCondition;
                } else {
                    condition = cb.and(condition, readCondition);
                }
            }

            cq.select(taskRoot).distinct(true).orderBy(collectSortOrder(cb, taskRoot, sortParams));
            countCq.select(cb.countDistinct(taskRoot));


            if (condition != null) {
                cq.where(condition);
                countCq.where(condition);
            }

            TypedQuery<Task> typedQuery = em.createQuery(cq);
            Query query = em.createQuery(countCq);

            //TODO to test
            typedQuery.setHint(QueryHints.READ_ONLY, HintValues.TRUE);
            query.setHint(QueryHints.READ_ONLY, HintValues.TRUE);

            long count = (long) query.getSingleResult();
            int maxPage = pageable(typedQuery, count, pageNum, pageSize);

            List<Task> result = typedQuery.getResultList();

            return new ViewPage<>(result, count, maxPage, pageNum);
        } finally {
            em.close();
        }
    }

    public ViewPage<Task> findAllWithResponses(TaskFilter filter, SortParams sortParams, int pageNum, int pageSize,
                                               List<UUID> expandedIds) {
        ViewPage<Task> vp = findViewPage(filter, sortParams, pageNum, pageSize);

        if (vp.getResult().isEmpty() || !filter.isTreeMode()) {
            return vp;
        }

        EntityManager em = getEntityManagerFactory().createEntityManager();

        try {
            vp.getResult().forEach(task -> {
                List<IAppEntity<UUID>> responses = findTaskResponses(task, filter, expandedIds, em);
                if (responses != null && responses.size() > 0) {
                    task.setResponsesCount((long) responses.size());
                    task.setResponses(responses);
                }
            });
        } finally {
            em.close();
        }

        return vp;
    }

    public ViewPage<Task> findTaskExecution(Task task) {
        List<Task> list = new ArrayList<>();
        list.add(task);
        ViewPage<Task> vp = new ViewPage(list, 1, 1, 1);

        EntityManager em = getEntityManagerFactory().createEntityManager();

        try {
            List<IAppEntity<UUID>> responses = findTaskResponses(task, null, null, em);
            if (responses != null && responses.size() > 0) {
                task.setResponsesCount((long) responses.size());
                task.setResponses(responses);
            }
        } finally {
            em.close();
        }

        return vp;
    }

    private List<IAppEntity<UUID>> findTaskResponses(Task task, TaskFilter filter, List<UUID> expandedIds, EntityManager em) {

        List<Task> tasks;

        // Task
        CriteriaBuilder cbt = em.getCriteriaBuilder();
        CriteriaQuery<Task> cqt = cbt.createQuery(Task.class);
        Root<Task> taskRoot = cqt.from(Task.class);
        cqt.select(taskRoot).distinct(true);

        Predicate conditionA = cbt.equal(taskRoot.get("parent"), task);

        if (!user.isSuperUser() && SecureAppEntity.class.isAssignableFrom(Task.class)) {
            conditionA = cbt.and(taskRoot.get("readers").in(user.getId()), conditionA);
        }

        cqt.where(conditionA);
        cqt.orderBy(cbt.desc(taskRoot.get("regDate")));

        TypedQuery<Task> typedQueryT = em.createQuery(cqt);
        tasks = typedQueryT.getResultList();

        // Request
        CriteriaBuilder cbr = em.getCriteriaBuilder();
        CriteriaQuery<Request> cqr = cbr.createQuery(Request.class);
        Root<Request> requestRoot = cqr.from(Request.class);
        Join attCount = requestRoot.join("attachments", JoinType.LEFT);
        cqr.select(requestRoot).distinct(true);
        // TODO does not worked if choose hierarchy view
        /*
         * cqr.select(cbr.construct(Request.class, requestRoot.get("regDate"),
		 * requestRoot.get("author"),cbr.construct(ReportQueryType.class,
		 * requestRoot.get("requestType").get("name"),
		 * requestRoot.get("requestType").get("locName")),
		 * requestRoot.get("resolution"), requestRoot.get("resolutionTime"),
		 * requestRoot.get("decisionComment"), requestRoot.get("comment"),
		 * cbr.count(attCount)));
		 */

        Predicate conditionR = cbr.equal(requestRoot.get("task"), task);

        cqr.where(conditionR);
        // cqr.groupBy(requestRoot);
        cqr.orderBy(cbr.desc(requestRoot.get("regDate")));

        TypedQuery<Request> typedQueryR = em.createQuery(cqr);
        List<Request> requests = typedQueryR.getResultList();

        // ---------------------------------------------
        List<IAppEntity<UUID>> result = new ArrayList<>(tasks);
        result.addAll(requests);

        Supplier<List<IAppEntity<UUID>>> supplier = LinkedList::new;
        result = result.stream().sorted((m1, m2) -> m1.getRegDate().after(m2.getRegDate()) ? 1 : -1)
                .collect(Collectors.toCollection(supplier));

        if (tasks.size() > 0) {
            for (Task t : tasks) {
                List<IAppEntity<UUID>> responses = findTaskResponses(t, filter, expandedIds, em);
                if (responses != null && responses.size() > 0) {
                    t.setResponsesCount((long) responses.size());
                    t.setResponses(responses);
                }
            }
        }

        return result;
    }

    public ViewPage<TaskViewEntry> findCreatedByUser(IUser user, int pageNum, int pageSize) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<TaskViewEntry> cq = cb.createQuery(TaskViewEntry.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> root = cq.from(Task.class);

            Predicate condition = cb.equal(root.get("author"), user);
          /*  condition = cb.and(
                    cb.or(cb.equal(root.get("status"), TaskStatusType.PROCESSING), cb.equal(root.get("status"), TaskStatusType.OPEN)),
                    condition);*/

            cq.select(cb.construct(TaskViewEntry.class, root.get("id"), root.get("regNumber"), root.get("taskType"), root.get("status"),
                    root.get("priority"), root.get("startDate"), root.get("dueDate"), root.get("tags")))
                    .orderBy(cb.asc(root.get("priority")));

            countCq.select(cb.count(root));
            cq.where(condition);
            countCq.where(condition);

            TypedQuery<TaskViewEntry> typedQuery = em.createQuery(cq);
            Query query = em.createQuery(countCq);
            long count = (long) query.getSingleResult();
            int maxPage = 1;

            if (pageNum != 0 || pageSize != 0) {
                maxPage = RuntimeObjUtil.countMaxPage(count, pageSize);
                if (pageNum == 0) {
                    pageNum = maxPage;
                }
                int firstRec = RuntimeObjUtil.calcStartEntry(pageNum, pageSize);
                typedQuery.setFirstResult(firstRec);
                if (pageSize > 0) {
                    typedQuery.setMaxResults(pageSize);
                }
            }

            List<TaskViewEntry> result = typedQuery.getResultList();
            return new ViewPage<>(result, count, maxPage, pageNum);
        } finally {
            em.close();
        }
    }

    public ViewPage<Task> findAssignedToUser(IUser user, int pageNum, int pageSize) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<Task> cq = cb.createQuery(Task.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> taskRoot = cq.from(Task.class);

            Predicate condition = cb.equal(taskRoot.get("assignee"), user.getId());
            condition = cb.and(cb.or(cb.equal(taskRoot.get("status"), TaskStatusType.PROCESSING),
                    cb.equal(taskRoot.get("status"), TaskStatusType.OPEN)), condition);

            cq.select(taskRoot).orderBy(cb.asc(taskRoot.get("priority")));
            countCq.select(cb.count(taskRoot));

            cq.where(condition);
            countCq.where(condition);

            TypedQuery<Task> typedQuery = em.createQuery(cq);
            Query query = em.createQuery(countCq);
            long count = (long) query.getSingleResult();
            int maxPage = pageable(typedQuery, count, pageNum, pageSize);

            List<Task> result = typedQuery.getResultList();

            return new ViewPage<>(result, count, maxPage, pageNum);
        } finally {
            em.close();
        }
    }

    public long getColByAssignee(Date date, Long user, TaskStatusType status) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<Task> cq = cb.createQuery(Task.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> c = cq.from(Task.class);
            countCq.select(cb.count(c));
            ParameterExpression<Date> parameter = cb.parameter(Date.class);
            //Predicate condition = cb.equal(cb.function("date", Date.class, c.<Date>get("regDate")), parameter);
            Predicate condition = cb.and(cb.equal(c.get("assignee"), user));
            condition = cb.and(cb.equal(c.get("status"), status), condition);
            countCq.where(condition);
            Query query = em.createQuery(countCq);
            //query.setParameter(parameter, date, TemporalType.DATE);
            return (long) query.getSingleResult();
        } finally {
            em.close();
        }
    }

    public long getColByAuthor(Date date, User user, TaskStatusType status) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<Task> cq = cb.createQuery(Task.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> c = cq.from(Task.class);
            cq.select(c);
            countCq.select(cb.count(c));
            ParameterExpression<Date> parameter = cb.parameter(Date.class);
            //Predicate condition = cb.equal(cb.function("date", Date.class, c.<Date>get("regDate")), parameter);
            Predicate condition = cb.and(cb.equal(c.get("author"), user));
            condition = cb.and(cb.equal(c.get("status"), status), condition);
            countCq.where(condition);
            Query query = em.createQuery(countCq);
            //query.setParameter(parameter, date, TemporalType.DATE);
            return (long) query.getSingleResult();

        } finally {
            em.close();
        }
    }

    public long getColByAssignee(Long user, TaskStatusType status) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<Task> cq = cb.createQuery(Task.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> c = cq.from(Task.class);
            countCq.select(cb.count(c));
            Predicate condition = cb.and(cb.equal(c.get("assignee"), user));
            condition = cb.and(cb.equal(c.get("status"), status), condition);
            countCq.where(condition);
            Query query = em.createQuery(countCq);
            return (long) query.getSingleResult();
        } finally {
            em.close();
        }
    }

    public long getColByAuthor(User user, TaskStatusType status) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<Task> cq = cb.createQuery(Task.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> c = cq.from(Task.class);
            cq.select(c);
            countCq.select(cb.count(c));
            Predicate condition = cb.and(cb.equal(c.get("author"), user));
            condition = cb.and(cb.equal(c.get("status"), status), condition);
            countCq.where(condition);
            Query query = em.createQuery(countCq);
            return (long) query.getSingleResult();

        } finally {
            em.close();
        }
    }

    public ViewPage<TaskViewEntry> findAllTaskDueToday() {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<TaskViewEntry> cq = cb.createQuery(TaskViewEntry.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> root = cq.from(Task.class);

            Expression<java.sql.Date> currentDateExp = cb.currentDate();
            TaskStatusType[] inWorkSt = {TaskStatusType.OPEN, TaskStatusType.PROCESSING};

            Predicate condition = cb.and(root.get("status").in(Arrays.asList(inWorkSt)));
            condition = cb.and(cb.equal(root.get("dueDate"), currentDateExp), condition);

            if (!user.isSuperUser()) {
                condition = cb.and(root.get("readers").in(user.getId()), condition);
            }

            cq.select(cb.construct(
                    TaskViewEntry.class,
                    root.get("id"),
                    root.get("title"),
                    root.get("regNumber"),
                    root.get("status"),
                    root.get("priority"),
                    root.get("startDate"),
                    root.get("dueDate")))
                    .orderBy(cb.asc(root.get("priority")));

            countCq.select(cb.count(root));

            cq.where(condition);
            countCq.where(condition);

            TypedQuery<TaskViewEntry> typedQuery = em.createQuery(cq);
            TypedQuery<Long> countQuery = em.createQuery(countCq);
            List<TaskViewEntry> result = typedQuery.getResultList();

            return new ViewPage<>(result, countQuery.getSingleResult(), 0, 0);
        } finally {
            em.close();
        }
    }

    public ViewPage<TaskViewEntry> findAllTaskIn7Day() {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<TaskViewEntry> cq = cb.createQuery(TaskViewEntry.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> root = cq.from(Task.class);

            Calendar todayPlus7DayC = Calendar.getInstance();
            todayPlus7DayC.add(Calendar.DATE, 7);
            Date todayPlus7Day = todayPlus7DayC.getTime();

            Expression<java.sql.Date> currentDateExp = cb.currentDate();
            Expression<Date> dueDateExp = root.get("dueDate");
            TaskStatusType[] inWorkSt = {TaskStatusType.OPEN, TaskStatusType.PROCESSING};

            Predicate condition = cb.and(root.get("status").in(Arrays.asList(inWorkSt)));
            condition = cb.and(cb.lessThanOrEqualTo(dueDateExp, todayPlus7Day), condition);
            condition = cb.and(cb.greaterThan(root.get("dueDate"), currentDateExp), condition);

            if (!user.isSuperUser()) {
                condition = cb.and(root.get("readers").in(user.getId()), condition);
            }

            cq.select(cb.construct(
                    TaskViewEntry.class,
                    root.get("id"),
                    root.get("title"),
                    root.get("regNumber"),
                    root.get("status"),
                    root.get("priority"),
                    root.get("startDate"),
                    root.get("dueDate")))
                    .orderBy(cb.asc(root.get("priority")));

            countCq.select(cb.count(root));

            cq.where(condition);
            countCq.where(condition);

            TypedQuery<TaskViewEntry> typedQuery = em.createQuery(cq);
            TypedQuery<Long> countQuery = em.createQuery(countCq);
            List<TaskViewEntry> result = typedQuery.getResultList();

            return new ViewPage<>(result, countQuery.getSingleResult(), 0, 0);
        } finally {
            em.close();
        }
    }

    public ViewPage<TaskViewEntry> findAllTaskExpired() {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<TaskViewEntry> cq = cb.createQuery(TaskViewEntry.class);
            CriteriaQuery<Long> countCq = cb.createQuery(Long.class);
            Root<Task> root = cq.from(Task.class);

            Expression<java.sql.Date> currentDateExp = cb.currentDate();
            TaskStatusType[] inWorkSt = {TaskStatusType.OPEN, TaskStatusType.PROCESSING};

            Predicate condition = cb.and(root.get("status").in(Arrays.asList(inWorkSt)));
            condition = cb.and(cb.lessThan(root.get("dueDate"), currentDateExp), condition);

            if (!user.isSuperUser()) {
                condition = cb.and(root.get("readers").in(user.getId()), condition);
            }

            cq.select(cb.construct(
                    TaskViewEntry.class,
                    root.get("id"),
                    root.get("title"),
                    root.get("regNumber"),
                    root.get("status"),
                    root.get("priority"),
                    root.get("startDate"),
                    root.get("dueDate")))
                    .orderBy(cb.asc(root.get("priority")));

            countCq.select(cb.count(root));

            cq.where(condition);
            countCq.where(condition);

            TypedQuery<TaskViewEntry> typedQuery = em.createQuery(cq);
            TypedQuery<Long> countQuery = em.createQuery(countCq);
            List<TaskViewEntry> result = typedQuery.getResultList();

            return new ViewPage<>(result, countQuery.getSingleResult(), 0, 0);
        } finally {
            em.close();
        }
    }

    public List<CountStat> getStatTaskPriority() {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<CountStat> cq = cb.createQuery(CountStat.class);
            Root<Task> root = cq.from(Task.class);

            Predicate condition = null;

            if (!user.isSuperUser()) {
                condition = cb.and(root.get("readers").in(user.getId()));
            }

            cq.select(cb.construct(
                    CountStat.class,
                    root.get("priority"),
                    cb.count(root.get("priority")))
            )
                    .groupBy(root.get("priority"));

            if (condition != null) {
                cq.where(condition);
            }

            TypedQuery<CountStat> typedQuery = em.createQuery(cq);
            return typedQuery.getResultList();
        } finally {
            em.close();
        }
    }

    public List<CountStat> getStatTaskStatus() {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        CriteriaBuilder cb = em.getCriteriaBuilder();
        try {
            CriteriaQuery<CountStat> cq = cb.createQuery(CountStat.class);
            Root<Task> root = cq.from(Task.class);

            Predicate condition = null;

            if (!user.isSuperUser()) {
                condition = cb.and(root.get("readers").in(user.getId()));
            }

            cq.select(cb.construct(
                    CountStat.class,
                    root.get("status"),
                    cb.count(root.get("status")))
            )
                    .groupBy(root.get("status"));

            if (condition != null) {
                cq.where(condition);
            }

            TypedQuery<CountStat> typedQuery = em.createQuery(cq);
            return typedQuery.getResultList();
        } finally {
            em.close();
        }
    }
}
