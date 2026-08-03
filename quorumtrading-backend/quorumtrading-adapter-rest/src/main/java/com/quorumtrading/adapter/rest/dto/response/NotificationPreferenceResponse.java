package com.quorumtrading.adapter.rest.dto.response;

import com.quorumtrading.model.entity.NotificationPreference;

import java.util.List;

public record NotificationPreferenceResponse(
        String type,
        List<String> channels) {

    public static NotificationPreferenceResponse fromDomain(NotificationPreference p) {
        List<String> channels = p.getChannels().stream()
                .map(Enum::name)
                .sorted()
                .toList();
        return new NotificationPreferenceResponse(p.getType().name(), channels);
    }
}
