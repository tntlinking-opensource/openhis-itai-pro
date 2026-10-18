/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.response;

import com.zxplat.application.inpatient.pharmacy.returnmedicine.dto.InpatientReturnPatientRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分页查询住院退药患者队列响应（IF-IPR-02）。
 */
/** 分页查询住院退药患者队列响应。 */
@Schema(description = "分页查询住院退药患者队列响应")
public class PageInpatientReturnPatientResponse extends PagedResult<InpatientReturnPatientRowDto> {

    private static final long serialVersionUID = 1L;
}
