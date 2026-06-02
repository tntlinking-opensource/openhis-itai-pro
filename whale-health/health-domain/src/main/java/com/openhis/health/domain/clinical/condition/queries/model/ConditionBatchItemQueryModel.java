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

package com.openhis.health.domain.clinical.condition.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 诊断记录批量项模型。
 */
@Getter
@Setter
public class ConditionBatchItemQueryModel {
    /** 主键。 */
    private Long id;
    /** 患者主键。 */
    private Long patientId;
    /** 目录主键。 */
    private Long definitionId;
    /** 核销状态枚举编码。 */
    private String verificationStatusEnum;
    /** 临床状态枚举编码。 */
    private String clinicalStatusEnum;
    /** 病情描述（快照展示兜底）。 */
    private String description;
    /** 医保编码。 */
    private String ybNo;
}

