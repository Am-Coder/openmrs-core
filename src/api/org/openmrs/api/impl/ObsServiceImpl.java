package org.openmrs.api.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.MimeType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.APIAuthenticationException;
import org.openmrs.api.APIException;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ObsDAO;
import org.openmrs.logic.Aggregation;
import org.openmrs.logic.Constraint;
import org.openmrs.util.OpenmrsConstants;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observation-related services
 * 
 * @author Ben Wolfe
 * @author Burke Mamlin
 * @version 1.0
 */
public class ObsServiceImpl implements ObsService {

	private Log log = LogFactory.getLog(this.getClass());

	private ObsDAO dao;

	public ObsServiceImpl() {
	}

	private ObsDAO getObsDAO() {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_VIEW_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_VIEW_OBS);

		return dao;
	}

	public void setObsDAO(ObsDAO dao) {
		this.dao = dao;
	}

	/**
	 * Create an observation
	 * 
	 * @param Obs
	 * @throws APIException
	 */
	public void createObs(Obs obs) throws APIException {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_ADD_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_ADD_OBS);
		getObsDAO().createObs(obs);
	}

	/**
	 * Create a grouping of observations (observations linked by
	 * obs.obs_group_id)
	 * 
	 * @param obs -
	 *            array of observations to be grouped
	 * @throws APIException
	 */
	public void createObsGroup(Obs[] obs) throws APIException {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_ADD_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_ADD_OBS);
		if (obs == null || obs.length < 1)
			return; // silently tolerate calls with missing/empty parameter

		// TODO - consider creating a DAO-level method for creating obs groups
		// more efficiently
		getObsDAO().createObs(obs[0]);
		Integer obsGroupId = obs[0].getObsId();
		obs[0].setObsGroupId(obsGroupId);
		getObsDAO().updateObs(obs[0]);
		for (int i = 1; i < obs.length; i++) {
			obs[i].setObsGroupId(obsGroupId);
			getObsDAO().createObs(obs[i]);
		}
	}

	/**
	 * Get an observation
	 * 
	 * @param integer
	 *            obsId of observation desired
	 * @return matching Obs
	 * @throws APIException
	 */
	public Obs getObs(Integer obsId) throws APIException {
		return getObsDAO().getObs(obsId);
	}

	/**
	 * Save changes to observation
	 * 
	 * @param Obs
	 * @throws APIException
	 */
	public void updateObs(Obs obs) throws APIException {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_EDIT_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_EDIT_OBS);

		if (obs.isVoided() && obs.getVoidedBy() == null)
			voidObs(obs, obs.getVoidReason());
		else if (obs.isVoided() == false && obs.getVoidedBy() != null)
			unvoidObs(obs);
		else {
			log.debug("Date voided: " + obs.getDateVoided());
			getObsDAO().updateObs(obs);
		}
	}

	/**
	 * Equivalent to deleting an observation
	 * 
	 * @param Obs
	 *            obs to void
	 * @param String
	 *            reason
	 * @throws APIException
	 */
	public void voidObs(Obs obs, String reason) throws APIException {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_EDIT_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_EDIT_OBS);
		obs.setVoided(true);
		obs.setVoidReason(reason);
		obs.setVoidedBy(Context.getAuthenticatedUser());
		obs.setDateVoided(new Date());
		getObsDAO().updateObs(obs);
	}

	/**
	 * Revive an observation (pull a Lazarus)
	 * 
	 * @param Obs
	 * @throws APIException
	 */
	public void unvoidObs(Obs obs) throws APIException {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_EDIT_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_EDIT_OBS);
		obs.setVoided(false);
		obs.setVoidReason(null);
		obs.setVoidedBy(null);
		obs.setDateVoided(null);
		getObsDAO().updateObs(obs);
	}

	/**
	 * Delete an observation. SHOULD NOT BE CALLED unless caller is lower-level.
	 * 
	 * @param Obs
	 * @throws APIException
	 * @see voidObs(Obs)
	 */
	public void deleteObs(Obs obs) throws APIException {
		if (!Context.hasPrivilege(OpenmrsConstants.PRIV_DELETE_OBS))
			throw new APIAuthenticationException("Privilege required: "
					+ OpenmrsConstants.PRIV_DELETE_OBS);
		getObsDAO().deleteObs(obs);
	}

	/**
	 * Get all mime types
	 * 
	 * @return mime types list
	 * @throws APIException
	 */
	public List<MimeType> getMimeTypes() throws APIException {
		return getObsDAO().getMimeTypes();
	}

	/**
	 * Get mimeType by internal identifier
	 * 
	 * @param mimeType
	 *            id
	 * @return mimeType with given internal identifier
	 * @throws APIException
	 */
	public MimeType getMimeType(Integer mimeTypeId) throws APIException {
		return getObsDAO().getMimeType(mimeTypeId);
	}

	/**
	 * Get all Observations for a patient
	 * 
	 * @param who
	 * @return
	 */
	public Set<Obs> getObservations(Patient who) {
		return getObsDAO().getObservations(who);
	}

	/**
	 * Get all Observations for this concept/location Sort is optional
	 * 
	 * @param concept
	 * @param location
	 * @param sort
	 * @return list of obs for a location
	 */
	public List<Obs> getObservations(Concept c, Location loc, String sort) {
		return getObsDAO().getObservations(c, loc, sort);
	}

	/**
	 * e.g. get all CD4 counts for a patient
	 * 
	 * @param who
	 * @param question
	 * @return
	 */
	public Set<Obs> getObservations(Patient who, Concept question) {
		return getObsDAO().getObservations(who, question);
	}

	/**
	 * e.g. get last 'n' number of observations for a patient for given concept
	 * 
	 * @param n
	 *            number of concepts to retrieve
	 * @param who
	 * @param question
	 * @return
	 */
	public List<Obs> getLastNObservations(Integer n, Patient who,
			Concept question) {
		return getObsDAO().getLastNObservations(n, who, question);
	}

	/**
	 * e.g. get all observations referring to RETURN VISIT DATE
	 * 
	 * @param question
	 *            (Concept: RETURN VISIT DATE)
	 * @param sort
	 * @return
	 */
	public List<Obs> getObservations(Concept question, String sort) {
		return getObsDAO().getObservations(question, sort);
	}
	
	/**
	 *  @see org.openmrs.api.ObsService#getObservationsAnsweredByConcept(org.openmrs.Concept)
	 */
	@Transactional(readOnly = true)
	public List<Obs> getObservationsAnsweredByConcept(Concept answer) {
		return getObsDAO().getObservationsAnsweredByConcept(answer);
	}
	
	/**
	 *  @see org.openmrs.api.ObsService#getNumericAnswersForConcept(org.openmrs.Concept,java.lang.Boolean)
	 */
	@Transactional(readOnly = true)
	public List<Object[]> getNumericAnswersForConcept(Concept answer, Boolean sortByValue) {
		return getObsDAO().getNumericAnswersForConcept(answer, sortByValue);
	}
	

	/**
	 * Get all observations from a specific encounter
	 * 
	 * @param whichEncounter
	 * @return Set of Obs
	 */
	public Set<Obs> getObservations(Encounter whichEncounter) {
		return getObsDAO().getObservations(whichEncounter);
	}

	/**
	 * Get all observations that have been voided Observations are ordered by
	 * descending voidedDate
	 * 
	 * @return List of Obs
	 */
	public List<Obs> getVoidedObservations() {
		return getObsDAO().getVoidedObservations();
	}

	/**
	 * Find observations matching the search string "matching" is defined as
	 * either the obsId or the patient identifier
	 * 
	 * @param search
	 * @param includeVoided
	 * @return list of matched observations
	 */
	public List<Obs> findObservations(String search, boolean includeVoided) {
		List<Obs> obs = new Vector<Obs>();
		for (Patient p : Context.getPatientService().getPatientsByIdentifier(
				search, includeVoided)) {
			obs.addAll(getObsDAO().findObservations(p.getPatientId(),
					includeVoided));
		}
		try {
			Integer i = Integer.valueOf(search);
			if (i != null)
				obs.addAll(getObsDAO().findObservations(i, includeVoided));
		} catch (Exception e) {
		}

		return obs;
	}

	public List<String> getDistinctObservationValues(Concept question) {
		// todo: make this efficient, and add a sort option

		Locale l = Context.getLocale();
		List<Obs> obs = getObservations(question, null);
		SortedSet<String> set = new TreeSet<String>();
		for (Obs o : obs) {
			set.add(o.getValueAsString(l));
		}
		return new ArrayList<String>(set);
	}

	/**
	 * @param obsGroupId
	 * @return All obs that share obsGroupId
	 */
	public List<Obs> findObsByGroupId(Integer obsGroupId) {
		return getObsDAO().findObsByGroupId(obsGroupId);
	}

	public List<Obs> getObservations(Patient who, Aggregation aggregation,
			Concept question, Constraint constraint) {
		return getObsDAO().getObservations(who, aggregation, question, constraint);
	}

}
