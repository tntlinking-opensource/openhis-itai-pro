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

package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.openhis.health.application.common.pagination.PageQueryBinder.AbstractHealthPageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 住院发药页初始化请求（IF-IPP-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "住院发药页初始化请求")
public class LoadInpatientPharmacyDispensePageInitRequest extends AbstractHealthPageRequest {

    /** 当前药房机构（可选，用于收窄药师候选）。 */
    @Schema(description = "当前药房机构（收窄药师候选）")
    private Long pharmacyOrganizationId;

    /** 岗位过滤码，透传 {@code PractitionerQueryService#pageActivePractitionersForPosition}。 */
    @Schema(description = "岗位过滤码，透传药师分页查询")
    private Integer practitionerRoleFilterCode;
}
