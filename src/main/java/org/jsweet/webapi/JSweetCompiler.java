/* 
 * Copyright (C) 2015 Louis Grignon <louis.grignon@gmail.com>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jsweet.webapi;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.jsweet.transpiler.EcmaScriptComplianceLevel;
import org.jsweet.transpiler.JSweetTranspiler;
import org.jsweet.transpiler.ModuleKind;
import org.jsweet.transpiler.SourceFile;
import org.jsweet.transpiler.util.ConsoleTranspilationHandler;
import org.jsweet.transpiler.util.ErrorCountTranspilationHandler;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import fi.iki.elonen.NanoHTTPD;

/**
 * Compiler web service
 * 
 * @author Louis Grignon
 *
 */
public class JSweetCompiler extends NanoHTTPD {

	private final static DateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	private final static Logger logger = Logger.getLogger(JSweetCompiler.class);
	private JSweetTranspiler transpiler;

	public JSweetCompiler() throws IOException {
		super(8580);
		start();

		logger.info("...\nRunning! Point your browers to http://localhost:" + getListeningPort() + "/ \n");
	}

	public static void main(String[] args) {

		logger.info("starting server");
		Thread serverThread = new Thread() {
			@Override
			public void run() {
				try {
					JSweetCompiler server = new JSweetCompiler();
					logger.info("server=" + server);

					synchronized (this) {
						wait();
					}
				} catch (Exception ioe) {
					logger.error("server failed", ioe);
				}
			}
		};
		serverThread.setDaemon(false);
		serverThread.start();

		logger.info("exiting");
	}

	@Override
	public Response serve(IHTTPSession session) {
		logger.info("compilation request from " + session.getHeaders());

		Map<String, String> files = new HashMap<String, String>();
		try {
			session.parseBody(files);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		String body = "unread";
		// try {
		// while(session.getInputStream().available()) {
		// body += session.getInputStream().re
		// }
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		logger.info("method=" + session.getMethod());
		logger.debug("parmsNames=" + session.getParms().keySet());
		logger.debug("parms=" + session.getParms());
		logger.debug("queryParameterString=" + session.getQueryParameterString());
		logger.info("body=" + body);

		String javaCode = session.getParms().get("javaCode");
		logger.info("javaCode=" + javaCode);
		if (javaCode == null) {
			javaCode = "";
		}
		// StringBuilder responseBuilder = new StringBuilder();

		JsonObject output = applyJSweetMagic(transpiler(), javaCode);

		return newFixedLengthResponse(new GsonBuilder() //
				.setPrettyPrinting() //
				.create() //
				.toJson(output));
	}

	private synchronized JsonObject applyJSweetMagic(JSweetTranspiler transpiler, String javaCode) {
		String transactionId = UUID.randomUUID().toString();
		Date startTime = new Date();

		JsonObject result = new JsonObject();
		result.addProperty("startTime", OUTPUT_DATE_FORMAT.format(startTime));
		result.addProperty("transactionId", transactionId);
		result.addProperty("sourceLength", javaCode.length());

		ErrorCountTranspilationHandler transpilationHandler = new ErrorCountTranspilationHandler(
				new ConsoleTranspilationHandler());

		if (!isBlank(javaCode)) {
			try {

				File sourceFile = new File(FileUtils.getTempDirectory(), transactionId + "/HelloWorld.java");
				FileUtils.write(sourceFile, javaCode);
				logger.info("javaFile written to " + sourceFile.getCanonicalPath());

				SourceFile[] sources = { new SourceFile(sourceFile) };

				logger.info("transpiling " + sources);
				transpiler.transpile(transpilationHandler, sources);

				// String className = "org.jsweet.HelloWorld";

				// File outJsFile = new File(transpiler.getJsOutputDir(),
				// sourceFile.getName().replace(".java", ".js"));
				File outJsFile = new File(transpiler.getJsOutputDir(), "org/jsweet/HelloWorld.js");
				logger.info("transpiled, outFile = " + outJsFile + " exists?" + outJsFile.exists());

				String out = "";
				if (outJsFile.exists()) {
					out = FileUtils.readFileToString(outJsFile);
				}
				result.addProperty("out", out.replace("\n", ""));

			} catch (Throwable t) {
				logger.error("critical error during transpilation", t);
				transpilationHandler.reportSilentError();
			}
		}

		Date endTime = new Date();

		result.addProperty("errorCount", transpilationHandler.getErrorCount());
		result.addProperty("warningCount", transpilationHandler.getWarningCount());
		result.addProperty("success", transpilationHandler.getErrorCount() == 0);
		result.addProperty("endTime", OUTPUT_DATE_FORMAT.format(endTime));
		result.addProperty("durationMillis", endTime.getTime() - startTime.getTime());

		return result;
	}

	private JSweetTranspiler transpiler() {

		if (transpiler == null) {
			synchronized (JSweetCompiler.class) {
				if (transpiler == null) {
					File tsOut = new File(".ts");
					tsOut.mkdirs();

					File jsOut = new File(".js");
					jsOut.mkdirs();

					String classPath = System.getProperty("java.class.path");

					EcmaScriptComplianceLevel targetVersion = EcmaScriptComplianceLevel.ES6;
					ModuleKind module = ModuleKind.none;
					String encoding = "UTF-8";
					boolean verbose = true;
					boolean sourceMaps = false;
					boolean noRootDirectories = false;
					boolean enableAssertions = false;
					String jdkHome = System.getProperty("java.home");

					logger.debug("jsOut: " + jsOut);
					logger.debug("tsOut: " + tsOut);
					logger.debug("ecmaTargetVersion: " + targetVersion);
					logger.debug("moduleKind: " + module);
					logger.debug("sourceMaps: " + sourceMaps);
					logger.debug("verbose: " + verbose);
					logger.debug("noRootDirectories: " + noRootDirectories);
					logger.debug("enableAssertions: " + enableAssertions);
					logger.debug("encoding: " + encoding);
					logger.debug("jdkHome: " + jdkHome);

					transpiler = new JSweetTranspiler(tsOut, jsOut, classPath);
					transpiler.setTscWatchMode(false);
					transpiler.setEcmaTargetVersion(targetVersion);
					transpiler.setModuleKind(module);
					transpiler.setBundle(false);
					// transpiler.setBundlesDirectory(StringUtils.isBlank(bundlesDirectory)
					// ? null : new File(bundlesDirectory));
					transpiler.setPreserveSourceLineNumbers(sourceMaps);
					transpiler.setEncoding(encoding);
					transpiler.setNoRootDirectories(noRootDirectories);
					transpiler.setIgnoreAssertions(!enableAssertions);

				}
			}
		}

		return transpiler;
	}
}
