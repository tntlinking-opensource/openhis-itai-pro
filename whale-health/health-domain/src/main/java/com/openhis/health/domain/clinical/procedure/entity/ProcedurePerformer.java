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

package com.openhis.health.domain.clinical.procedure.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 执行人子实体。
 */
@Getter
@Setter
@Entity
@Table(name = "cln_procedure_performer")
public class ProcedurePerformer extends BaseAuditedEntity {
    @Column(name = "procedure_id", nullable = false)
    /** 操作主键。 */
    private Long procedureId;
    @Column(name = "function_code")
    /** 职能编码。 */
    private String functionCode;
    @Column(name = "start_time", nullable = false)
    /** 开始时间。 */
    private LocalDateTime startTime;
    @Column(name = "end_time")
    /** 结束时间。 */
    private LocalDateTime endTime;
    @Column(name = "practitioner_id", nullable = false)
    /** 执业者主键。 */
    private Long practitionerId;
    @Column(name = "org_from_id")
    /** 机构来源主键。 */
    private String orgFromId;
}
