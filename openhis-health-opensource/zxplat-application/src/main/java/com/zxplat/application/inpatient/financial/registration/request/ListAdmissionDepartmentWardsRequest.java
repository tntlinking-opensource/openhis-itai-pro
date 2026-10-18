/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 更正入院科室时的病区列表请求。
 *
 * <p>不按目标科室过滤，占位以便与其它登记接口一样走请求体。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "更正入院科室病区列表请求")
public class ListAdmissionDepartmentWardsRequest {
}
