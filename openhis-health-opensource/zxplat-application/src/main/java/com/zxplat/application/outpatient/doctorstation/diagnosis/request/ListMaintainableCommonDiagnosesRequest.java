/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 列出当前登录用户可维护的个人/科室常用诊断请求。
 *
 * <p>归属对象由服务端按登录上下文解析，请求体无需传用户/科室主键；可传空对象 {@code {}}。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-列出可维护常用诊断请求（空体即可）")
public class ListMaintainableCommonDiagnosesRequest {
}
