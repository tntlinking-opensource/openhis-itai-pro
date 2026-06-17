package com.openhis.whale.booking.domain.queue.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 叫号大屏配置（bkg_queue_display_config）。
 */
@Getter
@Setter
@Entity
@Table(name = "bkg_queue_display_config")
public class QueueDisplayConfig extends BaseAuditedEntity {

    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    @Column(name = "location_id")
    private Long locationId;

    @Column(name = "display_title")
    private String displayTitle;

    @Column(name = "refresh_seconds", nullable = false)
    private Integer refreshSeconds = 5;

    @Column(name = "voice_enabled_flag", nullable = false)
    private Boolean voiceEnabledFlag = Boolean.FALSE;
}
