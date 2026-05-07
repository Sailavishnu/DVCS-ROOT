package com.dvcs.client.workspacepage.model;

import org.bson.types.ObjectId;

public record UserModel(ObjectId userId, String username) {
}
