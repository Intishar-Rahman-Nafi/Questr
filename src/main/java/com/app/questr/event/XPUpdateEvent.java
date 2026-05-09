package com.app.questr.event;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XPUpdateEvent {
    private UUID userId;
    private int xpGained;
    private int newTotalXp;
    private int newLevel;
    private boolean leveledUp;
    private Instant timestamp;
}
