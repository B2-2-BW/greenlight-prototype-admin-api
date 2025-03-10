package com.winten.greenlight.prototype.admin.domain.event;

import com.winten.greenlight.prototype.admin.db.repository.redis.EventEntity;
import com.winten.greenlight.prototype.admin.support.dto.AuditDto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Event extends AuditDto {
    private Long eventSeq;
    private String eventName;
    private String eventDescription;
    private String eventType;
    private String eventUrl;
    private Integer queueBackpressure;
    private LocalDateTime eventStartTime;
    private LocalDateTime eventEndTime;

    public Event(final EventEntity entity) {
        this.eventName = entity.getEventName();
        this.eventDescription = entity.getEventDescription();
        this.eventType = entity.getEventType();
        this.eventUrl = entity.getEventUrl();
        this.queueBackpressure = entity.getQueueBackpressure();
        this.eventStartTime = entity.getEventStartTime();
        this.eventEndTime = entity.getEventEndTime();
    }

    public EventEntity toEntity() {
        final EventEntity entity = new EventEntity();
        entity.setEventName(eventName);
        entity.setEventDescription(eventDescription);
        entity.setEventType(eventType);
        entity.setEventUrl(eventUrl);
        entity.setQueueBackpressure(queueBackpressure);
        entity.setEventStartTime(eventStartTime);
        entity.setEventEndTime(eventEndTime);
        return entity;
    }
}