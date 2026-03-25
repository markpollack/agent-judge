package org.springaicommunity.judge.file.comparator;

import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.*;

/**
 * Compares arbitrary XML documents semantically rather than textually.
 * <p>
 * Handles common equivalences:
 * <ul>
 * <li>Whitespace and formatting differences</li>
 * <li>XML declaration presence/absence</li>
 * <li>Sibling element ordering</li>
 * <li>Text node normalization</li>
 * </ul>
 */
public class XmlSemanticComparator {

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
			Document expectedDoc = parse(expected);
			Document actualDoc = parse(actual);

			List<String> differences = new ArrayList<>();
			compareElements(expectedDoc.getDocumentElement(), actualDoc.getDocumentElement(), "", differences);

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
				.mismatch("XML parse failed, whitespace-normalized comparison also failed: " + e.getMessage());
		}
	}

	private Document parse(String xml) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(false);
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new InputSource(new StringReader(xml)));
	}

	private void compareElements(Element expected, Element actual, String path, List<String> differences) {
		String currentPath = path.isEmpty() ? expected.getTagName() : path + "/" + expected.getTagName();

		if (!expected.getTagName().equals(actual.getTagName())) {
			differences.add(currentPath + ": tag name differs: expected=<" + expected.getTagName() + ">, actual=<"
					+ actual.getTagName() + ">");
			return;
		}

		compareAttributes(expected, actual, currentPath, differences);

		List<Element> expectedChildren = getChildElements(expected);
		List<Element> actualChildren = getChildElements(actual);

		if (expectedChildren.isEmpty() && actualChildren.isEmpty()) {
			String expectedText = normalizeText(expected.getTextContent());
			String actualText = normalizeText(actual.getTextContent());
			if (!expectedText.equals(actualText)) {
				differences.add(currentPath + ": text differs: expected=\"" + expectedText + "\", actual=\""
						+ actualText + "\"");
			}
			return;
		}

		Map<String, List<Element>> expectedByTag = groupByTagName(expectedChildren);
		Map<String, List<Element>> actualByTag = groupByTagName(actualChildren);

		for (String tag : expectedByTag.keySet()) {
			if (!actualByTag.containsKey(tag)) {
				differences.add(currentPath + ": missing element <" + tag + ">");
			}
		}
		for (String tag : actualByTag.keySet()) {
			if (!expectedByTag.containsKey(tag)) {
				differences.add(currentPath + ": unexpected element <" + tag + ">");
			}
		}

		for (String tag : expectedByTag.keySet()) {
			if (actualByTag.containsKey(tag)) {
				List<Element> expList = expectedByTag.get(tag);
				List<Element> actList = actualByTag.get(tag);

				if (expList.size() != actList.size()) {
					differences.add(currentPath + "/" + tag + ": count differs: expected=" + expList.size()
							+ ", actual=" + actList.size());
				}
				else {
					for (int i = 0; i < expList.size(); i++) {
						compareElements(expList.get(i), actList.get(i), currentPath, differences);
					}
				}
			}
		}
	}

	private void compareAttributes(Element expected, Element actual, String path, List<String> differences) {
		NamedNodeMap expectedAttrs = expected.getAttributes();
		NamedNodeMap actualAttrs = actual.getAttributes();

		Set<String> expectedNames = new HashSet<>();
		for (int i = 0; i < expectedAttrs.getLength(); i++) {
			expectedNames.add(expectedAttrs.item(i).getNodeName());
		}

		Set<String> actualNames = new HashSet<>();
		for (int i = 0; i < actualAttrs.getLength(); i++) {
			actualNames.add(actualAttrs.item(i).getNodeName());
		}

		for (String name : expectedNames) {
			if (!actualNames.contains(name)) {
				differences.add(path + ": missing attribute @" + name);
			}
			else {
				String expVal = expected.getAttribute(name);
				String actVal = actual.getAttribute(name);
				if (!expVal.equals(actVal)) {
					differences.add(path + "/@" + name + ": value differs: expected=\"" + expVal + "\", actual=\""
							+ actVal + "\"");
				}
			}
		}

		for (String name : actualNames) {
			if (!expectedNames.contains(name)) {
				differences.add(path + ": unexpected attribute @" + name);
			}
		}
	}

	private List<Element> getChildElements(Node parent) {
		List<Element> elements = new ArrayList<>();
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element element) {
				elements.add(element);
			}
		}
		return elements;
	}

	private Map<String, List<Element>> groupByTagName(List<Element> elements) {
		Map<String, List<Element>> grouped = new LinkedHashMap<>();
		for (Element e : elements) {
			grouped.computeIfAbsent(e.getTagName(), k -> new ArrayList<>()).add(e);
		}
		return grouped;
	}

	private String normalizeText(String text) {
		if (text == null)
			return "";
		return text.trim().replaceAll("\\s+", " ");
	}

}
