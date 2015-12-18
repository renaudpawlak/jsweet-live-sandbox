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
package org.jsweet;

import static def.codemirror.Globals.CodeMirror;
import static def.jquery.Globals.$;
import static jsweet.dom.Globals.alert;
import static jsweet.dom.Globals.console;
import static jsweet.dom.Globals.document;

import def.codemirror.codemirror.EditorConfiguration;
import def.jquery.JQuery;
import def.jquery.JQueryEventObject;
import jsweet.dom.FormData;
import jsweet.dom.XMLHttpRequest;
import jsweet.lang.Interface;
import jsweet.lang.JSON;
import jsweet.lang.Object;

/**
 * Compilation result object contract
 * 
 * @author Louis Grignon
 */
@Interface
abstract class JSweetCompilationServiceResponse {
	public String startTime;
	public String transactionId;
	public long sourceLength;
	public int errorCount;
	public int warningCount;
	public boolean success;
	public String endTime;
	public int durationMillis;
	public String out;
}

/**
 * Editor controller
 * 
 * @author Louis Grignon
 *
 */
class JSweetLiveEditor {

	public static JSweetLiveEditor instance;

	private JQuery javaEditor;
	private JQuery jsEditor;

	private String lastJavaCode;

	public JSweetLiveEditor() {
		// weird singleton in order to debug more easily
		instance = this;
	}

	public void initialize() {
		console.log("initializing JSweetLiveEditor");

		this.javaEditor = $("#javaEditor");
		this.jsEditor = $("#jsEditor");

		this.initEditors();

		this.transpileJavaToJs();

		this.javaEditor.change((JQueryEventObject e) -> {

			console.log("editor touched");

			this.jsEditor.val("Loading.......");

			this.transpileJavaToJs();

			return null;
		});
	}

	private Runnable doTranspile = this::transpileJavaToJs;

	private void initEditors() {
		console.log("initializing the magic editor!");
		CodeMirror(this.javaEditor.get(0), new EditorConfiguration() {
			{
				extraKeys = new Object() {
					{
						$set("Ctrl-S", doTranspile);
						$set("Cmd-S", doTranspile);
					}
				};
				mode = "html";
				lineNumbers = true;
				lineWrapping = true;
				indentWithTabs = true;
				theme = "pastel-on-dark";
				autofocus = true;
			}
		});
	}

	private void transpileJavaToJs() {

		if (this.lastJavaCode == null) {
			this.lastJavaCode = "";
		}

		String javaCode = (String) this.javaEditor.val();
		if (javaCode == "") {
			console.log("no java code to be transpiled");
			return;
		}

		if (javaCode.trim() == this.lastJavaCode.trim()) {
			console.log("did not modified, just doing nothing");
			return;
		}

		this.lastJavaCode = javaCode;

		String serviceUrl = "http://localhost:8580/";

		XMLHttpRequest request = new XMLHttpRequest();
		request.open("POST", serviceUrl, true);

		request.onload = (e) -> {
			JSweetCompilationServiceResponse response = (JSweetCompilationServiceResponse) JSON
					.parse(request.responseText);

			if (!response.success) {
				alert(response.errorCount + " errors!");
			}

			this.jsEditor.val(response.out);

			return null;
		};

		request.onerror = (requestError) -> {
			console.error(requestError);

			return null;
		};

		FormData data = new FormData();
		data.append("javaCode", javaCode);

		try {
			request.send(data);
		} catch (Exception requestError) {
			console.error(requestError);
		}
	}
}

/**
 * Entry point, instantiate and initialize the editor
 * 
 * @author Louis Grignon
 *
 */
public class JSweetPlayground {
	public static void main(String[] args) {
		JSweetLiveEditor editor = new JSweetLiveEditor();
		$(document).ready(() -> {
			editor.initialize();

			return null;
		});
	}
}
