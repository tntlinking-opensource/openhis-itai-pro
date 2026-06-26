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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * IF-IP-ARG-08：单条滚费落账行上下文（Request + Charge 成对）。
 */
@Getter
@Setter
public class AutoRollLineContext {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 个人账户主键。 */
    private Long accountId;

    /** 科室/机构主键。 */
    private Long organizationId;

    /** 诊疗活动定义主键。 */
    private Long activityId;

    /** 收费项定价主键。 */
    private Long chargeDefinitionId;

    /** 落账数量。 */
    private BigDecimal quantity;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 诊疗类目（51 床位 / 26 护理）。 */
    private String categoryEnum;

    /** 开立人主键。 */
    private Long requesterId;

    /** 滚费业务日。 */
    private LocalDate billDate;

    /** 费用发生时刻。 */
    private LocalDateTime occurrenceTime;

    /** 单价（入账权威）。 */
    private BigDecimal unitPrice;

    /** 行总价。 */
    private BigDecimal totalPrice;
}
