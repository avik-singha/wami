/**
 * This package contains content accessible through a proxy servlet, if the web.xml contains 
 * 
 * 	<servlet>
 *		<servlet-name>content</servlet-name>
 *		<servlet-class>edu.mit.csail.sls.wami.jsapi.WamiContentProxy</servlet-class>
 *	</servlet>
 *
 *	<servlet-mapping>
 *		<servlet-name>content</servlet-name>
 *		<url-pattern>/content/*</url-pattern>
 *	</servlet-mapping>
 * 
 *  
 */
package edu.mit.csail.sls.wami.content;

