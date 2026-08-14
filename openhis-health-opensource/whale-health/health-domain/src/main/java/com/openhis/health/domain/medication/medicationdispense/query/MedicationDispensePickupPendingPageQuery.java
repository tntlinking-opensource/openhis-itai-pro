/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
 * <p>中西药 {@code tcmFlag}、长临嘱 {@code therapyEnum} 由应用层预解析为 {@link #resolvedMedReqIds} 后写入
 * （须已排除 {@code DISCHARGE_MEDICATION}，见 **BR-IP-SUM-016**）；
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
     * 长嘱不可拆一盒一点由执行写路径整包装落一行，本列表不再做整盒补齐（原 **BR-IP-SUM-017** 已退役）。
     */
    private LocalDateTime queryDeadline;
}
