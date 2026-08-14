/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /** 就诊 start_time（首次分床 {@code transitToInProgress} 锚点；转床不更新；BR-IP-ARG-007 滚费 Gate 主锚）。 */
    private LocalDateTime startTime;

    /**
     * 就诊 end_time（办理出院写入；未出院可空）。
     *
     * <p>滚费记进不计出：{@code end_time} 自然日不晚于 billDate 则整 encounter 当日不滚（BR-IP-ARG-008）。</p>
     */
    private LocalDateTime endTime;

    /** 首次分床时刻（全部 BED 轨迹最早 {@code start_time}；转床仅新增轨迹，不替换本字段）。 */
    private LocalDateTime bedAssignedAt;

    /** 就诊状态持久化码（IN_PROGRESS / ON_HOLD / COMPLETED）。 */
    private String statusEnum;

    /** 当前激活床位 location 主键。 */
    private Long bedLocationId;

    /** 责任护士主键（滚费主执行人；BR-IP-ARG-019）。 */
    private Long primaryNurseId;
}
