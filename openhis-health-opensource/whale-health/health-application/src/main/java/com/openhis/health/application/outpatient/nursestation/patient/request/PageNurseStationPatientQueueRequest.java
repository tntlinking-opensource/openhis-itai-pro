/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.nursestation.patient.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询门诊护士站患者队列请求（IF-NUR-01；B.10.11）。
 *
 * <p>继承 {@link AbstractHealthPageRequest}，统一分页契约：
 * {@code pageNo} 为 1-based，{@code pageSize} 经边界防护，{@code keyword} 为可选检索关键字。</p>
 *
 * <p>会话管辖机构收窄仍由应用层 Resolver 负责（B.10.11），与本请求可选业务筛选 {@link #orgId}（开立科室）分离：
 * {@code orgId} 空表示全部开立科室，非空则等值匹配就诊 {@code organization_id}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询门诊护士站患者队列请求（可选开立科室 orgId；空=全部）")
public class PageNurseStationPatientQueueRequest extends AbstractHealthPageRequest {

    /**
     * 开立科室筛选（可选）。
     *
     * <p>空或未传=全部科室；非空时等值匹配就诊 {@code organization_id}（门诊挂号/开立科室上下文）。</p>
     */
    @Schema(description = "开立科室筛选（可选；空=全部）")
    private Long orgId;

    /**
     * 队列类型筛选（可选）。
     */
    @Schema(description = "队列类型筛选")
    private String queueType;
}
