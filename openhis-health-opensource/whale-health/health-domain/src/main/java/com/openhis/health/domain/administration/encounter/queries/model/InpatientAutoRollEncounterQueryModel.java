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

package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院滚费任务就诊上下文读模型（AS-IP-NUR-013 IF-IP-ARG-08；DM-ADM-002 §6.2.21）。
 *
 * <p>表达「已分床且未清床」IMP 就诊的滚费落账所需最小字段；账户缺省时 {@code accountId} 为 {@code null}，由应用层决定是否跳过。</p>
 */
@Getter
@Setter
public class InpatientAutoRollEncounterQueryModel {

    /** 就诊主键。 */
    private Long encounterId;

    /** 患者主键。 */
    private Long patientId;

    /** 当前科室主键（开立科室 / requestingOrg 缺省锚点）。 */
    private Long organizationId;

    /** 默认个人现金账户主键；未开立时不含值。 */
    private Long accountId;

    /** 入院时刻（BR-IP-ARG-007 入院日跨午夜判定）。 */
    private LocalDateTime startTime;

    /** 就诊状态持久化码（IN_PROGRESS / ON_HOLD / COMPLETED）。 */
    private String statusEnum;

    /** 当前激活床位 location 主键。 */
    private Long bedLocationId;
}
