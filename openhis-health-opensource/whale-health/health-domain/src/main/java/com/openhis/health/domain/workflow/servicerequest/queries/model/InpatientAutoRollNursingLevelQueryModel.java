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

package com.openhis.health.domain.workflow.servicerequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 住院滚费护理级别读模型（AS-IP-NUR-013 BR-IP-ARG-009；DM-WKF-004 §6.2.25）。
 *
 * <p>表达 billDate 当日最后一条有效校对护理级别医嘱；定价解析由应用层经 ActivityDefinition 完成。</p>
 */
@Getter
@Setter
public class InpatientAutoRollNursingLevelQueryModel {

    /** 源护理级别医嘱主键。 */
    private Long serviceRequestId;

    /** 诊疗活动定义主键。 */
    private Long activityId;

    /** 医嘱数量（通常 1）。 */
    private BigDecimal quantity;

    /** 数量单位持久化码。 */
    private String unitCode;

    /** 开立人主键。 */
    private Long requesterId;

    /** 护士校对时刻（billDate 口径下取最大值）。 */
    private LocalDateTime checkTime;

    /** 医嘱状态持久化码（VERIFIED / STOPPED）。 */
    private String statusEnum;
}
