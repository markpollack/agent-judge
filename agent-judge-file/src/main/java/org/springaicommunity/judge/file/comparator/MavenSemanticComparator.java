package org.springaicommunity.judge.file.comparator;

import org.apache.maven.model.Build;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.StringReader;
import java.util.*;

/**
 * Compares Maven POMs semantically rather than textually.
 * <p>
 * Handles Maven-specific equivalences like: - Default groupId for maven plugins
 * (org.apache.maven.plugins) - Whitespace/formatting differences - Element ordering where
 * Maven doesn't care
 */
public class MavenSemanticComparator {

	private static final String DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

	public record ComparisonResult(boolean equivalent, List<String> differences) {
		public static ComparisonResult match() {
			return new ComparisonResult(true, List.of());
		}

		public static ComparisonResult mismatch(String... diffs) {
			return new ComparisonResult(false, Arrays.asList(diffs));
		}

		public static ComparisonResult mismatch(List<String> diffs) {
			return new ComparisonResult(false, diffs);
		}
	}

	public ComparisonResult compare(String expected, String actual) {
		try {
			MavenXpp3Reader reader = new MavenXpp3Reader();
			Model expectedModel = reader.read(new StringReader(expected));
			Model actualModel = reader.read(new StringReader(actual));

			List<String> differences = new ArrayList<>();

			compareCoordinates(expectedModel, actualModel, differences);
			compareBuild(expectedModel.getBuild(), actualModel.getBuild(), differences);
			compareDependencies(expectedModel, actualModel, differences);

			if (differences.isEmpty()) {
				return ComparisonResult.match();
			}
			return ComparisonResult.mismatch(differences);

		}
		catch (Exception e) {
			String normalizedExpected = expected.replaceAll("\\s+", " ").trim();
			String normalizedActual = actual.replaceAll("\\s+", " ").trim();
			if (normalizedExpected.equals(normalizedActual)) {
				return ComparisonResult.match();
			}
			return ComparisonResult
				.mismatch("POM parse failed, whitespace-normalized comparison also failed: " + e.getMessage());
		}
	}

	private void compareCoordinates(Model expected, Model actual, List<String> differences) {
		if (!Objects.equals(expected.getGroupId(), actual.getGroupId())) {
			differences.add("groupId differs: expected=" + expected.getGroupId() + ", actual=" + actual.getGroupId());
		}
		if (!Objects.equals(expected.getArtifactId(), actual.getArtifactId())) {
			differences
				.add("artifactId differs: expected=" + expected.getArtifactId() + ", actual=" + actual.getArtifactId());
		}
		if (!Objects.equals(expected.getVersion(), actual.getVersion())) {
			differences.add("version differs: expected=" + expected.getVersion() + ", actual=" + actual.getVersion());
		}
	}

	private void compareBuild(Build expected, Build actual, List<String> differences) {
		if (expected == null && actual == null) {
			return;
		}
		if (expected == null) {
			differences.add("Unexpected build section in actual");
			return;
		}
		if (actual == null) {
			differences.add("Missing build section in actual");
			return;
		}

		comparePlugins(expected.getPlugins(), actual.getPlugins(), "build/plugins", differences);
		comparePluginManagement(expected.getPluginManagement(), actual.getPluginManagement(), differences);
	}

	private void comparePluginManagement(PluginManagement expected, PluginManagement actual, List<String> differences) {
		if (expected == null && actual == null) {
			return;
		}
		if (expected == null) {
			differences.add("Unexpected pluginManagement section in actual");
			return;
		}
		if (actual == null) {
			differences.add("Missing pluginManagement section in actual");
			return;
		}

		comparePlugins(expected.getPlugins(), actual.getPlugins(), "pluginManagement/plugins", differences);
	}

