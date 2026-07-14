/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 清床完成事件。
 */
@Getter
@Setter
public class TerminalCleaningCompletedEvent extends DomainEvent {

    /** bed主键（TerminalCleaningCompletedEvent）。 */
    private Long bedId;
    /** cleanedAt（TerminalCleaningCompletedEvent）。 */
    private LocalDateTime cleanedAt;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public TerminalCleaningCompletedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long bedId, LocalDateTime cleanedAt逻辑。
     * @return 返回结果
     */
    public TerminalCleaningCompletedEvent(Long encounterId, Long bedId, LocalDateTime cleanedAt) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.bedId = bedId;
        this.cleanedAt = cleanedAt;
    }
}
