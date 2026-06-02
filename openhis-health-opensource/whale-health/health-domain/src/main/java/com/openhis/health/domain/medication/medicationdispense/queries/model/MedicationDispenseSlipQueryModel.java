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

package com.openhis.health.domain.medication.medicationdispense.queries.model;

import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 发药单列表/分页查询行（替代 {@code MedicationDispenseSlipReadModel} 读侧投影）。
 */
@Getter
@Setter
public class MedicationDispenseSlipQueryModel {

    private Long dispenseId;
    private String busNo;
    private Long medReqId;
    private DispenseStatus statusEnum;
    private String notPerformedReasonEnum;
    private LocalDateTime statusChangedTime;
    private MedicationDispenseType dispenseEnum;
    private Long medicationId;
    private Long patientId;
    private Long encounterId;
    private Long locationId;
    private BigDecimal quantity;
    private String unitCode;
    private BigDecimal dispenseQuantity;
    private String lotNumber;
    private String traceNo;
    private Long preparerId;
    private LocalDateTime prepareTime;
    private Long dispenserId;
    private LocalDateTime dispenseTime;
    private String summaryNo;
    /** 处方号（由药嘱 {@code prescription_no} 回填；查询未 join 时可为 null）。 */
    private String prescriptionNo;
    /** 开单医生（读模型曾投影；主表无列时可为 null，由药嘱回填）。 */
    private Long medRequesterId;
}
