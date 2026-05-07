package com.dvcs.client.dashboard.collaborators;

import java.time.Instant;
import java.util.List;

public record CollaboratorItem(
        String username,
        String initials,
        String workspaceName,
        String status,
        Instant since,
        List<String> recentCommitMessages,
        List<Instant> recentCommitTimes) {}
