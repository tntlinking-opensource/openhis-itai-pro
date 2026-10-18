/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 医技工作站患者队列分页入参（AS-MT-EXE-001 IF-MT-EXE-01 / BR-MT-EXE-002～003）。
 *
 * <p>继承分页窗（含 {@code startDate}/{@code endDate} 就诊开始时间闭区间、关键字）。
 * {@code startDate}/{@code endDate} **仅门诊**生效；住院由应用层清空，不按入院日筛。
 * {@link #loginTechOrgId} 为登录医技科；{@link #visibleTechOrgIds} 为诊疗有活可见执行科室
 * （共看组时含对等科，空则回落登录科单值）；{@link #encounterScope} 收窄门诊/住院；
 * 队列硬门槛为本站可见诊疗有活；{@link #pendingDeviceOnly} 为 true 时在诊疗有活之上再收窄本站待发耗材；
 * {@link #executionQueueFilter} 按整单待执行/已执行分桶（门诊看项目+耗材，住院仅项目）；
 * {@link #inpatientStayFilter} 仅住院生效（在院 / 不在院）。</p>
 */
@Getter
@Setter
public class MedicalTechWorkstationQueueQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 登录医技科组织主键（必填）。
     *
     * <p>诊疗「有活」默认按 {@code ServiceRequest.org_id} 等值；若 {@link #visibleTechOrgIds}
     * 非空则按 IN 扩展。耗材待发红点/「仅待发」筛选仍按发放耗材房挂靠可见轴（可见性 DD），
     * 但不得单独作为队列准入条件。</p>
     */
    private Long loginTechOrgId;

    /**
     * 诊疗有活可见执行科室集合（共看组；含登录科）。
     *
     * <p>空或仅登录科 → 与历史单科等值一致；多值时 {@code ServiceRequest.org_id IN (...)}。</p>
     */
    private List<Long> visibleTechOrgIds = new ArrayList<>();

    /**
     * 就诊类别 scope：{@code OUTPATIENT}→AMB，{@code INPATIENT}→IMP。
     *
     * <p>非法或空时规约产出空结果，避免误扫全类别。</p>
     */
    private String encounterScope;

    /**
     * 是否仅返回「本站诊疗有活且仍有待发放耗材」的就诊。
     *
     * <p>空或 {@code false} 不按耗材待发收窄（仍须本站诊疗有活）；
     * {@code true} 时叠加本科耗材房待发 EXISTS，分页在过滤后生效。仅耗材无诊疗不得进队。</p>
     */
    private Boolean pendingDeviceOnly;

    /**
     * 队列执行态筛选（BR-MT-EXE-023）：{@code PENDING} / {@code EXECUTED}。
     *
     * <p>空或非法时不按执行态收窄（全量有活）。
     * {@code PENDING}：本站仍有尚可执行诊疗；门诊另含仍有待发耗材的就诊。
     * {@code EXECUTED}：本站可见诊疗均已做满；门诊另要求无待发耗材；住院不看耗材。</p>
     */
    private String executionQueueFilter;

    /**
     * 住院在院态筛选（仅 {@code encounterScope=INPATIENT} 生效）。
     *
     * <p>{@code IN_HOSPITAL}（默认/空）：已入科 / 待转科·待出院 / 待出院结算。
     * {@code NOT_IN_HOSPITAL}：已清床离院（{@code DISCHARGED}）。门诊忽略本字段。</p>
     */
    private String inpatientStayFilter;
}
