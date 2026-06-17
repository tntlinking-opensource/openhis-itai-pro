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

package com.openhis.health.application.outpatient.doctorstation.diagnosis.request;

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-DOR-07：分页检索有效诊断定义请求。分页与检索见 {@link AbstractPageQuery}（B.10.11）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 医生站-诊断定义分页检索请求。 */
@Schema(description = "医生站-诊断定义分页检索请求")
public class PageConditionDefinitionRequest extends AbstractHealthPageRequest {

    @Schema(description = "诊断中西医维度筛选：1=西医，2=中医；不传则不过滤来源")
    private String diagnosisKindCode;
}
