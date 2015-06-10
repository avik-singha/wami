For this project you will need: Mercurial, Eclipse, and Tomcat.

# Eclipse and Tomcat #

Download and install the latest [Eclipse IDE for Java EE developers](http://www.eclipse.org/downloads/).  Once you have it running, create a workspace.  You will also need [Apache Tomcat](http://tomcat.apache.org/download-60.cgi).  Operation under Tomcat 5.5 is tested extensively, but Tomcat 6.0 should also work with a little extra configuration.

If you have not developed a Dynamic Web Application in Eclipse before, you might wish to walk through a [tutorial](http://www.ibm.com/developerworks/opensource/library/os-eclipse-tomcat/index.html) before diving into Wami.  Note that you will already have the proper Web Tools installed, so you can skip that step in the tutorial.

# Checking out the Code #

Presumably, you now have some familiarity with Eclipse and developing J2EE applications.  Now forget everything you learned about JSPs, because they just complicate things.  Wami does all the client-server communication for you anyway, using AJAX.

Now you can check out [wami](http://code.google.com/p/wami/source/checkout) and [wami-example](http://code.google.com/p/wami/source/checkout?repo=example) using [Mercurial](http://mercurial.selenic.com/).  Place them into your workspace.  Then within eclipse, import these projects into your workspace by going to File -> Import... -> Existing Project into Workspace.

If there are build errors, it's likely the case that some libraries are not linked correctly.  Resolve build path problems by right-clicking on the newly imported project directory, and selecting Build Path -> Configure Build Path.  When your wami-example project builds without errors, there is just one more step before you're up and running:  Read the README.  Seriously.

With that done, run it on the tomcat server, and browse to `http://localhost:8080/wami-example` to give it a shot!