	private void comparePlugins(List<Plugin> expected, List<Plugin> actual, String path, List<String> differences) {
		if (expected == null)
			expected = List.of();
		if (actual == null)
			actual = List.of();

		Map<String, Plugin> expectedMap = new LinkedHashMap<>();
		for (Plugin p : expected) {
			expectedMap.put(normalizePluginKey(p), p);
		}

		Map<String, Plugin> actualMap = new LinkedHashMap<>();
		for (Plugin p : actual) {
			actualMap.put(normalizePluginKey(p), p);
		}

		for (String key : expectedMap.keySet()) {
			if (!actualMap.containsKey(key)) {
				differences.add(path + ": missing plugin " + key);
			}
		}

		for (String key : actualMap.keySet()) {
			if (!expectedMap.containsKey(key)) {
				differences.add(path + ": unexpected plugin " + key);
			}
		}

		for (String key : expectedMap.keySet()) {
			if (actualMap.containsKey(key)) {
				comparePlugin(expectedMap.get(key), actualMap.get(key), path + "/" + key, differences);
			}
		}
	}

	private String normalizePluginKey(Plugin plugin) {
		String groupId = plugin.getGroupId();
		if (groupId == null || groupId.isEmpty()) {
			groupId = DEFAULT_PLUGIN_GROUP_ID;
		}
		return groupId + ":" + plugin.getArtifactId();
	}

	private void comparePlugin(Plugin expected, Plugin actual, String path, List<String> differences) {
		if (expected.getVersion() != null && !expected.getVersion().isEmpty()) {
			if (!Objects.equals(expected.getVersion(), actual.getVersion())) {
				differences.add(path + ": version differs: expected=" + expected.getVersion() + ", actual="
						+ actual.getVersion());
			}
		}

		Xpp3Dom expectedConfig = (Xpp3Dom) expected.getConfiguration();
		Xpp3Dom actualConfig = (Xpp3Dom) actual.getConfiguration();

		compareConfiguration(expectedConfig, actualConfig, path + "/configuration", differences);
	}

	private void compareConfiguration(Xpp3Dom expected, Xpp3Dom actual, String path, List<String> differences) {
		if (expected == null && actual == null) {
			return;
		}
		if (expected == null) {
			differences.add(path + ": unexpected configuration in actual");
			return;
		}
		if (actual == null) {
			differences.add(path + ": missing configuration in actual");
			return;
		}

		Set<String> expectedChildren = new HashSet<>();
		for (Xpp3Dom child : expected.getChildren()) {
			expectedChildren.add(child.getName());
		}

		Set<String> actualChildren = new HashSet<>();
		for (Xpp3Dom child : actual.getChildren()) {
			actualChildren.add(child.getName());
		}

		for (String name : expectedChildren) {
			if (!actualChildren.contains(name)) {
				differences.add(path + ": missing element <" + name + ">");
			}
		}

		for (String name : actualChildren) {
			if (!expectedChildren.contains(name)) {
				differences.add(path + ": unexpected element <" + name + ">");
			}
		}

		for (String name : expectedChildren) {
			if (actualChildren.contains(name)) {
				Xpp3Dom[] expectedKids = expected.getChildren(name);
				Xpp3Dom[] actualKids = actual.getChildren(name);

				if (expectedKids.length != actualKids.length) {
					differences.add(path + "/" + name + ": count differs: expected=" + expectedKids.length + ", actual="
							+ actualKids.length);
				}
				else {
					for (int i = 0; i < expectedKids.length; i++) {
						compareConfiguration(expectedKids[i], actualKids[i], path + "/" + name + "[" + i + "]",
								differences);
					}
				}
			}
		}

		if (expected.getChildCount() == 0 && actual.getChildCount() == 0) {
			String expectedValue = normalizeValue(expected.getValue());
			String actualValue = normalizeValue(actual.getValue());
			if (!Objects.equals(expectedValue, actualValue)) {
				differences.add(path + ": value differs: expected=" + expectedValue + ", actual=" + actualValue);
			}
		}
	}

	private String normalizeValue(String value) {
		if (value == null)
			return null;
		return value.trim();
	}

	private void compareDependencies(Model expected, Model actual, List<String> differences) {
		int expectedCount = expected.getDependencies() != null ? expected.getDependencies().size() : 0;
		int actualCount = actual.getDependencies() != null ? actual.getDependencies().size() : 0;

		if (expectedCount != actualCount) {
			differences.add("Dependency count differs: expected=" + expectedCount + ", actual=" + actualCount);
		}
	}

}
