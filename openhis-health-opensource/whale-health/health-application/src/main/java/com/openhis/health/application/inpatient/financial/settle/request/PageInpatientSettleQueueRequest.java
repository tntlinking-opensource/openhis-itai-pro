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

package com.openhis.health.application.inpatient.financial.settle.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 住院结算患者队列分页请求（IF-IP-STL-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 住院结算患者队列分页请求。 */
@Schema(description = "住院结算患者队列分页请求")
public class PageInpatientSettleQueueRequest extends AbstractHealthPageRequest {

    @Schema(description = "入院日期筛选")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    /** 入院日期筛选（业务字段）。 */
    private LocalDate admissionDate;

    /** 队列页签：PENDING_SETTLE / SETTLED。 */
    @Schema(description = "结算队列页签 code")
    /** 结算队列页签 code（业务字段）。 */
    private String settleQueueTab;
}
