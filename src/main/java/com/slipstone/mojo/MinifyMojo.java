package com.slipstone.mojo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.sonatype.plexus.build.incremental.BuildContext;

import com.yahoo.platform.yui.compressor.CssCompressor;
import com.yahoo.platform.yui.compressor.JavaScriptCompressor;

/**
 * Generate md5 fingerprints of static resources
 * 
 * @author Keith
 * @goal minify
 */
public class MinifyMojo extends AbstractMojo {
	private static final String LINE_SEPARATOR = System
			.getProperty("line.separator");

	// plexus injected fields first
	/**
	 * @parameter expression="${project}"
	 * @required
	 * @readonly
	 */
	protected MavenProject project;

	/**
	 * Specifies the source of the javascript files.
	 * 
	 * @parameter default-value="${basedir}/src/main"
	 * @required
	 */
	private File sourceJSFolder;

	/**
	 * Specifies the source of the static files.
	 * 
	 * @parameter default-value="${basedir}/src/main/resources"
	 * @required
	 */
	private File sourceFolder;

	/**
	 * The html file where the javascript and css dependencies are defined
	 * 
	 * @parameter expression= "/index.html"
	 * @required
	 */
	private String html;

	/**
	 * The target for the html file using uber css and js files
	 * 
	 * @parameter expression= "/generated/index-uber.html"
	 * @required
	 */
	private String htmlUber;

	/**
	 * The target for the html file using less and less processor
	 * 
	 * @parameter expression= "/generated/index-less.html"
	 * @required
	 */
	private String htmlLess;
	/**
	 * The target for the compressed slipstone css
	 * 
	 * @parameter expression= "/generated/css/uber.css"
	 * @required
	 */
	private String uberCss;
	/**
	 * The target for the compressed 3rd party css
	 * 
	 * @parameter expression= "/generated/css/3p/uber.css"
	 * @required
	 */
	private String uber3pCss;
	/**
	 * The target for the compressed slipstone javascript
	 * 
	 * @parameter expression= "/generated/js/uber.js"
	 * @required
	 */
	private String uberJs;
	/**
	 * The target for the compressed 3rd party javascript
	 * 
	 * @parameter expression= "/generated/js/3p/uber.js"
	 * @required
	 */
	private String uber3pJs;

	/**
	 * [js only] Display possible errors in the code
	 * 
	 * @parameter expression="${maven.yuicompressor.jswarm}"
	 *            default-value="false"
	 */
	protected boolean jswarn;
	/**
	 * define if plugin must stop/fail on warnings.
	 * 
	 * @parameter expression="${maven.yuicompressor.failOnWarning}"
	 *            default-value="false"
	 */
	protected boolean failOnWarning;
	/**
	 * Insert line breaks in output after the specified column number.
	 * 
	 * @parameter expression="${maven.yuicompressor.linebreakpos}"
	 *            default-value="65535"
	 */
	private int linebreakpos;

	/**
	 * [js only] Minify only, do not obfuscate.
	 * 
	 * @parameter expression="${maven.yuicompressor.nomunge}"
	 *            default-value="false"
	 */
	private boolean nomunge;

	/**
	 * [js only] Preserve unnecessary semicolons.
	 * 
	 * @parameter expression="${maven.yuicompressor.preserveAllSemiColons}"
	 *            default-value="false"
	 */
	private boolean preserveAllSemiColons;

	/**
	 * [js only] disable all micro optimizations.
	 * 
	 * @parameter expression="${maven.yuicompressor.disableOptimizations}"
	 *            default-value="false"
	 */
	private boolean disableOptimizations;

	/**
	 * @component role="org.sonatype.plexus.build.incremental.BuildContext"
	 * @required
	 */
	private BuildContext buildContext;

	// Local fields below this point
	private JSErrorReporter jsErrorReporter;

	/** @see org.apache.maven.plugin.Mojo#execute() */
	public void execute() throws MojoExecutionException {
		if (failOnWarning) {
			jswarn = true;
		}
		jsErrorReporter = new JSErrorReporter(getLog(), jswarn);

		try {
			doIt();
		} catch (final Exception e) {
			throw new MojoExecutionException("", e);
		}
	}

