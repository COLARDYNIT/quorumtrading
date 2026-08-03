package com.quorumtrading.adapter.persistence.mapper;

import com.quorumtrading.adapter.persistence.entity.NotificationPreferenceJpaEntity;
import com.quorumtrading.model.entity.NotificationPreference;
import com.quorumtrading.model.enums.NotificationChannel;
import com.quorumtrading.model.enums.NotificationType;
import com.quorumtrading.model.valueobject.UserId;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class NotificationPreferenceMapper {

    public NotificationPreferenceJpaEntity toJpaEntity(NotificationPreference pref) {
        NotificationPreferenceJpaEntity e = new NotificationPreferenceJpaEntity();
        e.setUserId(pref.getUserId().value());
        e.setType(pref.getType().name());
        e.setChannels(channelsToCsv(pref.getChannels()));
        return e;
    }

    public NotificationPreference toDomain(NotificationPreferenceJpaEntity e) {
        return NotificationPreference.reconstitute(
                new UserId(e.getUserId()),
                NotificationType.valueOf(e.getType()),
                csvToChannels(e.getChannels())
        );
    }

    private String channelsToCsv(Set<NotificationChannel> channels) {
        if (channels == null || channels.isEmpty()) {
            return "";
        }
        return channels.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private Set<NotificationChannel> csvToChannels(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(NotificationChannel::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationChannel.class)));
    }
}
