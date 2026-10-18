/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.pharmacy.returnmedicine.response;

import com.zxplat.application.outpatient.pharmacy.returnmedicine.dto.ReturnMedicineDetailRowDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 查询退药明细响应（IF-PHA-02-03）。
 */
@Data
/** 查询退药明细响应。 */
@Schema(description = "查询退药明细响应")
public class ReturnMedicineDetailResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "退药明细列表")
    private List<ReturnMedicineDetailRowDto> items;
}