	private void doIt() throws IOException {
		final String rawHtml = FileUtils.readFileToString(
				new File(sourceFolder, html), "UTF-8").replaceAll("<!--.*?-->", "");
		final Pattern less = Pattern
				.compile(
						"<link\\s+rel=\"stylesheet/less\"\\s+href=\"/css/styles.less\".*?>\\s*<script\\s+src=\"/js/3p/less.*?\\.js\".*?</script>",
						Pattern.MULTILINE);
		final String lessHtml = less
				.matcher(rawHtml)
				.replaceFirst(
						"<link rel=\"stylesheet\" href=\"/generated/css/styles.css\" type=\"text/css\">");
		final File htmlLessFile = new File(sourceFolder, htmlLess);
		FileUtils.writeStringToFile(htmlLessFile, lessHtml);
		buildContext.refresh(htmlLessFile);

		final StringBuilder htmlContent = new StringBuilder(lessHtml);
		compress(
				false,
				join(sourceFolder,
						extractAndSwap(
								htmlContent,
								"<link\\s+rel=\"stylesheet\"\\s+href=\"(/generated/css/[^3][^\"]+)\".*?>",
								uberCss)), new File(sourceFolder, uberCss));
		compress(
				false,
				join(sourceFolder,
						extractAndSwap(
								htmlContent,
								"<link\\s+rel=\"stylesheet\"\\s+href=\"(/css/3p/[^\"]+)\".*?>",
								uber3pCss)), new File(sourceFolder, uber3pCss));
		compress(
				true,
				join(sourceJSFolder,
						extractAndSwap(
								htmlContent,
								"<script\\s+src=\"(/js/[^3][^\"]+)\".*?</script>",
								uberJs)), new File(sourceFolder, uberJs));
		compress(
				true,
				join(sourceJSFolder,
						extractAndSwap(
								htmlContent,
								"<script\\s+src=\"(/js/3p/[^\"]+)\".*?</script>",
								uber3pJs)), new File(sourceFolder, uber3pJs));
		final File htmlUberFile = new File(sourceFolder, htmlUber);
		FileUtils.writeStringToFile(htmlUberFile, htmlContent.toString());
		buildContext.refresh(htmlUberFile);
	}

	/**
	 * @param extract
	 * @param servletContext
	 * @return
	 * @throws IOException
	 */
	private String join(final File sourceFolder, final List<String> files)
			throws IOException {
		final StringBuilder sb = new StringBuilder();
		for (final String file : files) {
			final String contents = FileUtils.readFileToString(new File(
					sourceFolder, file), "UTF-8");
			sb.append(contents).append(LINE_SEPARATOR);
		}
		return sb.toString();
	}

	/**
	 * Compress the javascript and write to a given file
	 * 
	 * @param js
	 *            a flag = true mean javascript, false means css
	 * @param source
	 *            The javascript or css to be compressed
	 * @param target
	 *            The file to write the results to
	 * @throws IOException
	 */
	private void compress(final boolean js, final String source,
			final File target) throws IOException {
		if (!target.getParentFile().exists()) {
			target.getParentFile().mkdirs();
		}
		OutputStream os = new FileOutputStream(target);
		final Writer fw = new OutputStreamWriter(os, "UTF-8");
		if (js) {
			new JavaScriptCompressor(new StringReader(source), jsErrorReporter)
					.compress(fw, linebreakpos, !nomunge, jswarn,
							preserveAllSemiColons, disableOptimizations);
		} else {
			new CssCompressor(new StringReader(source)).compress(fw,
					linebreakpos);
		}
		IOUtils.closeQuietly(fw);
		IOUtils.closeQuietly(os);
		getLog().info("generated " + target);
		buildContext.refresh(target);
	}

	/**
	 * Find the entries matching the first capture group of pattern in source
	 * Remove all but the last one<br />
	 * Replace the last one with replacement
	 * 
	 * @param source
	 *            The source html
	 * @param pattern
	 *            The pattern to find
	 * @param replacement
	 *            The replacement for the last item
	 * @return A list of the patterns that matched
	 */
	private static List<String> extractAndSwap(final StringBuilder source,
			final String pattern, final String replacement) {
		final List<String> results = new ArrayList<String>();
		final Pattern p = Pattern.compile(pattern);
		final Pattern p2 = Pattern.compile(pattern + "\\s*", Pattern.MULTILINE);
		// first find the matches
		final Matcher matcher = p.matcher(source);
		while (matcher.find()) {
			results.add(matcher.group(1));
		}
		// then do the replacements
		String temp = new String(source);
		for (int i = 0; i < results.size() - 1; i++) {
			temp = p2.matcher(temp).replaceFirst("");
		}
		matcher.reset(temp).find();
		temp = temp.substring(0, matcher.start(1)) + replacement
				+ temp.substring(matcher.end(1));
		source.delete(0, source.length()).append(temp);
		return results;
	}
}
