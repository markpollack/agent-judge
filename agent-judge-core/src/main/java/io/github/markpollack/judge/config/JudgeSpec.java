/*
 * Copyright 2024 Spring AI Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.markpollack.judge.config;

import java.util.Map;

/**
 * Configuration specification for Judge instances loaded from YAML or other configuration
 * sources.
 *
 * <p>
 * This is a pure data transfer object (DTO) with no behavior. It holds configuration data
 * that driver programs (like spring-ai-bench) can use to instantiate judges via Spring DI
 * or other mechanisms.
 * </p>
 *
 * <p>
 * Example YAML:
 * </p>
 *
 * Executable examples are maintained in the Agent Judge Tutorial: https://github.com/markpollack/agent-judge-tutorial.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
public class JudgeSpec {

	private String type;

	private String path;

	private String expected;

	private String matchMode;

	private Map<String, Object> config;

	/** Create an empty specification for data binding. */
	public JudgeSpec() {
	}

	/**
	 * Create a specification.
	 * @param type judge type identifier
	 * @param path target path
	 * @param expected expected value
	 * @param matchMode comparison mode
	 */
	public JudgeSpec(String type, String path, String expected, String matchMode) {
		this.type = type;
		this.path = path;
		this.expected = expected;
		this.matchMode = matchMode;
	}

	/**
	 * Return the judge type identifier.
	 * @return judge type identifier
	 */
	public String getType() {
		return type;
	}

	/**
	 * Set the judge type identifier.
	 * @param type judge type identifier
	 */
	public void setType(String type) {
		this.type = type;
	}

	/**
	 * Return the target path.
	 * @return target path
	 */
	public String getPath() {
		return path;
	}

	/**
	 * Set the target path.
	 * @param path target path
	 */
	public void setPath(String path) {
		this.path = path;
	}

	/**
	 * Return the expected value.
	 * @return expected value
	 */
	public String getExpected() {
		return expected;
	}

	/**
	 * Set the expected value.
	 * @param expected expected value
	 */
	public void setExpected(String expected) {
		this.expected = expected;
	}

	/**
	 * Return the comparison mode.
	 * @return comparison mode
	 */
	public String getMatchMode() {
		return matchMode;
	}

	/**
	 * Set the comparison mode.
	 * @param matchMode comparison mode
	 */
	public void setMatchMode(String matchMode) {
		this.matchMode = matchMode;
	}

	/**
	 * Return additional judge configuration.
	 * @return additional judge configuration
	 */
	public Map<String, Object> getConfig() {
		return config;
	}

	/**
	 * Set additional judge configuration.
	 * @param config additional judge configuration
	 */
	public void setConfig(Map<String, Object> config) {
		this.config = config;
	}

}
