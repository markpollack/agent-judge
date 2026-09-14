/*
 * Copyright (c) 2024-2026 Mark Pollack
 * See LICENSE in the repository root for project-specific Business Source License terms.
 */

package io.github.markpollack.judge.description;

import java.util.Map;

import io.github.markpollack.judge.Judge;
import io.github.markpollack.judge.JudgeWithMetadata;

/**
 * A judge that declares the configuration its verdicts depend on.
 *
 * <p>
 * Opt-in, parallel to {@link JudgeWithMetadata}: implementing this interface <em>is</em> the
 * declaration. A judge that does not implement it is described with
 * {@code "configuration": {"declared": false}}, which means "this judge said nothing", not
 * "this judge has no configuration". A judge that implements it and returns an empty map is
 * described with {@code {"declared": true, "values": {}}}: it has stated that nothing it holds
 * affects its verdicts.
 * </p>
 *
 * <p>
 * Declare what the judge knows at construction and what would change a verdict if it
 * changed: a rubric version, a prompt digest, a pass mark, the model identifier the judge
 * pins. Do not guess what a collaborator will do. A judge that calls a model client which
 * chooses its model at call time does not know the model, and should leave it out rather
 * than name a default.
 * </p>
 *
 * <p>
 * Values must be portable: strings, booleans, interoperable integers, finite numbers, lists,
 * and string-keyed maps, recursively, with no {@code null} at any depth. Describing a judge
 * whose configuration holds anything else fails with a message naming the offending path.
 * Key order does not matter; the description orders keys.
 * </p>
 *
 * @author Mark Pollack
 * @since 0.17.0
 * @see io.github.markpollack.judge.Judges#describe(Judge)
 */
public interface ConfiguredJudge extends Judge {

	/**
	 * The configuration this judge's verdicts depend on, as portable values.
	 * <p>
	 * An empty map is a declaration that nothing configurable affects the verdict. Never
	 * return {@code null}; a judge with nothing to declare should not implement this
	 * interface.
	 * </p>
	 * @return the declared configuration; never null
	 */
	Map<String, Object> configuration();

}
