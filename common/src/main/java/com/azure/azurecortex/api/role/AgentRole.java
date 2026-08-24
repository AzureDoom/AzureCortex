package com.azure.azurecortex.api.role;

import com.azure.azurecortex.role.RoleAssignment;
import com.azure.azurecortex.role.RoleSelector;

/**
 * Marker interface a mod's own soft-role enum should implement so it can be used with
 * {@link RoleSelector}/{@link RoleAssignment}.
 * <p>
 * A "role" here is a soft, advisory intent label a planner assigns an agent for a while (Ovomorphosis's
 * {@code XenoRole} — hunter, guard, scout, and so on — is the motivating example) rather than a hard capability. Roles
 * influence goal scoring but don't gate what an agent is allowed to do. AzureCortex ships no concrete roles of its own;
 * mods declare their own enum implementing this interface:
 *
 * <pre>{@code
 * public enum MyRole implements AgentRole {
 *     HUNTER,
 *     GUARD,
 *     SCOUT,
 * }
 * }</pre>
 */
public interface AgentRole {}
