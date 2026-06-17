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
import com.openhis.health.application.inpatient.nursestation.inpatientbilling.dto.PrepickedProductKeyDto;
import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-PRC-01：划价选品目录分页请求。
 *
 * <p>组套 IF-08 预览仅给结构；套用期以 {@link #prepickedProductKeys} 收窄本 IF，
 * 读侧返回 {@code unitPrice} 与耗材 {@code defaultDispenseLocationId}，写 IF-02/04 回传 {@code performLocation}。
 * 分页关键字走 {@link AbstractHealthPageRequest}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 护士站划价选品目录分页请求。 */
@Schema(description = "护士站划价选品目录分页请求")
public class PageNurseStationPricingCatalogRequest extends AbstractHealthPageRequest {

    @Schema(description = "产品物理表集合；空则默认诊疗+耗材")
    private List<String> productTableNames;

    /** 机构单元（选品目录机构维度筛选，可空）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 机构单元。 */
    @Schema(description = "机构单元")
    private Long organizationUnitId;

    /** 耗材库房筛选（仅耗材行生效，不做库存 Gate）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 耗材库房筛选。 */
    @Schema(description = "耗材库房筛选")
    private Long pharmacyLocationId;

    /** 关键字排序模式（与 ProductDefinition 分页读模型约定一致）。 */
    @Schema(description = "关键字排序模式")
    private String sortMode;

    /** 组套/历史行 ids 收窄目录；价与 defaultDispenseLocationId 由本 IF 读侧返回（IF-08 预览不提供）。 */
    @Schema(description = "预选产品键列表（快捷套用 ids 查目录）")
    private List<PrepickedProductKeyDto> prepickedProductKeys;
}
