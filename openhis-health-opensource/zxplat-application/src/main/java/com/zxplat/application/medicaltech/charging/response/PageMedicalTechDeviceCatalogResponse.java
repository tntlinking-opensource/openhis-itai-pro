/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.charging.response;

import com.zxplat.application.medicaltech.charging.dto.MedicalTechDeviceCatalogRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 分页查询医技耗材选品响应（AS-MT-CHG-001 IF-MT-CHG-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@Schema(description = "分页查询医技耗材选品响应")
public class PageMedicalTechDeviceCatalogResponse extends PagedResult<MedicalTechDeviceCatalogRowDto> {
}
