# WAMI : Web Accessible Multimodal Interfaces #

Wami is an open source Javascript API for speech recognition.  This project contains both the server and client side code necessary to host a web-based API for speech recognition.  If you're just interested in recording, the newest client-side code, which uses Flash to transport audio from browser to server via an HTTP post, can be found in the [WAMI recorder](https://code.google.com/p/wami-recorder/) project.

This project does not contain a speech recognizer.  If you're looking for an open-source speech recognizer, check out [Sphinx](http://cmusphinx.sourceforge.net/wordpress/).  Instead, this project provides all the plumbing you'll need to give a speech recognizer a web-interface.  We host an example speech recognizer for your prototyping needs here at MIT.  There's even an iPhone app associated with it (search "wami" in the app store).

If you would like to use MIT's existing speech recognition web-services, you do NOT need this project.  Simply browse to http://wami.csail.mit.edu to get started with our Javascript API.  If you would like to host your own speech recognition API, this project is for you!  You can start by hooking it up with MIT's speech recognition web-service, and then integrate your own speech recognizer once you have the basics down.

# Getting started #

The core WAMI code is a library for a J2EE servlet, which you can check out [here](http://code.google.com/p/wami/source/checkout).  In addition to this project, you will want to check out the example wami server [here](http://code.google.com/p/wami/source/checkout?repo=example).  This project is hosted using [Mercurial](http://mercurial.selenic.com/) not SVN, so check out the great [documentation](http://hgbook.red-bean.com/read/) of this distributed version control system if you haven't used it before.