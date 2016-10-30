package workflow.tasks;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.exponentus.legacy.ConvertorEnvConst;
import com.exponentus.legacy.smartdoc.ImportNSF;
import com.exponentus.localization.LanguageCode;
import com.exponentus.scripting._Session;
import com.exponentus.scriptprocessor.tasks.Command;
import com.exponentus.user.IUser;

import administrator.dao.UserDAO;
import administrator.model.User;
import lotus.domino.Document;
import lotus.domino.NotesException;
import lotus.domino.ViewEntry;
import lotus.domino.ViewEntryCollection;
import reference.dao.DocumentLanguageDAO;
import reference.dao.DocumentTypeDAO;
import reference.model.DocumentLanguage;
import reference.model.DocumentType;
import staff.dao.OrganizationDAO;
import staff.model.Organization;
import workflow.dao.IncomingDAO;
import workflow.model.Incoming;

@Command(name = "import_in_nsf")
public class ImportIncomingsFromNSF extends ImportNSF {
	private static final String VID_CATEGORY = "01. Входящие";

	@Override
	public void doTask(_Session ses) {
		Map<String, Incoming> entities = new HashMap<>();
		OrganizationDAO oDao = new OrganizationDAO(ses);
		IncomingDAO iDao = new IncomingDAO(ses);
		DocumentTypeDAO dtDao = new DocumentTypeDAO(ses);
		DocumentLanguageDAO dlDao = new DocumentLanguageDAO(ses);
		UserDAO uDao = new UserDAO(ses);
		Map<String, LanguageCode> docLangCollation = docLangCollationMapInit();
		User dummyUser = (User) uDao.findByLogin(ConvertorEnvConst.DUMMY_USER);
		try {
			ViewEntryCollection vec = getAllEntries("incoming.nsf");
			ViewEntry entry = vec.getFirstEntry();
			ViewEntry tmpEntry = null;
			while (entry != null) {
				Document doc = entry.getDocument();
				String form = doc.getItemValueString("Form");
				if (form != null && form.equals("IN")) {
					String unId = doc.getUniversalID();
					Incoming inc = iDao.findByExtKey(unId);
					if (inc == null) {
						inc = new Incoming();
					}
					String vn = doc.getItemValueString("Vn");
					if (vn != null) {
						inc.setRegNumber(vn);
						try {
							inc.setAppliedRegDate(doc.getFirstItem("Dvn").getDateTimeValue().toJavaDate());
						} catch (NotesException ne) {
							logger.errorLogEntry(ne.text);
						}
						IUser<Long> author = uDao.findByExtKey(doc.getItemValueString("AuthorNA"));
						if (author != null) {
							inc.setAuthor(author);
						} else {
							inc.setAuthor(dummyUser);
						}

						String vid = doc.getItemValueString("Vid");
						DocumentType docType = dtDao.findByNameAndCategory(VID_CATEGORY, vid);
						if (docType != null) {
							inc.setDocType(docType);
						} else {
							logger.errorLogEntry("reference ext value has not been find \"" + vid + "\"");
						}

						String docLangVal = doc.getItemValueString("langName");
						LanguageCode intRefKey = docLangCollation.get(docLangVal);
						if (intRefKey == null) {
							logger.errorLogEntry("wrong reference ext value \"" + docLangVal + "\"");
							intRefKey = LanguageCode.UNKNOWN;
						}
						DocumentLanguage docLang = dlDao.findByCode(intRefKey);
						if (docLang != null) {
							inc.setDocLanguage(docLang);
						}
						inc.setSenderAppliedRegDate(doc.getFirstItem("Din").getDateTimeValue().toJavaDate());
						inc.setSenderRegNumber(doc.getItemValueString("In"));
						String corrId = doc.getItemValueString("CorrID");
						if (!corrId.isEmpty()) {
							Organization org = oDao.findByExtKey(corrId);
							if (org != null) {
								inc.setSender(org);
							}
						}

						inc.setBody(doc.getItemValueString("Vn"));
						// inc.setControl(doc.getItemValueString("Vn"));
						// inc.setDocLanguage(doc.getItemValueString("Vn"));
						// inc.setDocType(doc.getItemValueString("Vn"));
						inc.setTitle(doc.getItemValueString("BriefContent"));
						entities.put(unId, inc);
					}
				}
				tmpEntry = vec.getNextEntry();
				entry.recycle();
				entry = tmpEntry;
			}

		} catch (NotesException e) {
			logger.errorLogEntry(e);
		} catch (Exception e) {
			logger.errorLogEntry(e);
		}
		logger.infoLogEntry("has been found " + entities.size() + " records");
		for (Entry<String, Incoming> entry : entities.entrySet()) {
			save(iDao, entry.getValue(), entry.getKey());
		}
		logger.infoLogEntry("done...");
	}

	private Map<String, LanguageCode> docLangCollationMapInit() {
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
