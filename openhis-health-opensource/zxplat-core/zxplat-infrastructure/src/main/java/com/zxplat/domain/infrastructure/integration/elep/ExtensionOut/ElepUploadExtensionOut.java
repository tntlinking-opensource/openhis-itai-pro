/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.elep.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 电子处方上传结果。
 */
@Data
@Accessors(chain = true)
public class ElepUploadExtensionOut {

    /** 上传是否成功。 */
    private boolean success;

    /** 平台返回的医保电子处方号。 */
    private String hiRxno;

    /** 处方追溯码（平台 rx_trace_code，扫码取药用）。 */
    private String rxTraceCode;

    /** 失败或提示信息。 */
    private String message;
}
