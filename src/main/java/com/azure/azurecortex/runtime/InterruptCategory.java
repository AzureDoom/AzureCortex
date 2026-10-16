package com.azure.azurecortex.runtime;

import com.azure.azurecortex.api.action.Action;
import com.azure.azurecortex.api.behavior.BehaviorNode;

/**
 * Describes how an {@link Action} participates in preemption, both as the currently-running action (how resistant it is
 * to being interrupted) and as a candidate returned from the {@link BehaviorNode} tree (what authority it has to
 * interrupt whatever is currently running).
 * <h3>Why not a boolean</h3> A plain {@code isInterruptible() == false} would otherwise be treated as an absolute lock:
 * {@link CortexRuntime} would skip evaluating the behavior tree entirely while such an action was running, which would
 * mean nothing — not even "on fire" or "explosion incoming" — could ever preempt it. {@link InterruptCategory} splits
 * "resistant to normal priority-based preemption" from "resistant to everything, even emergencies" so genuinely
 * critical situations can still break through.
 * <h3>Resolution rules ({@link InterruptController})</h3>
 * <ul>
 * <li>A currently-running {@link #LOCKED} action can only be preempted by an {@link #EMERGENCY} candidate.</li>
 * <li>A currently-running {@link #NORMAL} action can be preempted by an {@link #EMERGENCY} candidate, or by a
 * {@link #NORMAL} candidate with strictly higher priority.</li>
 * <li>A currently-running {@link #EMERGENCY} action can only be preempted by a different {@link #EMERGENCY} candidate
 * with strictly higher priority — a normal candidate, however high its priority, never displaces an active
 * emergency.</li>
 * </ul>
 */
public enum InterruptCategory {

    /** Behaves like the legacy {@code isInterruptible() == false}: immune to ordinary priority-based preemption. */
    LOCKED,

    /** The default: ordinary priority-based preemption applies (legacy {@code isInterruptible() == true}). */
    NORMAL,

    /**
     * Reserved for genuinely critical situations (on fire, imminent explosion, critical health, ...). As the running
     * action it resists everything except another, higher-priority emergency. As a candidate it can preempt a
     * {@link #LOCKED} or {@link #NORMAL} action regardless of that action's priority.
     */
    EMERGENCY
}
