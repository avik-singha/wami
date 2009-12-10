Wami = function() {

}

Wami.utils = function() {
	
}

Wami.utils.debug = function(message) {
	if (typeof console == 'undefined') {
		// No Firebug console, so no logging.
	}
	else {
		console.log(message);
	}
}

Wami.utils.gup = function(name) {
	var regexS = "[\\?&]"+name+"=([^&#]*)";
	var regex = new RegExp(regexS);
	var tmpURL = window.location.href;
	var results = regex.exec(tmpURL);
	
	if(results == null) {
		return null;
	}
	else {
		return results[1];
	}
}

Wami.utils.setCookie = function(cookieName, cookieValue, nDays) {
	var today = new Date();
	var expire = new Date();
	if (nDays == null || nDays == 0)
		nDays = 1;
	expire.setTime(today.getTime() + 3600000 * 24 * nDays);
	document.cookie = cookieName + "=" + escape(cookieValue) + ";expires="
			+ expire.toGMTString();
}

Wami.utils.getCookie = function(name) {
	var start = document.cookie.indexOf(name + "=");
	var len = start + name.length + 1;
	if ((!start) && (name != document.cookie.substring(0, name.length))) {
		return null;
	}
	if (start == -1)
		return null;
	var end = document.cookie.indexOf(";", len);
	if (end == -1)
		end = document.cookie.length;
	return unescape(document.cookie.substring(len, end));
}

Wami.utils.xml = function() { }

Wami.utils.xml.fromString = function(xmlString) {
    if (typeof DOMParser != "undefined") { 
    	// Mozilla, Firefox, and related browsers
    	var doc = (new DOMParser()).parseFromString(xmlString, "application/xml");
    	return doc; 
	} 
	else if (typeof ActiveXObject != "undefined") { 
		// Internet Explorer.
		var doc = Wami.utils.xml.newDocument();  // Create an empty document
    	doc.loadXML(xmlString);            // Parse text into it
    	return doc;                   // Return it
	} 
	else { 
		// As a last resort, try loading the document from a data: URL
		var url = "data:text/xml;charset=utf-8," + encodeURIComponent(xmlString); 
		var request = new XMLHttpRequest(); 
		request.open("GET", url, false); 
		request.send(null); 
		return request.responseXML; 
	}
}

Wami.utils.xml.toString = function(xmlNode) {
	var serialized;

	try {
		// Mozilla...
		serializer = new XMLSerializer();
		serialized = serializer.serializeToString(xmlNode);
	} catch (e) {
		// IE
		serialized = xmlNode.xml;
	}
	return serialized;
}

Wami.utils.xml.newRequest = function() {
	var xmlhttp = false;

	if (!xmlhttp && typeof XMLHttpRequest != 'undefined') {
		try {
			xmlhttp = new XMLHttpRequest();
		} catch (e) {
			xmlhttp = false;
		}
	}
	if (!xmlhttp && window.createRequest) {
		try {
			xmlhttp = window.createRequest();
		} catch (e) {
			xmlhttp = false;
		}
	}

	return xmlhttp;
}

Wami.utils.xml.newDocument = function(rootTagName, namespaceURL) {
	if (!rootTagName)
		rootTagName = "";
	if (!namespaceURL)
		namespaceURL = "";

	if (document.implementation && document.implementation.createDocument) {
		// This is the W3C standard way to do it
		return document.implementation.createDocument(namespaceURL,
				rootTagName, null);
	} else { // This is the IE way to do it
		// Create an empty document as an ActiveX object
		// If there is no root element, this is all we have to do
		var doc = new ActiveXObject("MSXML2.DOMDocument");

		// If there is a root tag, initialize the document
		if (rootTagName) {
			// Look for a namespace prefix
			var prefix = "";
			var tagname = rootTagName;
			var p = rootTagName.indexOf(':');
			if (p != -1) {
				prefix = rootTagName.substring(0, p);
				tagname = rootTagName.substring(p + 1);
			}

			// If we have a namespace, we must have a namespace prefix
			// If we don't have a namespace, we discard any prefix
			if (namespaceURL) {
				if (!prefix)
					prefix = "a0"; // What Firefox uses
			} else
				prefix = "";

			// Create the root element (with optional namespace) as a
			// string of text
			var text = "<"
					+ (prefix ? (prefix + ":") : "")
					+ tagname
					+ (namespaceURL ? (" xmlns:" + prefix + '="' + namespaceURL + '"')
							: "") + "/>";
			// And parse that text into the empty document
			doc.loadXML(text);
		}
		return doc;
	}
};
