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

package com.openhis.health.domain.administration.encounter.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * 住院就诊上下文分页入参（替代 RM {@code InpatientInfoPageQuery}，读主表/EncounterQueryService）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InpatientEncounterPageQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /** 病区主键；护士站/医生站按管辖病区收窄时使用。 */
    private Long wardId;
    /** 患者主键；按患者精确检索时使用。 */
    private Long patientId;
    /** 就诊状态持久化码（{@code status_enum}）；单状态筛选。 */
    private String statusEnum;
    /** 登记列表状态（与挂号/登记读侧队列语义对齐）。 */
    private String registrationListStatus;
    /** 入院日期；按自然日过滤在院候选。 */
    private LocalDate admissionDate;
    /** 为 {@code true} 时仅返回预交金页左侧队列可见就诊（清床前，含 {@code COMPLETED} 待清床）。 */
    private Boolean prepayEligibleOnly;
    /** 为 {@code true} 时激活护士站专用队列过滤（EXISTS 任务/在院口径）。 */
    private Boolean nurseStationQueue;
    /** 为 {@code true} 时激活住院医生站在院队列过滤（IMP + 在科状态 + 科室收窄）。 */
    private Boolean doctorStationQueue;
    /** 病房（house）主键；按病房进一步收窄床位视图。 */
    private Long houseId;
    /** 当前用户管辖病区 id 集合；多病区护士/医生工作台收窄。 */
    private List<Long> managedWardIds;
    /** 护士站任务页签过滤码（待执行/已执行等，与 AS 队列 tab 对齐）。 */
    private String nurseStationTaskFilter;
    /** 护士站医嘱执行日期；按执行计划日过滤任务队列。 */
    private LocalDate nurseStationExecutionDate;
    /** 就诊主键白名单（{@code IN}）；批量卡片/勾选回显场景。 */
    private Collection<Long> encounterIdsIn;
    /** 为 {@code true} 时仅返回结算队列候选就诊。 */
    private Boolean settleQueue;
    /** 结算队列页签码（与 {@code domain.share.constants} 队列 tab 常量对齐）。 */
    private String settleQueueTab;
}
