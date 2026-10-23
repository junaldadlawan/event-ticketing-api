package com.junaldadlawan.event_ticketing_api.platformsetting.entity;

import com.junaldadlawan.event_ticketing_api.common.entity.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The auto-delete rule for unpublished drafts (BR-EVENT-007). Single row, seeded with 60 by V70. */
@Entity
@Table(name = "draft_event_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftEventSettings extends Auditable {

    public static final short ROW_ID = 1;

    @Id
    @Column(name = "id")
    @Builder.Default
    private Short id = ROW_ID;

    /** A draft whose start is less than this many minutes away (or already past) is deleted. 0 = the rule is off. */
    @Column(name = "auto_delete_minutes_before_start", nullable = false)
    @Builder.Default
    private int autoDeleteMinutesBeforeStart = 60;
}
