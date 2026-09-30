/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.judgment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link Check}.
 *
 * @author Mark Pollack
 * @since 0.1.0
 */
class CheckTest {

	@Test
	void shouldCreatePassCheck() {
		Check check = Check.pass("Test passed");

		assertThat(check.id()).isEqualTo("Test passed");
		assertThat(check.judgment().pass()).isTrue();
		assertThat(check.judgment().reasoning()).isEmpty();
	}

	@Test
	void shouldCreatePassCheckWithMessage() {
		Check check = Check.pass("Test passed", "All assertions succeeded");

		assertThat(check.id()).isEqualTo("Test passed");
		assertThat(check.judgment().pass()).isTrue();
		assertThat(check.judgment().reasoning()).isEqualTo("All assertions succeeded");
	}

	@Test
	void shouldCreateFailCheck() {
		Check check = Check.fail("Test failed", "Expected 5 but was 3");

		assertThat(check.id()).isEqualTo("Test failed");
		assertThat(check.judgment().pass()).isFalse();
		assertThat(check.judgment().reasoning()).isEqualTo("Expected 5 but was 3");
	}

	@Test
	void shouldCreateCheckWithConstructor() {
		Check check = new Check("Custom check", Judgment.pass("Custom message"));

		assertThat(check.id()).isEqualTo("Custom check");
		assertThat(check.judgment().pass()).isTrue();
		assertThat(check.judgment().reasoning()).isEqualTo("Custom message");
	}

	// ==================== Record Tests ====================

	@Test
	void recordShouldProvideEquality() {
		Check c1 = Check.pass("Test", "Message");
		Check c2 = Check.pass("Test", "Message");

		assertThat(c1).isEqualTo(c2);
		assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
	}

	@Test
	void recordShouldProvideToString() {
		Check check = Check.fail("Build", "Compilation failed");

		String toString = check.toString();

		assertThat(toString).contains("Check");
		assertThat(toString).contains("Build");
		assertThat(toString).contains("Compilation failed");
	}

	@Test
	void recordShouldDistinguishPassFromFail() {
		Check pass = Check.pass("Test");
		Check fail = Check.fail("Test", "Failed");

		assertThat(pass).isNotEqualTo(fail);
	}

	// ==================== Use Case Tests ====================

	@Test
	void shouldSupportMultipleChecksInJudgment() {
		Check compilationCheck = Check.pass("Compilation");
		Check testCheck = Check.pass("Tests ran");
		Check coverageCheck = Check.fail("Coverage", "Only 70%, expected 80%");

		assertThat(compilationCheck.judgment().pass()).isTrue();
		assertThat(testCheck.judgment().pass()).isTrue();
		assertThat(coverageCheck.judgment().pass()).isFalse();
		assertThat(coverageCheck.judgment().reasoning()).contains("70%");
	}

	@Test
	void shouldHandleEmptyMessage() {
		Check check = Check.pass("Test");

		assertThat(check.judgment().reasoning()).isEmpty();
	}

	@Test
	void shouldRejectInvalidRecordComponents() {
		assertThatThrownBy(() -> new Check(null, Judgment.pass("message"))).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new Check("  ", Judgment.pass("message")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Check("name", null)).isInstanceOf(NullPointerException.class);
	}

}
