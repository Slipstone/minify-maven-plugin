package com.slipstone.mojo;

import org.apache.commons.lang.StringUtils;
import org.apache.maven.plugin.logging.Log;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 * Copied from net.alchim31.maven.yuicompressor.ErrorReporter4Mojo
 * 
 * @author David Bernard
 * @author keith (add comments and some minor modifications)
 */
public class JSErrorReporter implements ErrorReporter {
	private String defaultFilename;
	private final boolean recordWarnings;
	private final Log log;
	private int warningCount;
	private int errorCount;

	/**
	 * Constructor
	 * 
	 * @param log
	 *            Maven logger
	 * @param recordWarnings
	 *            flag to indicate if warnings should be recorded
	 */
	public JSErrorReporter(final Log log, final boolean recordWarnings) {
		this.log = log;
		this.recordWarnings = recordWarnings;
	}

	/**
	 * Setter for defaultFilename
	 * 
	 * @param defaultFilename
	 *            The new value for default filename
	 */
	public void setDefaultFileName(final String defaultFilename) {
		this.defaultFilename = StringUtils.stripToNull(defaultFilename);
	}

	/**
	 * Getter for errorCount
	 * 
	 * @return errorCount
	 */
	public int getErrorCount() {
		return errorCount;
	}

	/**
	 * Getter for warning count
	 * 
	 * @return warningCount
	 */
	public int getWarningCount() {
		return warningCount;
	}

	/** {@inheritDoc} */
	public void error(final String message, final String sourceName,
			final int line, final String lineSource, final int lineOffset) {
		final String fullMessage = newMessage(message, sourceName, line,
				lineSource, lineOffset);
		log.error(fullMessage);
		errorCount++;
	}

	/** {@inheritDoc} */
	public EvaluatorException runtimeError(final String message,
			final String sourceName, final int line, final String lineSource,
			final int lineOffset) {
		error(message, sourceName, line, lineSource, lineOffset);
		throw new EvaluatorException(message, sourceName, line, lineSource,
				lineOffset);
	}

	/** {@inheritDoc} */
	public void warning(final String message, final String sourceName,
			final int line, final String lineSource, final int lineOffset) {
		if (recordWarnings) {
			final String fullMessage = newMessage(message, sourceName, line,
					lineSource, lineOffset);
			log.warn(fullMessage);
			warningCount++;
		}
	}

	/**
	 * Format a message ready for logging
	 * 
	 * @param message
	 *            The bare message
	 * @param sourceName
	 *            The name of the source file
	 * @param line
	 *            The line number of the message
	 * @param lineSource
	 *            The line of source containing the error
	 * @param lineOffset
	 *            The column number in the source
	 * @return The formatted message
	 */
	private String newMessage(final String message, final String sourceName,
			final int line, final String lineSource, final int lineOffset) {
		final StringBuilder sb = new StringBuilder();
		final String sourceNamePrime = StringUtils.stripToNull(sourceName) == null ? defaultFilename
				: sourceName;
		if (sourceNamePrime != null) {
			sb.append(sourceNamePrime).append(":line ").append(line)
					.append(":column ").append(lineOffset).append(':');
		}
		if ((message != null) && (message.length() != 0)) {
			sb.append(message);
		} else {
			sb.append("unknown error");
		}
		if ((lineSource != null) && (lineSource.length() != 0)) {
			sb.append("\n\t").append(lineSource);
		}
		return sb.toString();
	}
}
