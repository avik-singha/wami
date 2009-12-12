package edu.mit.csail.sls.wami.audio;

import java.io.InputStream;

import edu.mit.csail.sls.wami.util.Instantiable;

public interface IAudioRetriever extends Instantiable {

	public InputStream retrieveAudio(String fileName);

}
