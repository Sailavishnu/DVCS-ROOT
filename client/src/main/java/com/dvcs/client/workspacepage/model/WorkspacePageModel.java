package com.dvcs.client.workspacepage.model;

import java.util.List;

import org.bson.types.ObjectId;

public record WorkspacePageModel(
                ObjectId workspaceId,
                String workspaceName,
                String workspaceRootPath,
                List<FolderModel> folders,
                List<UserModel> collaborators,
                int totalCommits,
                String readmeContent) {
}
