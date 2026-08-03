package com.quorumtrading.adapter.rest.dto.request;

import java.util.List;

public record UpdateNotificationPreferenceRequest(List<String> channels) {
}
