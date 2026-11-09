/**
 * Public API for Cultivar and its addons.
 *
 * <p>Rules for this package tree:
 * <ul>
 *   <li>No client-only imports (enforced by the {@code checkSidedness} Gradle task).</li>
 *   <li>Data records only gain fields as {@code optionalFieldOf} with defaults, so existing JSON keeps loading.</li>
 *   <li>Everything here is experimental until 1.0; breaking changes are called out in the changelog.</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.kragnoth.cultivar.api;

import org.jetbrains.annotations.ApiStatus;
