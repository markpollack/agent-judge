package io.github.markpollack.judge.file.comparator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Compares Java source files semantically rather than textually.
 * <p>
 * Handles common equivalences:
 * <ul>
 * <li>Whitespace and formatting differences</li>
 * <li>Import ordering differences (semantically equivalent)</li>
 * <li>Comment differences (ignored)</li>
 * <li>Trailing semicolons and braces positioning</li>
 * </ul>
 */
public class JavaSemanticComparator {

	private final JavaParser parser;

	public JavaSemanticComparator() {
		ParserConfiguration config = new ParserConfiguration()
			.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
		this.parser = new JavaParser(config);
	}

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
			ParseResult<CompilationUnit> expectedResult = parser.parse(expected);
			ParseResult<CompilationUnit> actualResult = parser.parse(actual);

			if (!expectedResult.isSuccessful()) {
				return ComparisonResult.mismatch("Failed to parse expected Java: " + expectedResult.getProblems()
					.stream()
					.map(p -> p.getMessage())
					.collect(Collectors.joining("; ")));
			}

			if (!actualResult.isSuccessful()) {
				return ComparisonResult.mismatch("Failed to parse actual Java: " + actualResult.getProblems()
					.stream()
					.map(p -> p.getMessage())
					.collect(Collectors.joining("; ")));
			}

			CompilationUnit expectedCu = expectedResult.getResult().orElseThrow();
			CompilationUnit actualCu = actualResult.getResult().orElseThrow();

			List<String> differences = new ArrayList<>();
			compareCompilationUnits(expectedCu, actualCu, differences);

