/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.encounter;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 转床确认事件。
 */
@Getter
@Setter
public class BedTransferredEvent extends DomainEvent {

    /** from库位主键（BedTransferredEvent）。 */
    private Long fromLocationId;
    /** to库位主键（BedTransferredEvent）。 */
    private Long toLocationId;
    /** effectiveAt（BedTransferredEvent）。 */
    private LocalDateTime effectiveAt;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public BedTransferredEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long fromLocationId, Long toLocationId, LocalDateTime effectiveAt逻辑。
     * @return 返回结果
     */
    public BedTransferredEvent(Long encounterId, Long fromLocationId, Long toLocationId, LocalDateTime effectiveAt) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.fromLocationId = fromLocationId;
        this.toLocationId = toLocationId;
        this.effectiveAt = effectiveAt;
    }
}
