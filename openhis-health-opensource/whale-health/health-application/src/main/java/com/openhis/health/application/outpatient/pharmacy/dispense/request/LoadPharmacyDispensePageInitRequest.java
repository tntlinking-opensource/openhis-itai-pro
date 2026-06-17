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

import com.openhis.health.module.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 门诊发药页初始化请求（IF-OPP-01）。
 *
 * <p>分页字段继承 {@link AbstractHealthPageRequest}（{@code pageNo} 从 1 起、{@code keyword} 等全局一致）。</p>
 *
 * <p>药房机构、库房可见范围由会话解析，不由本请求传入。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
/** 门诊发药页初始化请求。 */
@Schema(description = "门诊发药页初始化请求")
public class LoadPharmacyDispensePageInitRequest extends AbstractHealthPageRequest {

    /**
     * 当前药房库位主键（{@code adm_location.id}，{@code LocationForm.PHARMACY}）；用于收窄调配药师候选。
     */
    @Schema(description = "当前药房库位主键 adm_location.id（收窄调配药师候选）")
    private Long pharmacyLocationId;

    /**
     * 历史字段：岗位过滤语义码；**已废弃**，调配药师固定按 {@code ROLE_PHARMACIST} + 药房 Location 管辖收窄。
     */
    @Schema(description = "已废弃；调配药师固定按药师岗与药房管辖收窄")
    private String pharmacistRoleFilterCode;
}
