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

package com.openhis.health.application.inpatient.nursestation.inpatientbilling.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-01：划价选品目录分页请求。
 *
 * <p>纯目录检索，不绑定就诊/患者；分页关键字走 {@link AbstractHealthPageRequest}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "护士站划价选品目录分页请求")
public class PageNurseStationPricingCatalogRequest extends AbstractHealthPageRequest {

    /** 产品物理表收窄；空则默认 wkf_activity_definition + adm_device_definition（BR-IP-PRC-002）。 */
    @Schema(description = "产品物理表集合；空则默认诊疗+耗材")
    private List<String> productTableNames;

    /** 定价来源：零售价 / 批次售价；空回落零售价。 */
    @Schema(description = "定价来源码；空回落零售价")
    private String pricingSourceCode;

    /** 机构单元（选品目录机构维度筛选，可空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "机构单元")
    private Long organizationUnitId;

    /** 耗材库房筛选（仅耗材行生效，不做库存 Gate）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "耗材库房筛选")
    private Long pharmacyLocationId;

    /** 关键字排序模式（与 ProductDefinition 分页读模型约定一致）。 */
    @Schema(description = "关键字排序模式")
    private String sortMode;
}
