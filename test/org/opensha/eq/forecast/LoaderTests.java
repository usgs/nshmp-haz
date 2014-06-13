package org.opensha.eq.forecast;

import java.net.URL;
import java.net.URLDecoder;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.io.Resources;

@SuppressWarnings("javadoc")
public class LoaderTests {

	private static final String FCAST_NAME = "Forecast Tests";
	
	private static final String BAD_PATH = "badPath";
	private static final String BAD_FOLDER = "data";
	private static final String EMPTY_ZIP = "data/empty.zip";
	private static final String BAD_URI = "data/bad[name].zip";
	
	@Rule
	public ExpectedException exception = ExpectedException.none();

	// These tests attempt to drill down through the Loader.load() heirarchy.
	// It is difficult to force IO and other more deeply nested checked
	// exceptions to be thrown as eariler checks have usually validated
	// any supplied String/Path
	
	// Problems with supplied forecast....
	
	@Test
	public void testNullPath() throws Exception {
		exception.expect(NullPointerException.class);
		Loader.load(null, FCAST_NAME);
	}
	
	@Test
	public void testBadPath() throws Exception {
		exception.expect(IllegalArgumentException.class);
		Loader.load(BAD_PATH, FCAST_NAME);
	}
	
	@Test
	public void testBadURI() throws Exception {
		exception.expect(IllegalArgumentException.class);
		URL badURL = Resources.getResource(LoaderTests.class, BAD_URI);
		String badURI = URLDecoder.decode(badURL.getPath(), "UTF-8");
		Loader.load(badURI, FCAST_NAME);
	}
	
	@Test
	public void testEmptyZip() throws Exception {
		exception.expect(IllegalArgumentException.class);
		URL badURL = Resources.getResource(LoaderTests.class, EMPTY_ZIP);
		String badURI = URLDecoder.decode(badURL.getPath(), "UTF-8");
		Loader.load(badURI, FCAST_NAME);
	}
	
	@Test
	public void testEmptyForecast() throws Exception {
		exception.expect(IllegalStateException.class);
		URL emptyURL = Resources.getResource(LoaderTests.class, BAD_FOLDER);
		Loader.load(emptyURL.getPath(), FCAST_NAME);
	}

	
	public static void main(String[] args) throws Exception {
		URL emptyURL = Resources.getResource(LoaderTests.class, BAD_FOLDER);
		Loader.load(emptyURL.getPath(), FCAST_NAME);
		
	}
	
	// Problems with forecast structure

}
