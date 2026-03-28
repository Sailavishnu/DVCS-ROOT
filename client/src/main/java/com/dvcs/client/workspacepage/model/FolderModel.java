package com.dvcs.client.workspacepage.model;

import java.util.List;

import org.bson.types.ObjectId;

public record FolderModel(ObjectId folderId, String folderName, List<FileItemModel> files) {
}
