/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.costdetail.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-CDT-01：费用明细页初始化请求。
 *
 * <p>无业务字段；服务端以 {@link java.time.LocalDate#now()} 计算默认日期区间。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院护士站费用明细页初始化请求")
public class LoadNurseStationCostDetailPageInitRequest {
}
