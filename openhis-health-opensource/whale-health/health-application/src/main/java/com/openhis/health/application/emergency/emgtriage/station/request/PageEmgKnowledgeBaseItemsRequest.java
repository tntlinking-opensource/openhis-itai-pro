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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.openhis.health.domain.share.query.AbstractPageQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 知识库维护分页（主诉/症状）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "急诊知识库维护-分页请求")
public class PageEmgKnowledgeBaseItemsRequest extends AbstractPageQuery {

    @NotBlank
    @Schema(description = "CHIEF_COMPLAINT 或 SYMPTOM", requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;

    @Schema(description = "名称模糊")
    private String nameHint;

    @Schema(description = "0 禁用 / 1 启用；空表示全部")
    private Integer status;

    @Override
    @NotNull(message = "页码不能为空")
    public Integer getPageNo() {
        return super.getPageNo();
    }

    @Override
    @NotNull(message = "每页条数不能为空")
    public Integer getPageSize() {
        return super.getPageSize();
    }
}
