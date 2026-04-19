package com.dvcs.client.core.dao;

import com.dvcs.client.core.model.Notification;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.bson.Document;
import org.bson.types.ObjectId;

public final class NotificationDao {

    private final MongoCollection<Document> notifications;

    public NotificationDao(MongoDatabase database) {
        Objects.requireNonNull(database, "database");
        this.notifications = database.getCollection("notifications");
    }

    public void insert(Notification notification) {
        Objects.requireNonNull(notification, "notification");
        notifications.insertOne(notification.toDocument());
    }

    public List<Notification> findUnreadForUser(ObjectId userId) {
        Objects.requireNonNull(userId, "userId");
        List<Notification> result = new ArrayList<>();
        for (Document doc : notifications.find(Filters.and(
                Filters.eq("userId", userId),
                Filters.eq("isRead", false)
        )).sort(Sorts.descending("createdAt"))) {
            result.add(Notification.fromDocument(doc));
        }
        return result;
    }

    public boolean markAsRead(ObjectId notificationId) {
        return notifications.updateOne(
                Filters.eq("_id", notificationId),
                Updates.set("isRead", true)
        ).getModifiedCount() > 0;
    }
}
