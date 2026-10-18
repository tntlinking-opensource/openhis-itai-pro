/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.medcas.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 单步上报结果（4401/4101A/4102）。
 */
@Getter
@Setter
public class UploadResultDto {

    /** 状态码：success/failed/skipped。 */
    private String statusCode;

    /** 上传日志主键（字符串，防 Long 精度丢失）。 */
    private String uploadLogId;

    /** 平台 infcode。 */
    private String infcode;

    /** 错误信息。 */
    private String errMsg;

    /** 组装/调用时间。 */
    private java.time.LocalDateTime assembledAt;

    /** 4101A 成功时平台 setl_list_id。 */
    private String setlListId;

    /** 是否本步跳过（已成功侧）。 */
    private Boolean skipped;
}
