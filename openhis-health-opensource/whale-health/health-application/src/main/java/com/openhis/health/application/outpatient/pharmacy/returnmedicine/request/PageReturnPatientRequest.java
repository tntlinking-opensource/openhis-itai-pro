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

package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import com.openhis.health.application.common.pagination.PageQueryBinder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询退药患者队列请求（IF-PHA-02-02）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "分页查询退药患者队列请求")
public class PageReturnPatientRequest extends PageQueryBinder.AbstractHealthPageRequest {

    private static final long serialVersionUID = 1L;

    /** 科室过滤（与门诊信息读模型 organization_id 对齐）。 */
    @Schema(description = "科室过滤（门诊信息 organization_id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long departmentId;

    /** 模糊查询关键字（姓名/就诊号/拼音/五笔/证件号片段）；写入分页关键字语义。 */
    @Schema(description = "模糊查询关键字（姓名/就诊号/拼音/五笔/证件号片段）")
    private String searchKey;
}
