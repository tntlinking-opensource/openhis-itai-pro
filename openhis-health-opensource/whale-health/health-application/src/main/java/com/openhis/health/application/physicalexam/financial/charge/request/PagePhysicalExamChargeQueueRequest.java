/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询体检收费队列请求（IF-PE-CHG-02/03）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询体检收费队列请求")
public class PagePhysicalExamChargeQueueRequest extends AbstractHealthPageRequest {

    /**
     * 收费类型：{@code PERSONAL} 个检、{@code GROUP} 团检；缺省或空白时个检与团检合并。
     */
    @Schema(description = "收费类型：PERSONAL/GROUP")
    private String chargeKind;
}
