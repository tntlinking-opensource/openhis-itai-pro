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

package com.openhis.health.domain.diagnosis.vitalsign.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 生命体征明细读模型（DM-DIA-002 v2.1 §4.6.3）。
 */
@Getter
@Setter
public class VitalSignDetailRowQueryModel {

    /** 明细 id。 */
    private Long id;

    /** 主表 id。 */
    private Long vitalSignId;

    /** 记录时点（join 主表）。 */
    private LocalDateTime recordedAt;

    /** 单据类型（join 主表）。 */
    private String typeEnum;

    /** 术语体系 code。 */
    private String termCode;

    /** 字典项 code。 */
    private String itemCode;

    /** 字典项值。 */
    private String itemValue;

    /** 单位。 */
    private String units;

    /** 明细备注。 */
    private String remark;
}
