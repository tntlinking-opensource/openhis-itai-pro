/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder.AbstractHealthPageRequest;
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

    /**
     * 当前药房/科室药房库位主键（{@code adm_location.id}，{@code LocationForm#PHARMACY} 或 {@link com.openhis.health.domain.share.enums.LocationForm#DEPARTMENT_PHARMACY}）；用于收窄调配药师候选。
     */
    @Schema(description = "当前药房库位主键 adm_location.id（收窄调配药师候选）")
    private Long pharmacyLocationId;
}
