/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
    /** 登记列表状态多选（与 {@link #registrationListStatus} 语义一致，优先于单值）。 */
    private List<String> registrationListStatuses;
    /** 入院日期；按自然日过滤在院候选。 */
    private LocalDate admissionDate;
    /** 为 {@code true} 时仅返回预交金页左侧队列可见就诊（清床前，含 {@code COMPLETED} 待清床）。 */
    private Boolean prepayEligibleOnly;
    /**
     * 是否在上下文组装时加载诊断快照（{@code diagnosisJson}）。
     *
     * <p>{@code false} 时跳过逐条诊断 enrich（预交金/登记卡片队列等无需诊断列的场景，防 N+1）。</p>
     */
    private Boolean includeDiagnosisSnapshot;
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
    /**
     * 护士站列表状态筛选码（已废止）。
     *
     * <p>空=默认在院；历史 {@code SETTLED_DISCHARGED} 已迁出，应用层应置 null。</p>
     */
    private String nurseStationStatusFilter;
    /** 就诊状态持久化码集合（{@code status_enum IN}）；由应用层或读侧解析 {@code nurseStationStatusFilter} 后写入。 */
    private Collection<String> statusEnumIn;
    /** 为 {@code true} 时仅返回结算队列候选就诊。 */
    private Boolean settleQueue;
    /** 结算队列页签码（与 {@code domain.share.constants} 队列 tab 常量对齐）。 */
    private String settleQueueTab;
}
