package com.queryai.workspace;

/**
 * Workspace roles, ordered from most to least privileged.
 * <ul>
 *     <li>OWNER – everything, including deleting the workspace</li>
 *     <li>ADMIN – manage members, data sources, glossary and permissions</li>
 *     <li>ANALYST – ask questions, write and run SQL, save queries and build dashboards</li>
 *     <li>VIEWER – read shared queries and dashboards and run them</li>
 * </ul>
 */
public enum WorkspaceRole {
    OWNER(4), ADMIN(3), ANALYST(2), VIEWER(1);

    private final int rank;

    WorkspaceRole(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(WorkspaceRole other) {
        return this.rank >= other.rank;
    }
}