			if (differences.isEmpty()) {
				return ComparisonResult.match();
			}
			return ComparisonResult.mismatch(differences);

		}
		catch (Exception e) {
			// Fallback to whitespace-normalized comparison
			String normalizedExpected = normalizeWhitespace(expected);
			String normalizedActual = normalizeWhitespace(actual);
			if (normalizedExpected.equals(normalizedActual)) {
				return ComparisonResult.match();
			}
			return ComparisonResult
				.mismatch("Java parse failed, whitespace-normalized comparison also failed: " + e.getMessage());
		}
	}

	private void compareCompilationUnits(CompilationUnit expected, CompilationUnit actual, List<String> differences) {
		// Compare package declarations
		if (expected.getPackageDeclaration().isPresent() != actual.getPackageDeclaration().isPresent()) {
			differences.add("Package declaration differs: expected="
					+ expected.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("(none)") + ", actual="
					+ actual.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("(none)"));
		}
		else if (expected.getPackageDeclaration().isPresent()) {
			String expPkg = expected.getPackageDeclaration().get().getNameAsString();
			String actPkg = actual.getPackageDeclaration().get().getNameAsString();
			if (!expPkg.equals(actPkg)) {
				differences.add("Package differs: expected=" + expPkg + ", actual=" + actPkg);
			}
		}

		// Compare imports (order-independent)
		compareImports(expected, actual, differences);

		// Compare types (classes, interfaces, enums, annotations)
		compareTypes(expected.getTypes(), actual.getTypes(), differences);
	}

	private void compareImports(CompilationUnit expected, CompilationUnit actual, List<String> differences) {
		Set<String> expectedImports = expected.getImports()
			.stream()
			.map(this::importToString)
			.collect(Collectors.toSet());

		Set<String> actualImports = actual.getImports().stream().map(this::importToString).collect(Collectors.toSet());

		Set<String> missing = new TreeSet<>(expectedImports);
		missing.removeAll(actualImports);

		Set<String> extra = new TreeSet<>(actualImports);
		extra.removeAll(expectedImports);

		for (String imp : missing) {
			differences.add("Missing import: " + imp);
		}
		for (String imp : extra) {
			differences.add("Unexpected import: " + imp);
		}
	}

	private String importToString(ImportDeclaration imp) {
		StringBuilder sb = new StringBuilder();
		if (imp.isStatic()) {
			sb.append("static ");
		}
		sb.append(imp.getNameAsString());
		if (imp.isAsterisk()) {
			sb.append(".*");
		}
		return sb.toString();
	}

	private void compareTypes(List<TypeDeclaration<?>> expected, List<TypeDeclaration<?>> actual,
			List<String> differences) {
		// Group by name for order-independent comparison
		Map<String, TypeDeclaration<?>> expectedByName = expected.stream()
			.collect(Collectors.toMap(TypeDeclaration::getNameAsString, t -> t, (a, b) -> a));
		Map<String, TypeDeclaration<?>> actualByName = actual.stream()
			.collect(Collectors.toMap(TypeDeclaration::getNameAsString, t -> t, (a, b) -> a));

		for (String name : expectedByName.keySet()) {
			if (!actualByName.containsKey(name)) {
				differences.add("Missing type: " + name);
			}
			else {
				compareTypeDeclarations(expectedByName.get(name), actualByName.get(name), name, differences);
			}
		}

		for (String name : actualByName.keySet()) {
			if (!expectedByName.containsKey(name)) {
				differences.add("Unexpected type: " + name);
			}
		}
	}

	private void compareTypeDeclarations(TypeDeclaration<?> expected, TypeDeclaration<?> actual, String path,
			List<String> differences) {
		// Compare type kind (class, interface, enum, annotation)
		if (!expected.getClass().equals(actual.getClass())) {
			differences.add(path + ": type kind differs: expected=" + expected.getClass().getSimpleName() + ", actual="
					+ actual.getClass().getSimpleName());
			return;
		}

		// Compare modifiers
		if (!expected.getModifiers().equals(actual.getModifiers())) {
			differences.add(path + ": modifiers differ: expected=" + expected.getModifiers() + ", actual="
					+ actual.getModifiers());
		}

		// Compare annotations (order-independent)
		compareAnnotations(expected.getAnnotations(), actual.getAnnotations(), path, differences);

		// For ClassOrInterfaceDeclaration, compare extends/implements
		if (expected instanceof ClassOrInterfaceDeclaration expClass
				&& actual instanceof ClassOrInterfaceDeclaration actClass) {
			compareClassDeclarations(expClass, actClass, path, differences);
		}

		// Compare members (fields, methods, constructors, inner classes)
		compareMembers(expected, actual, path, differences);
	}

	private void compareClassDeclarations(ClassOrInterfaceDeclaration expected, ClassOrInterfaceDeclaration actual,
			String path, List<String> differences) {
		// Compare type parameters
		if (!expected.getTypeParameters().equals(actual.getTypeParameters())) {
			differences.add(path + ": type parameters differ");
		}

		// Compare extends
		Set<String> expectedExtends = expected.getExtendedTypes()
			.stream()
			.map(Type::asString)
			.collect(Collectors.toSet());
		Set<String> actualExtends = actual.getExtendedTypes().stream().map(Type::asString).collect(Collectors.toSet());
		if (!expectedExtends.equals(actualExtends)) {
			differences
				.add(path + ": extends clause differs: expected=" + expectedExtends + ", actual=" + actualExtends);
		}

		// Compare implements
		Set<String> expectedImplements = expected.getImplementedTypes()
			.stream()
			.map(Type::asString)
			.collect(Collectors.toSet());
		Set<String> actualImplements = actual.getImplementedTypes()
			.stream()
			.map(Type::asString)
			.collect(Collectors.toSet());
		if (!expectedImplements.equals(actualImplements)) {
			differences.add(path + ": implements clause differs: expected=" + expectedImplements + ", actual="
					+ actualImplements);
		}
	}

	private void compareMembers(TypeDeclaration<?> expected, TypeDeclaration<?> actual, String path,
			List<String> differences) {
		// Fields
		Map<String, FieldDeclaration> expectedFields = new HashMap<>();
		Map<String, FieldDeclaration> actualFields = new HashMap<>();

		for (FieldDeclaration f : expected.getFields()) {
			for (VariableDeclarator v : f.getVariables()) {
				expectedFields.put(v.getNameAsString(), f);
			}
		}
		for (FieldDeclaration f : actual.getFields()) {
			for (VariableDeclarator v : f.getVariables()) {
				actualFields.put(v.getNameAsString(), f);
			}
		}

		compareFieldMaps(expectedFields, actualFields, path, differences);

		// Methods
		compareMethodsBySignature(expected.getMethods(), actual.getMethods(), path, differences);

		// Constructors
		if (expected instanceof ClassOrInterfaceDeclaration expClass
				&& actual instanceof ClassOrInterfaceDeclaration actClass) {
			compareConstructors(expClass.getConstructors(), actClass.getConstructors(), path, differences);
		}

		// Nested types
		for (BodyDeclaration<?> member : expected.getMembers()) {
			if (member instanceof TypeDeclaration<?> nested) {
				TypeDeclaration<?> actualNested = findNestedType(actual, nested.getNameAsString());
				if (actualNested != null) {
					compareTypeDeclarations(nested, actualNested, path + "." + nested.getNameAsString(), differences);
				}
				else {
					differences.add(path + ": missing nested type " + nested.getNameAsString());
				}
			}
		}
	}

	private TypeDeclaration<?> findNestedType(TypeDeclaration<?> parent, String name) {
		for (BodyDeclaration<?> member : parent.getMembers()) {
			if (member instanceof TypeDeclaration<?> nested) {
				if (nested.getNameAsString().equals(name)) {
					return nested;
				}
			}
		}
		return null;
	}

	private void compareFieldMaps(Map<String, FieldDeclaration> expected, Map<String, FieldDeclaration> actual,
			String path, List<String> differences) {
		for (String name : expected.keySet()) {
			if (!actual.containsKey(name)) {
				differences.add(path + ": missing field " + name);
			}
			else {
				compareFields(expected.get(name), actual.get(name), name, path, differences);
			}
		}
		for (String name : actual.keySet()) {
			if (!expected.containsKey(name)) {
				differences.add(path + ": unexpected field " + name);
			}
		}
	}

	private void compareFields(FieldDeclaration expected, FieldDeclaration actual, String fieldName, String path,
			List<String> differences) {
		String fieldPath = path + "." + fieldName;

		if (!expected.getModifiers().equals(actual.getModifiers())) {
			differences.add(fieldPath + ": modifiers differ");
		}

		compareAnnotations(expected.getAnnotations(), actual.getAnnotations(), fieldPath, differences);

		// Compare type
		Optional<VariableDeclarator> expVar = expected.getVariables()
			.stream()
			.filter(v -> v.getNameAsString().equals(fieldName))
			.findFirst();
		Optional<VariableDeclarator> actVar = actual.getVariables()
			.stream()
			.filter(v -> v.getNameAsString().equals(fieldName))
			.findFirst();

		if (expVar.isPresent() && actVar.isPresent()) {
			if (!expVar.get().getType().asString().equals(actVar.get().getType().asString())) {
				differences.add(fieldPath + ": type differs: expected=" + expVar.get().getType().asString()
						+ ", actual=" + actVar.get().getType().asString());
			}
			// Compare initializer
			if (expVar.get().getInitializer().isPresent() != actVar.get().getInitializer().isPresent()) {
				differences.add(fieldPath + ": initializer presence differs");
			}
			else if (expVar.get().getInitializer().isPresent()) {
				String expInit = normalizeExpression(expVar.get().getInitializer().get());
				String actInit = normalizeExpression(actVar.get().getInitializer().get());
				if (!expInit.equals(actInit)) {
					differences.add(fieldPath + ": initializer differs");
				}
			}
		}
	}

	private void compareMethodsBySignature(List<MethodDeclaration> expected, List<MethodDeclaration> actual,
			String path, List<String> differences) {
		Map<String, MethodDeclaration> expectedBySignature = expected.stream()
			.collect(Collectors.toMap(this::methodSignature, m -> m, (a, b) -> a));
		Map<String, MethodDeclaration> actualBySignature = actual.stream()
			.collect(Collectors.toMap(this::methodSignature, m -> m, (a, b) -> a));

		for (String sig : expectedBySignature.keySet()) {
			if (!actualBySignature.containsKey(sig)) {
				differences.add(path + ": missing method " + sig);
			}
			else {
				compareMethods(expectedBySignature.get(sig), actualBySignature.get(sig), path + "." + sig, differences);
			}
		}

		for (String sig : actualBySignature.keySet()) {
			if (!expectedBySignature.containsKey(sig)) {
				differences.add(path + ": unexpected method " + sig);
			}
		}
	}

	private String methodSignature(MethodDeclaration method) {
		return method.getNameAsString() + "("
				+ method.getParameters().stream().map(p -> p.getType().asString()).collect(Collectors.joining(","))
				+ ")";
	}

	private void compareMethods(MethodDeclaration expected, MethodDeclaration actual, String path,
			List<String> differences) {
		if (!expected.getModifiers().equals(actual.getModifiers())) {
			differences.add(path + ": modifiers differ");
		}

		if (!expected.getType().asString().equals(actual.getType().asString())) {
			differences.add(path + ": return type differs: expected=" + expected.getType().asString() + ", actual="
					+ actual.getType().asString());
		}

		compareAnnotations(expected.getAnnotations(), actual.getAnnotations(), path, differences);

		// Compare body
		if (expected.getBody().isPresent() != actual.getBody().isPresent()) {
			differences.add(path + ": body presence differs");
		}
		else if (expected.getBody().isPresent()) {
			compareStatementBlocks(expected.getBody().get(), actual.getBody().get(), path, differences);
		}
	}

	private void compareConstructors(List<ConstructorDeclaration> expected, List<ConstructorDeclaration> actual,
			String path, List<String> differences) {
		Map<String, ConstructorDeclaration> expectedBySig = expected.stream()
			.collect(Collectors.toMap(this::constructorSignature, c -> c, (a, b) -> a));
		Map<String, ConstructorDeclaration> actualBySig = actual.stream()
			.collect(Collectors.toMap(this::constructorSignature, c -> c, (a, b) -> a));

		for (String sig : expectedBySig.keySet()) {
			if (!actualBySig.containsKey(sig)) {
				differences.add(path + ": missing constructor " + sig);
			}
		}

		for (String sig : actualBySig.keySet()) {
			if (!expectedBySig.containsKey(sig)) {
				differences.add(path + ": unexpected constructor " + sig);
			}
		}
	}

	private String constructorSignature(ConstructorDeclaration ctor) {
		return "(" + ctor.getParameters().stream().map(p -> p.getType().asString()).collect(Collectors.joining(","))
				+ ")";
	}

	private void compareAnnotations(List<AnnotationExpr> expected, List<AnnotationExpr> actual, String path,
			List<String> differences) {
		Set<String> expectedSet = expected.stream().map(this::normalizeAnnotation).collect(Collectors.toSet());
		Set<String> actualSet = actual.stream().map(this::normalizeAnnotation).collect(Collectors.toSet());

		Set<String> missing = new TreeSet<>(expectedSet);
		missing.removeAll(actualSet);

		Set<String> extra = new TreeSet<>(actualSet);
		extra.removeAll(expectedSet);

		for (String ann : missing) {
			differences.add(path + ": missing annotation " + ann);
		}
		for (String ann : extra) {
			differences.add(path + ": unexpected annotation " + ann);
		}
	}

	private String normalizeAnnotation(AnnotationExpr annotation) {
		if (annotation instanceof MarkerAnnotationExpr marker) {
			return "@" + marker.getNameAsString();
		}
		else if (annotation instanceof SingleMemberAnnotationExpr single) {
			return "@" + single.getNameAsString() + "(" + normalizeExpression(single.getMemberValue()) + ")";
		}
		else if (annotation instanceof NormalAnnotationExpr normal) {
			String pairs = normal.getPairs()
				.stream()
				.sorted(Comparator.comparing(MemberValuePair::getNameAsString))
				.map(p -> p.getNameAsString() + "=" + normalizeExpression(p.getValue()))
				.collect(Collectors.joining(", "));
			return "@" + normal.getNameAsString() + "(" + pairs + ")";
		}
		return annotation.toString();
	}

	private void compareStatementBlocks(BlockStmt expected, BlockStmt actual, String path, List<String> differences) {
		List<Statement> expStmts = expected.getStatements();
		List<Statement> actStmts = actual.getStatements();

		if (expStmts.size() != actStmts.size()) {
			differences
				.add(path + ": statement count differs: expected=" + expStmts.size() + ", actual=" + actStmts.size());
			return;
		}

		for (int i = 0; i < expStmts.size(); i++) {
			String expNorm = normalizeStatement(expStmts.get(i));
			String actNorm = normalizeStatement(actStmts.get(i));
			if (!expNorm.equals(actNorm)) {
				differences.add(path + ": statement " + (i + 1) + " differs");
			}
		}
	}

	private String normalizeStatement(Statement stmt) {
		// Remove comments for comparison
		Statement copy = stmt.clone();
		copy.getAllContainedComments().forEach(Comment::remove);
		copy.getComment().ifPresent(Comment::remove);
		return normalizeWhitespace(copy.toString());
	}

	private String normalizeExpression(Expression expr) {
		Expression copy = expr.clone();
		copy.getAllContainedComments().forEach(Comment::remove);
		copy.getComment().ifPresent(Comment::remove);
		return normalizeWhitespace(copy.toString());
	}

	private String normalizeWhitespace(String s) {
		return s.replaceAll("\\s+", " ").trim();
	}

}
