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

package com.openhis.health.domain.medication.medicationdispense.command;

import lombok.Getter;
import lombok.Setter;
import com.openhis.health.domain.share.enums.MedicationDispenseType;

import java.math.BigDecimal;

/**
 * 由临时医嘱生成发放单命令。
 */
@Getter
@Setter
public class GenerateDispenseFromTemporaryOrderCommand {
    /** 药品申请标识。 */
    private Long medReqId;
    /** 药品定义标识。 */
    private Long medicationId;
    /** 就诊标识。 */
    private Long encounterId;
    /** 患者标识。 */
    private Long patientId;
    /** 发药位置标识。 */
    private Long locationId;
    /** 应发数量。 */
    private BigDecimal quantity;
    /** 发放单位编码。 */
    private String unitCode;
    /** 发放类型编码。 */
    private MedicationDispenseType dispenseEnum;
}
