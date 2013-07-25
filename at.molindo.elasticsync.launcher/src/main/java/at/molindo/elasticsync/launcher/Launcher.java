package at.molindo.elasticsync.launcher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.util.tracker.ServiceTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import at.molindo.elasticsync.api.ElasticsyncService;
import at.molindo.elasticsync.api.Index;

import com.beust.jcommander.JCommander;

public class Launcher {

	private static final Logger log = LoggerFactory.getLogger(Launcher.class);
	
	private static final String DEV_BUNDLE_PATH = "target/bundles";
	private static final String JAR_BUNDLE_PATH = "bundles";
	private static final String BUNDLE_PATH = "bundles.path";

	public static void main(String[] args) throws BundleException,
			InterruptedException, IOException, URISyntaxException {

		File bundlesDir = getBundlesDirectory();
		
		// parse arguments
		ElasticsyncCommandLineOptions opts = parseOptions(args);

		// start OSGI framework
		final Framework framework = newFramework();
		framework.start();
		final BundleContext context = framework.getBundleContext();

		// install bundles from bundlesDir
		List<Bundle> installed = new LinkedList<>();
		for (URL bundle : listBundles(bundlesDir)) {
			log.info("installing bundle {}", bundle);
			installed.add(context.installBundle(bundle.toString()));
		}
		
		// start bundles
		for (Bundle bundle : installed) {
			if (bundle.getHeaders().get(Constants.FRAGMENT_HOST) == null) {
				log.info("starting bundle " + bundle.getSymbolicName() + " " + bundle.getVersion());
				bundle.start();
			}
		}

		// register shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread() {

			@Override
			public void run() {
				try {
					log.info("shutting down");
					context.getBundle(0).stop();
					framework.waitForStop(0);
				} catch (BundleException e) {
					log.warn("failed to stop system bundle", e);
				} catch (InterruptedException e) {
					log.warn("waiting for shutdown interrupted", e);
				}
			}
			
		});
		
		// run verifcation
		log.info("environment ready, starting verification");
		try {
			verify(context, opts);
			System.exit(0);
		} catch (Throwable t) {
			log.error("verification failed", t);
			System.exit(1);
		}
	}

	private static File getBundlesDirectory() throws URISyntaxException {
		// find bundles dir
		File bundlesDir;
		
		String bundlePath = System.getProperty(BUNDLE_PATH);
		if (bundlePath != null) {
			bundlesDir = new File(bundlePath);
			log.info("custom mode: loading bundles from {}", bundlesDir.getAbsolutePath());
		} else {
			// running from jar or development?
			CodeSource src = Launcher.class.getProtectionDomain().getCodeSource();
			if (src != null) {
				URL location = src.getLocation();
				log.debug("code source location: {}", location);
				
				if (!"file".equals(location.getProtocol())) {
					throw new RuntimeException("unexpected code source protocol: " + location.getProtocol());
				}
				
				File codeSourceFile = new File(location.toURI());
				
				if (codeSourceFile.isDirectory()) {
					// development
					bundlesDir = new File(DEV_BUNDLE_PATH);
					log.info("development mode: loading bundles from {}", bundlesDir.getAbsolutePath());
				} else if (codeSourceFile.getName().endsWith(".jar")){
					bundlesDir = codeSourceFile;
					log.info("deployment mode: loading bundles from {}", bundlesDir.getAbsolutePath());
				} else {
					throw new RuntimeException("unexpected code source file: " + codeSourceFile.getAbsolutePath());
				}
			} else {
				throw new RuntimeException("unable to get code source. use " + BUNDLE_PATH + " property instead");
			}
		}
		return bundlesDir;
	}

	private static List<URL> listBundles(File bundlesDir) {

		if (bundlesDir.isDirectory()) {
			File[] files = bundlesDir.listFiles(new BundleFilter());
			URL[] urls = new URL[files.length];
			
			for (int i = 0; i < files.length; i++) {
				try {
					urls[i] = files[i].toURI().toURL();
				} catch (MalformedURLException e) {
					throw new RuntimeException("failed to convert file to url: " + files[i].getAbsolutePath());
				}
			}
			
			return Arrays.asList(urls);
			
		} else {
			try (
				ZipFile zipFile = new ZipFile(bundlesDir)
			){
				
				List<URL> urls = new ArrayList<>();
				String jarUrlPrefix = "jar:" + bundlesDir.toURI().toURL() + "!/";
				
				for (ZipEntry entry : Collections.list(zipFile.entries())) {
					String name = entry.getName();
					if (name.startsWith(JAR_BUNDLE_PATH) && name.endsWith(".jar")) {
						urls.add(new URL(jarUrlPrefix + name));
					}
				}
				return urls;
			} catch (IOException e) {
				throw new RuntimeException("failed to read bundles from zip file " + bundlesDir.getAbsolutePath());
			}
		}
	}

	private static ElasticsyncCommandLineOptions parseOptions(String[] args) {
		ElasticsyncCommandLineOptions options = new ElasticsyncCommandLineOptions();
		JCommander jcmdr = new JCommander(options, args);
		
		if (options.help) {
			jcmdr.setProgramName(Launcher.class.getName());
			jcmdr.usage();
			System.exit(0);
		}
		
		return options;
	}
	

	private static Framework newFramework() {
		FrameworkFactory frameworkFactory = ServiceLoader
				.load(FrameworkFactory.class).iterator().next();

		Map<String, String> config = new HashMap<>();
		// Control where OSGi stores its persistent data:
		config.put(Constants.FRAMEWORK_STORAGE, "/tmp/osgidata");
		// Request OSGi to clean its storage area on startup
		config.put(Constants.FRAMEWORK_STORAGE_CLEAN, "true");
		// export packages from classpath
		config.put(Constants.FRAMEWORK_SYSTEMPACKAGES_EXTRA,
				"at.molindo.elasticsync.api, org.slf4j;version=\"1.7.3\", javax.xml.parsers, sun.misc");

		Framework framework = frameworkFactory.newFramework(config);
		return framework;
	}

	private static void verify(final BundleContext context,
			ElasticsyncCommandLineOptions opts) {
		
		Index source = new Index((opts.sourceHosts), opts.indexName, opts.sourceVersion);
		Index target = new Index((opts.targetHosts), opts.indexName, opts.targetVersion);

		ServiceTracker<ElasticsyncService, ElasticsyncService> tracker = new ServiceTracker<>(context, ElasticsyncService.class, null);
		tracker.open();
		
		ElasticsyncService svc;
		try {
			svc = tracker.waitForService(30000);
			svc.verify(source, target, opts.types, opts.query, opts.update);
		} catch (InterruptedException e) {
			throw new RuntimeException("waiting for service interrupted", e);
		} finally {
			tracker.close();
		}
	}

	private static final class BundleFilter implements FilenameFilter {
		@Override
		public boolean accept(File dir, String name) {
			return name.toLowerCase().endsWith(".jar");
		}
	}

}
