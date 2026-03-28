package com.dvcs.client.dashboard.data;

import java.util.List;

public record WorkspaceDetails(
        String workspaceName,
        List<String> folders,
        List<String> files,
        List<String> collaborators) {
}
