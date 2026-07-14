/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.pharmacy.returnmedicine.request;

import com.openhis.health.application.shared.pagination.PageQueryBinder;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 加载门诊药房退药初始化元数据请求（IF-PHA-02-01）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
/** 加载门诊药房退药初始化元数据请求。 */
@Schema(description = "加载门诊药房退药初始化元数据请求")
public class LoadReturnMedicineInitRequest extends PageQueryBinder.AbstractHealthPageRequest {
    private static final long serialVersionUID = 1L;
}
