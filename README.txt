This is the core Wami library.  It provides the base classes for 
a web application that can be hosted Tomcat 5.5 to
serve a Javascript API for speech web-interfaces.  The
"wami-example" source code, available online, provides a sample
Dynamic Web Project for Eclipse, which makes use of this library.

Once hosted on Tomcat, wami-example can then be utilized by 3rd party
developers who wish to write client-side code against it.
Alternatively, you can override methods in the base classes to customize
a domain-specific speech application with arbitrary server-side functionality.

To find out more about the Javascript API exposed via a Wami servlet,
visit http://wami.csail.mit.edu/docs.php, and try out the one we host
at MIT.
