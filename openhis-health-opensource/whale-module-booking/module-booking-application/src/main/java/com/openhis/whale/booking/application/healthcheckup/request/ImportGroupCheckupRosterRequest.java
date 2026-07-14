/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.healthcheckup.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 导入团检名单请求（9.5）：简单 CSV 文本，非 Excel。
 *
 * <p>列顺序：姓名,身份证号,学号,手机号（首行可为表头）。</p>
 */
@Getter
@Setter
public class ImportGroupCheckupRosterRequest {

    /** 团检批次主键。 */
    @NotNull
    private Long batchId;

    /** CSV 文本内容。 */
    @NotBlank
    private String csvContent;
}
