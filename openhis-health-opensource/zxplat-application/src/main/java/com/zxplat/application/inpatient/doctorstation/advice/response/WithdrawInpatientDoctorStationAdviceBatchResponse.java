/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-16：签退医嘱批次响应。
 *
 * <p>整批成功标识与统一文案 message；失败时由事务整体回滚。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-医嘱开立-签退医嘱批次响应")
public class WithdrawInpatientDoctorStationAdviceBatchResponse {

    @Schema(description = "是否成功")
    /** 提示信息。 */
    private Boolean success;

    @Schema(description = "提示信息")
    private String message;
}
