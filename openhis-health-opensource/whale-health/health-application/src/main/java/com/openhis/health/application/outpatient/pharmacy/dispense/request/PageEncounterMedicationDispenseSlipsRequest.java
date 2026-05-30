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

package com.openhis.health.application.outpatient.pharmacy.dispense.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 就诊下药品发药单分页请求。
 *
 * <p>分页与通用检索字段见 {@link AbstractPageQuery}（代码生成通用规范 B.10.11）；机构收窄由读模型查询侧基于 {@link com.openhis.whale.core.infrastructure.security.ICurrentUser} 处理，本请求不传 organizationId。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "就诊下药品发药单分页请求")
public class PageEncounterMedicationDispenseSlipsRequest extends AbstractHealthPageRequest {

    /** 就诊主键；{@code null} 表示不按就诊过滤（慎用，列表可能偏大）。 */
    @Schema(description = "就诊主键（可空表示不按就诊过滤）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 发药库房（库位）主键；{@code null} 表示不按库房过滤。 */
    @Schema(description = "发药库房主键（可空表示不按库房过滤）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** {@code true} 时仅保留中药医嘱行（装配层契约占位，见 Mapper 说明）。 */
    @Schema(description = "true 仅中药")
    private Boolean tcmOnly;

    /** {@code true} 时仅保留非中药医嘱行（装配层契约占位）。 */
    @Schema(description = "true 仅非中药")
    private Boolean westernOnly;

    /** 发放状态过滤子集（各元素为 {@link com.openhis.health.domain.share.enums.DispenseStatus#getCode()}）；空表示不按状态过滤。 */
    @Schema(description = "发放状态 code 集合；空表示不按发放状态过滤")
    private List<String> dispenseStatusEnumIn;

    /** 医嘱请求生命周期过滤（各元素为 {@link com.openhis.health.domain.share.enums.RequestStatus#getCode()}）。 */
    @Schema(description = "医嘱发布态 RequestStatus code 集合（目标态过滤）")
    private List<String> requestStatusEnumIn;
}
