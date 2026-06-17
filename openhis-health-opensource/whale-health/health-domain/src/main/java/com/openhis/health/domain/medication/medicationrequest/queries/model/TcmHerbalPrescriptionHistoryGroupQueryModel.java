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

package com.openhis.health.domain.medication.medicationrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 患者历史中药处方按方聚合读模型（IF-DOR-39 读侧真源，排除当前就诊）。
 */
@Getter
@Setter
public class TcmHerbalPrescriptionHistoryGroupQueryModel {
    /** 处方号。 */
    private String prescriptionNo;
    /** 来源就诊标识。 */
    private Long encounterId;
    /** 方级开立时间。 */
    private LocalDateTime reqAuthoredTime;
    /** 方内行数。 */
    private Integer lineCount;
    /** 方内饮片定义主键集合（应用层补药名展示）。 */
    private List<Long> medicationIds = new ArrayList<>();
}
