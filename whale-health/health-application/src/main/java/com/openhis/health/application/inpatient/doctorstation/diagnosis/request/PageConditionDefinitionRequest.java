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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.request;

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-04：分页检索已发布诊断定义请求（AS-IP-DOR-002）。
 *
 * <p>分页字段继承 {@link AbstractHealthPageRequest}（B.10.11，{@code pageNo} 为 1-based）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医生站-诊断定义分页检索请求")
public class PageConditionDefinitionRequest extends AbstractHealthPageRequest {

    /**
     * 诊断中西医维度筛选：{@code 1} 西医、{@code 2} 中医；空白表示不按目录来源收敛。
     */
    @Schema(description = "诊断中西医维度筛选：1=西医，2=中医；不传则不过滤来源")
    private String diagnosisKindCode;
}
