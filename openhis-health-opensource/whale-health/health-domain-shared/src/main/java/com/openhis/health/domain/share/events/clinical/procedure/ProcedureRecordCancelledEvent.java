/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.clinical.procedure;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 执行记录已取消事件。
 */
@Getter
@Setter
/**
 * ProcedureRecordCancelledEvent。
 */
public class ProcedureRecordCancelledEvent extends DomainEvent {
    /**
     * statusEnum。
     */
    private String statusEnum;
    /**
     * statusReasonText。
     */
    private String statusReasonText;
    /**
     * refundId。
     */
    private Long refundId;

    public ProcedureRecordCancelledEvent(Long procedureId, String statusEnum, String statusReasonText, Long refundId) {
        super(procedureId, AggregateTypeConstants.PROCEDURE);
        this.statusEnum = statusEnum;
        this.statusReasonText = statusReasonText;
        this.refundId = refundId;
    }
}
