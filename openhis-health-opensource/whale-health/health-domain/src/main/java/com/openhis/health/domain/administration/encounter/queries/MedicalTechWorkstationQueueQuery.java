/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技工作站患者队列分页入参（AS-MT-EXE-001 IF-MT-EXE-01 / BR-MT-EXE-002～003）。
 *
 * <p>继承分页窗（含 {@code startDate}/{@code endDate} 就诊开始时间闭区间、关键字）。
 * {@code startDate}/{@code endDate} **仅门诊**生效；住院由应用层清空，不按入院日筛。
 * {@link #loginTechOrgId} 为登录医技科；{@link #encounterScope} 收窄门诊/住院；
 * {@link #pendingDeviceOnly} 为 true 时仅保留本站待发耗材有活就诊；
 * {@link #inpatientStayFilter} 仅住院生效（在院 / 不在院）。</p>
 */
@Getter
@Setter
public class MedicalTechWorkstationQueueQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 登录医技科组织主键（必填）。
     *
     * <p>诊疗「有活」按 {@code ServiceRequest.org_id} 等值；耗材「有活」按发放耗材房
     * {@code Location.organization_id} 等值（可见性 DD）。</p>
     */
    private Long loginTechOrgId;

    /**
     * 就诊类别 scope：{@code OUTPATIENT}→AMB，{@code INPATIENT}→IMP。
     *
     * <p>非法或空时规约产出空结果，避免误扫全类别。</p>
     */
    private String encounterScope;

    /**
     * 是否仅返回本站仍有待发放耗材的就诊。
     *
     * <p>空或 {@code false} 不按耗材待发收窄；{@code true} 时叠加本科耗材房待发 EXISTS，分页在过滤后生效。</p>
     */
    private Boolean pendingDeviceOnly;

    /**
     * 住院在院态筛选（仅 {@code encounterScope=INPATIENT} 生效）。
     *
     * <p>{@code IN_HOSPITAL}（默认/空）：已入科 / 待转科·待出院 / 待出院结算。
     * {@code NOT_IN_HOSPITAL}：已清床离院（{@code DISCHARGED}）。门诊忽略本字段。</p>
     */
    private String inpatientStayFilter;
}
