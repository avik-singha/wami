package edu.mit.csail.sls.wami.validation;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import edu.mit.csail.sls.wami.util.Instantiable;

public interface IValidator extends Instantiable {
	/**
	 * Validates the state of the entire servlet. Returns an empty list if
	 * everything's fine. For most people, this will suffice. For those who want
	 * to make sure the servlet always up and running properly, this can be used
	 * in conjunction with a cron job to validate the servlet every so often.
	 * 
	 * @param params
	 *            The default options.
	 * @return
	 */
	public List<String> validate(HttpServletRequest request);

}
