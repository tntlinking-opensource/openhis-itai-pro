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

package com.openhis.health.domain.share.events.mrms.caserecord;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案已归档事件（DM-MRMS-001 §5；首页冻结、EMR 文书锁定等下游可订阅）。
 */
@Getter
@Setter
public class CaseRecordArchivedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 归档时间。 */
    private LocalDateTime archivedAt;

    /** 无参构造器（反序列化用）。 */
    public CaseRecordArchivedEvent() {
        super();
    }

    /**
     * 构造病案归档事件。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户
     * @param operatorId 操作人
     * @param encounterId 就诊
     * @param patientId 患者
     * @param archivedAt 归档时间
     */
    public CaseRecordArchivedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            Long patientId,
            LocalDateTime archivedAt) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.archivedAt = archivedAt;
    }
}
