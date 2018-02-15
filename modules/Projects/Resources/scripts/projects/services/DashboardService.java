package projects.services;

import administrator.dao.UserDAO;
import com.exponentus.common.model.constants.StatusType;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.env.Voc;
import com.exponentus.localization.constants.LanguageCode;
import com.exponentus.rest.RestProvider;
import com.exponentus.rest.outgoingdto.Outcome;
import com.exponentus.scripting._Session;
import com.exponentus.user.IUser;
import com.exponentus.util.TimeUtil;
import dataexport.model.constants.ExportFormatType;
import monitoring.dto.TimeChart;
import projects.dao.TaskDAO;
import projects.dto.stat.CountStat;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Path("dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardService extends RestProvider {

    @GET
    public Response get() {
        try {
            _Session session = getSession();
            LanguageCode lang = session.getLang();
            Outcome outcome = new Outcome();
            outcome.setId("dashboard");
            outcome.setTitle("dashboard");

            Date tillDate = new Date();
            List<IUser> allUsers = new ArrayList<>();

            UserDAO userDAO = new UserDAO();
            //allUsers.addAll(userDAO.findAll());
            allUsers.add(session.getUser());
            Date fromDate = TimeUtil.convertTextToDate("01.01.2017");
            StatusType[] stats = {StatusType.PROCESSING, StatusType.OPEN};
            String periodType = "week"; //could be "day","week", "year" as well
            List<CountStat<Timestamp>> result = new TaskDAO(session).getCountByStatus(fromDate, tillDate, periodType, "assignee", allUsers, stats);
            long total = 0;
            Map vals = new LinkedHashMap();
            for (CountStat r : result) {
                total += r.count; // (long) r[1];
                vals.put(new SimpleDateFormat("dd.MM.yyyy").format(new Date(((Timestamp) r.title).getTime())), r.count);
            }

            String statusesAsText = Arrays.stream(stats).map(Enum::name).collect(Collectors.joining(","));

            TimeChart chart = new TimeChart();
            chart.setValues(vals);
            if (total > 0) {
                long average = total / vals.size();
                chart.setTitle(average + " " + Voc.get("task", lang) + "/" + Voc.get(periodType, lang) + " " + statusesAsText);
            }
            chart.setStart(TimeUtil.dateToStringSilently(fromDate));
            chart.setEnd(TimeUtil.dateTimeToStringSilently(tillDate));
            chart.setStatus(statusesAsText);
            outcome.addPayload("statAssigneeStateProcessing", chart);

            TimeChart chart1 = new TimeChart();
            StatusType[] stats1 = {StatusType.PENDING, StatusType.COMPLETED};
            List<CountStat<Timestamp>> result1 = new TaskDAO(session).getCountByStatus(fromDate, tillDate, periodType, "assignee", allUsers, stats1);
            long total1 = 0;
            Map vals1 = new LinkedHashMap();
            for (CountStat r : result1) {
                total1 += r.count;
                vals1.put(new SimpleDateFormat("dd.MM.yyyy").format(new Date(((Timestamp) r.title).getTime())), r.count);
            }

            chart1.setStatus(Arrays.stream(stats1).map(Enum::name).collect(Collectors.joining(",")));
            chart1.setValues(vals1);
            outcome.addPayload("statAssigneeStateCompleted", chart1);

            TaskDAO taskDAO = new TaskDAO(session);

            List<CountStat> taskStatusStatList = taskDAO.getStatTaskStatus();
            outcome.addPayload("taskStatusStat", taskStatusStatList);
            outcome.addPayload("exportFormatType", ExportFormatType.values());

            return Response.ok(outcome).build();
        } catch (DAOException e) {
            return responseException(e);
        }
    }
}