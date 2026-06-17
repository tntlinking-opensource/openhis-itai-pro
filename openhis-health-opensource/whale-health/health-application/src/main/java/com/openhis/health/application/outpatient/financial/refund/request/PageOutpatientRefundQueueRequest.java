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

package com.openhis.health.application.outpatient.financial.refund.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
/** 分页查询待退费患者队列请求。 */
@Schema(description = "分页查询待退费患者队列请求")
public class PageOutpatientRefundQueueRequest extends AbstractHealthPageRequest {

    /** 队列页签：ALL / PENDING（待退费）/ SETTLED（已退费）。 */
    @NotBlank(message = "queueTabCode 不能为空")
    @Schema(description = "队列页签编码", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 队列页签编码（业务字段）。 */
    private String queueTabCode;
}
