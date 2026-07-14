/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
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

    /** 机构标识。 */
    @Column(name = "organization_id", nullable = false)
    private Long organizationId;

    /** 候诊区 Location 主键。 */
    @Column(name = "location_id")
    private Long locationId;

    /** 大屏标题。 */
    @Column(name = "display_title")
    private String displayTitle;

    /** 数据刷新间隔（秒）。 */
    @Column(name = "refresh_seconds", nullable = false)
    private Integer refreshSeconds = 5;

    /** 是否启用语音叫号。 */
    @Column(name = "voice_enabled_flag", nullable = false)
    private Boolean voiceEnabledFlag = Boolean.FALSE;
}
