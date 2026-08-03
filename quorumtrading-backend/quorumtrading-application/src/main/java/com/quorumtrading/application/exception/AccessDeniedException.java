package com.quorumtrading.application.exception;

import com.quorumtrading.model.valueobject.UserId;

/**
 * Exception thrown when a user attempts to access a resource they do not own.
 * This exception results in a 403 Forbidden response.
 */
public class AccessDeniedException extends RuntimeException {

    private final UserId userId;
    private final String resourceType;
    private final String resourceId;

    public AccessDeniedException(UserId userId, String resourceType, String resourceId) {
        super(String.format("User %s does not have access to %s %s",
            userId != null ? userId.value() : "anonymous", resourceType, resourceId));
        this.userId = userId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public UserId getUserId() {
        return userId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
