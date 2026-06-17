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

package com.openhis.health.domain.medication.medicationdispense.query;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 住院护士站汇总领药 · 待汇总发放明细分页查询入参（**AS-IP-NUR-010 · IF-IP-SUM-01**）。
 *
 * <p>中西药 {@code tcmFlag}、长临嘱 {@code therapyEnum} 由应用层预解析为 {@link #resolvedMedReqIds} 后写入；
 * 本 QueryService 仅按 {@code med_req_id IN} 收窄，不做 MedicationRequest 跨聚合过滤。</p>
 */
@Getter
@Setter
public class MedicationDispensePickupPendingPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 可见住院就诊主键集合（**BR-IP-SUM-001**）；非 null 且为空时表示无命中、返回空页。
     */
    private List<Long> encounterIds;

    /**
     * 医嘱状态/中药/长临嘱预解析的药品申请主键（应用层写入；非 null 时以 {@code med_req_id IN} 收窄）。
     */
    private List<Long> resolvedMedReqIds;

    /**
     * 计划发药截止时间（含）：{@code planned_dispense_time <= queryDeadline}（**BR-IP-SUM-002**）。
     */
    private LocalDateTime queryDeadline;
}
