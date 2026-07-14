/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询待退费患者队列请求（IF-REF-02）。
 *
 * <p>分页键见 {@link AbstractHealthPageRequest}（pageNo 1-based、pageSize、keyword；B.10.11）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询待退费患者队列请求")
public class PageOutpatientRefundQueueRequest extends AbstractHealthPageRequest {

    /** 队列页签：ALL / PENDING（待退费）/ SETTLED（已退费）。 */
    @NotBlank(message = "queueTabCode 不能为空")
    @Schema(description = "队列页签编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String queueTabCode;
}
