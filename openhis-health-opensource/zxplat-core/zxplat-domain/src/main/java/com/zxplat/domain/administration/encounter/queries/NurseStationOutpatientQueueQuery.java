/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries;

import com.zxplat.domain.share.query.AbstractPageQuery;
import lombok.Getter;
import lombok.Setter;

/**
 * 门诊护士站患者队列分页入参（IF-NUR-01 / BR-NUR-001 / BR-NUR-017）。
 *
 * <p>继承分页窗（含 {@code startDate}/{@code endDate} 就诊开始时间闭区间、关键字）。
 * 空 {@link #orgId} 表示全部开立科室；{@link #pendingDeviceOnly} 为 true 时叠加待发耗材 EXISTS。</p>
 */
@Getter
@Setter
public class NurseStationOutpatientQueueQuery extends AbstractPageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 开立科室筛选（可选）。
     *
     * <p>非空时等值匹配就诊 {@code organization_id}（门诊挂号/开立科室上下文）；
     * 空或未传表示不按科室收窄（全部）。</p>
     */
    private Long orgId;

    /**
     * 是否仅返回仍有待发放耗材的就诊（已收费耗材请求且发放单缺失或仍为待发放）。
     *
     * <p>空或 {@code false} 不按耗材待发收窄；{@code true} 时叠加 EXISTS 条件且分页在过滤后生效。</p>
     */
    private Boolean pendingDeviceOnly;
}
