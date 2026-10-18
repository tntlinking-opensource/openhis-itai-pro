/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import com.zxplat.application.medicaltech.charging.dto.MedicalTechDeviceCatalogRowDto;
import com.zxplat.framework.core.application.paging.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * IF-MT-CHG-06：医技诊疗选品分页响应。
 *
 * <p>行 DTO 复用 {@link MedicalTechDeviceCatalogRowDto}（definitionId=activityId；无批号/库房）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "医技诊疗选品分页")
public class PageMedicalTechServiceCatalogResponse extends PagedResult<MedicalTechDeviceCatalogRowDto> {
}
