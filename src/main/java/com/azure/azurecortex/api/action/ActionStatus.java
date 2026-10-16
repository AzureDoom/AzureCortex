package com.azure.azurecortex.api.action;

import com.azure.azurecortex.runtime.CortexRuntime;

/**
 * Describes <em>why</em> an {@link Action} was stopped — passed to {@link Action#stop}.
 * <p>
 * This is deliberately narrow: per-tick results flow through {@link ActionOutcome}, returned by {@link Action#tick}.
 * {@link ActionOutcome.Running} and {@link ActionOutcome.Blocked} never reach {@code stop()} at all (the action keeps
 * executing); only {@link ActionOutcome.Success} and {@link ActionOutcome.Failed} terminate an action and translate to
 * {@link #SUCCESS} / {@link #FAILURE} here. {@link #INTERRUPTED} is reserved for the one case an action itself never
 * decides: {@link CortexRuntime} preempting it in favor of something else.
 */
public enum ActionStatus {

    /** The action completed its goal successfully. */
    SUCCESS,

    /** The action was unable to complete its goal and has given up (see {@link ActionOutcome.Failed}). */
    FAILURE,

    /** The action was forcibly stopped by the runtime before it could finish, in favor of a different action. */
    INTERRUPTED
}
