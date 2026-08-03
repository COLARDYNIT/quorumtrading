package com.quorumtrading.model.valueobject;

/**
 * A follow-up suggestion shown as a clickable chip after an assistant response.
 *
 * @param label   Short display text for the chip
 * @param message Full message sent when clicked
 */
public record FollowUpSuggestion(String label, String message) {
}
