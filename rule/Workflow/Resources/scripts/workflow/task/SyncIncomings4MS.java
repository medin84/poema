package workflow.task;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;

import com.exponentus.appenv.AppEnv;
import com.exponentus.common.model.Attachment;
import com.exponentus.dataengine.DatabaseUtil;
import com.exponentus.dataengine.IDBConnectionPool;
import com.exponentus.dataengine.exception.DAOException;
import com.exponentus.dataengine.jpa.TempFile;
import com.exponentus.legacy.ConvertorEnvConst;
import com.exponentus.legacy.forms.Import4MS;
import com.exponentus.localization.LanguageCode;
import com.exponentus.scheduler.tasks.TempFileCleaner;
import com.exponentus.scripting._FormAttachments;
import com.exponentus.scripting._Session;
import com.exponentus.scriptprocessor.tasks.Command;
import com.exponentus.user.IUser;

import administrator.dao.UserDAO;
import administrator.model.User;
import reference.dao.DocumentLanguageDAO;
import reference.dao.DocumentSubjectDAO;
import reference.dao.DocumentTypeDAO;
import reference.model.DocumentLanguage;
import reference.model.DocumentSubject;
import reference.model.DocumentType;
import staff.dao.OrganizationDAO;
import staff.model.Organization;
import workflow.dao.IncomingDAO;
import workflow.model.Incoming;

@Command(name = "import_in_4ms")
public class SyncIncomings4MS extends Import4MS {
	private static final String VID_CATEGORY = "Интеграция";
	private static final String TMP_FIELD_NAME = "incoming_tmp_file";
	
	@Override
	public void doTask(AppEnv appEnv, _Session ses) {
		Map<String, Incoming> entities = new HashMap<>();
		IDBConnectionPool dbPool = getConnectionPool();
		Connection conn = dbPool.getConnection();
		try {
			OrganizationDAO oDao = new OrganizationDAO(ses);
			IncomingDAO iDao = new IncomingDAO(ses);
			DocumentTypeDAO dtDao = new DocumentTypeDAO(ses);
			DocumentLanguageDAO dlDao = new DocumentLanguageDAO(ses);
			DocumentSubjectDAO dsDao = new DocumentSubjectDAO(ses);
			UserDAO uDao = new UserDAO(ses);
			Map<Integer, String> vidCollation = vidCollationMapInit();
			Map<String, LanguageCode> docLangCollation = langCollationMapInit();
			User dummyUser = (User) uDao.findByLogin(ConvertorEnvConst.DUMMY_USER);
			
			conn.setAutoCommit(false);
			Statement s = conn.createStatement();
			String sql = "SELECT * FROM maindocs as m WHERE form='IN';";
			ResultSet rs = s.executeQuery(sql);
			while (rs.next()) {
				int docId = rs.getInt("docid");
				String extKey = docId + "maindocs";
				Incoming inc = iDao.findByExtKey(extKey);
				if (inc == null) {
					inc = new Incoming();
				}
				inc.setRegNumber(getStringValue(conn, docId, "vn"));
				inc.setAppliedRegDate(getDateValue(conn, docId, "dvn"));
				IUser<Long> author = uDao.findByLogin(getStringValue(conn, docId, "author"));
				if (author != null) {
					inc.setAuthor(author);
				} else {
					inc.setAuthor(dummyUser);
				}
				int code = getGloassaryValue(conn, docId, "vid");
				String vidName = vidCollation.get(code);
				DocumentType docType = dtDao.findByNameAndCategory(VID_CATEGORY, vidName);
				if (docType != null) {
					inc.setDocType(docType);
				} else {
					logger.errorLogEntry("reference ext value has not been found \"" + vidName + "\"");
				}
				
				String har = ConvertorEnvConst.GAG_KEY;
				DocumentSubject docSubj = dsDao.findByName(har);
				if (docSubj != null) {
					inc.setDocSubject(docSubj);
				} else {
					logger.errorLogEntry("reference ext value has not been found \"" + har + "\"");
				}
				
				int docLangVal = getIntValue(conn, docId, "lang");
				LanguageCode intRefKey = docLangCollation.get(docLangVal);
				if (intRefKey == null) {
					logger.errorLogEntry("wrong reference ext value \"" + docLangVal + "\"");
					intRefKey = LanguageCode.UNKNOWN;
				}
				DocumentLanguage docLang = dlDao.findByCode(intRefKey);
				if (docLang != null) {
					inc.setDocLanguage(docLang);
				}
				inc.setSenderAppliedRegDate(getDateValue(conn, docId, "din"));
				inc.setSenderRegNumber(getStringValue(conn, docId, "in"));
				int corrId = getGloassaryValue(conn, docId, "corr");
				if (corrId != 0) {
					Organization org = oDao.findByExtKey(Integer.toString(corrId));
					if (org != null) {
						inc.setSender(org);
					}
				}
				inc.setTitle(StringUtils.abbreviate(getStringValue(conn, docId, "briefcontent"), 140));
				inc.setBody(getStringValue(conn, docId, "briefcontent"));
				
				_FormAttachments files = new _FormAttachments(ses);
				Map<String, String> blobs = getBlobValue(ses, conn, docId);
				for (Entry<String, String> entry : blobs.entrySet()) {
					String filePath = entry.getValue();
					files.addFile(new File(entry.getValue()), filePath, TMP_FIELD_NAME);
					TempFileCleaner.addFileToDelete(filePath);
				}
				
				List<Attachment> attachments = new ArrayList<>();
				for (TempFile tmpFile : files.getFiles(TMP_FIELD_NAME)) {
					Attachment a = (Attachment) tmpFile.convertTo(new Attachment());
					attachments.add(a);
				}
				inc.setAttachments(attachments);
				
				normalizeACL(uDao, docId, inc, conn);
				entities.put(extKey, inc);
			}
			s.close();
			conn.commit();
			logger.infoLogEntry("has been found " + entities.size() + " records");
			for (Entry<String, Incoming> ee : entities.entrySet()) {
				save(iDao, ee.getValue(), ee.getKey());
			}
		} catch (SQLException e) {
			DatabaseUtil.errorPrint(e);
		} catch (DAOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			dbPool.returnConnection(conn);
		}
		logger.infoLogEntry("done...");
	}

