/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.returnmedicine.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 加载住院退药页初始化元数据请求（IF-IPR-01）。
 */
@Data
/** 加载住院退药页初始化元数据请求。 */
@Schema(description = "加载住院退药页初始化元数据请求")
public class LoadInpatientReturnMedicineInitRequest implements Serializable {

    private static final long serialVersionUID = 1L;
}
