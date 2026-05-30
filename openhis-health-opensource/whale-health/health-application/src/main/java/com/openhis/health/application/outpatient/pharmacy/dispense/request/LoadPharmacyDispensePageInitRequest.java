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

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
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
@Schema(description = "门诊发药页初始化请求")
public class LoadPharmacyDispensePageInitRequest extends AbstractHealthPageRequest {

    /**
     * 岗位/业务角色过滤码（可选）；用于调配药师分页候选过滤。
     *
     * <p>未传时应用服务按 SDD 使用默认登记口径。</p>
     */
    @Schema(description = "岗位或业务角色过滤整数码")
    private Integer pharmacistRoleFilterCode;
}
