/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.location.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 库房库存期初关闭态（表 {@code adm_location_inventory_opening}；GAP-OPN-002）。
 *
 * <p>与 {@link Location} 业务 1:1（{@code location_id} UK）；仅三房形态允许写入。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_location_inventory_opening")
public class LocationInventoryOpening extends BaseAuditedEntity {

    /** 位置主键（= {@code adm_location.id}）。 */
    @Column(name = "location_id", nullable = false, unique = true)
    private Long locationId;

    /** 是否已关闭期初。 */
    @Column(name = "opening_closed", nullable = false)
    private Boolean openingClosed;

    /** 关闭时间。 */
    @Column(name = "closed_time")
    private LocalDateTime closedTime;

    /** 关闭操作人。 */
    @Column(name = "closed_by")
    private Long closedBy;

    /** 乐观锁。 */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 标记关闭（幂等：已关闭则保持）。
     *
     * @param closedBy 关闭人
     * @param closedTime 关闭时刻
     */
    public void markClosed(Long closedBy, LocalDateTime closedTime) {
        if (Boolean.TRUE.equals(this.openingClosed)) {
            return;
        }
        this.openingClosed = Boolean.TRUE;
        this.closedBy = closedBy;
        this.closedTime = closedTime;
    }
}