	private String getStringValue(Connection conn, int docId, String fieldName) {
		try {
			Statement s = conn.createStatement();
			String sql = "SELECT value FROM custom_fields as cf WHERE cf.docid = " + docId + " AND cf.name = '"
					+ fieldName + "';";
			ResultSet rs = s.executeQuery(sql);
			if (rs.next()) {
				return rs.getString(1);
			} else {
				return "";
			}
		} catch (SQLException e) {
			DatabaseUtil.errorPrint(e);
			return "";
		}
	}

	private int getIntValue(Connection conn, int docId, String fieldName) throws SQLException {
		Statement s = conn.createStatement();
		String sql = "SELECT valueasnumber FROM custom_fields as cf WHERE cf.docid = " + docId + " AND cf.name = '"
				+ fieldName + "';";
		ResultSet rs = s.executeQuery(sql);
		if (rs.next()) {
			return rs.getInt(1);
		} else {
			return 0;
		}
	}

	private Date getDateValue(Connection conn, int docId, String fieldName) throws SQLException {
		Statement s = conn.createStatement();
		try {
			String sql = "SELECT valueasdate FROM custom_fields as cf WHERE cf.docid = " + docId + " AND cf.name = '"
					+ fieldName + "';";
			ResultSet rs = s.executeQuery(sql);
			if (rs.next()) {
				return rs.getDate(1);
			} else {
				return null;
			}
		} catch (SQLException e) {
			DatabaseUtil.errorPrint(e);
			return null;
		}
	}
	
	private Map<String, String> getBlobValue(_Session ses, Connection conn, int docId) throws SQLException {
		Map<String, String> paths = new HashMap<String, String>();
		Statement s = conn.createStatement();
		String sql = "SELECT * FROM custom_blobs_maindocs as cf WHERE cf.docid = " + docId + ";";
		LargeObjectManager lobj = ((org.postgresql.PGConnection) conn).getLargeObjectAPI();
		
		ResultSet rs = s.executeQuery(sql);
		while (rs.next()) {
			int oid = rs.getInt("oid");
			String originalName = rs.getString("originalname");
			LargeObject obj = lobj.open(oid, LargeObjectManager.READ);
			byte buf[] = new byte[obj.size()];
			obj.read(buf, 0, obj.size());
			String path = ses.getTmpDir().getAbsolutePath() + File.separator + originalName;
			File file = new File(path);
			try {
				FileInputStream fileInputStream = new FileInputStream(file);
				fileInputStream.read(buf);
				fileInputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			paths.put(originalName, path);
			obj.close();

		}
		return paths;
		
	}
	
	private int getGloassaryValue(Connection conn, int docId, String fieldName) throws SQLException {
		Statement s = conn.createStatement();
		String sql = "SELECT valueasglossary FROM custom_fields as cf WHERE cf.docid = " + docId + " AND cf.name = '"
				+ fieldName + "';";
		ResultSet rs = s.executeQuery(sql);
		if (rs.next()) {
			return rs.getInt(1);
		} else {
			return 0;
		}
	}
	
	private Map<Integer, String> vidCollationMapInit() {
		Map<Integer, String> collation = new HashMap<>();
		collation.put(110, "Письмо");
		collation.put(111, "Поручение");
		return collation;

	}

	private Map<String, LanguageCode> langCollationMapInit() {
		Map<String, LanguageCode> depTypeCollation = new HashMap<>();
		depTypeCollation.put("Русский", LanguageCode.RUS);
		depTypeCollation.put("Английский", LanguageCode.ENG);
		depTypeCollation.put("Казахский", LanguageCode.KAZ);
		depTypeCollation.put("Французский", LanguageCode.FRA);
		depTypeCollation.put("Китайский", LanguageCode.CHI);
		depTypeCollation.put("Немецкий", LanguageCode.DEU);
		depTypeCollation.put("Польский", LanguageCode.POL);
		depTypeCollation.put("Белорусский", LanguageCode.BEL);
		depTypeCollation.put("Чешский", LanguageCode.CES);
		depTypeCollation.put("Греческий", LanguageCode.GRE);
		depTypeCollation.put("Украинский", LanguageCode.UKR);
		depTypeCollation.put("Турецкий", LanguageCode.TUR);
		depTypeCollation.put("Итальянский", LanguageCode.ITA);
		depTypeCollation.put("Корейский", LanguageCode.KOR);
		depTypeCollation.put("Японский", LanguageCode.JPN);
		depTypeCollation.put("Испанский", LanguageCode.SPA);
		depTypeCollation.put("Хинди", LanguageCode.HIN);
		depTypeCollation.put("Арабский", LanguageCode.ARA);
		depTypeCollation.put("", LanguageCode.UNKNOWN);
		depTypeCollation.put("null", LanguageCode.UNKNOWN);
		return depTypeCollation;
		
	}
}
