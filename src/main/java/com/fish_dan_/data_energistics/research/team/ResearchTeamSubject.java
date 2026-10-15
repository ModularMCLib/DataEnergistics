package com.fish_dan_.data_energistics.research.team;

import java.util.UUID;

/**
 * Read-only external team identity for a current, verified membership.
 * IDs remain owned by the source integration; callers must reevaluate membership on the server thread.
 */
public record ResearchTeamSubject(UUID id, String name, String source) {}
