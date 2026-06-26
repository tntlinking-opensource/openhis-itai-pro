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
 * 生命体征主表读模型（DM-DIA-002 v2.1 §4.6.1）。
 */
@Getter
@Setter
public class VitalSignHeaderQueryModel {

    /** 主表 id。 */
    private Long id;

    /** 患者 id。 */
    private Long patientId;

    /** 就诊 id。 */
    private Long encounterId;

    /** 记录时点。 */
    private LocalDateTime recordedAt;

    /** 单据类型 code。 */
    private String typeEnum;

    /** 业务单号。 */
    private String busNo;

    /** 病床 id。 */
    private Long locationId;

    /** 婴儿序号。 */
    private Integer babyNo;

    /** 记录人 id。 */
    private Long recorderId;

    /** 备注。 */
    private String remark;
}
