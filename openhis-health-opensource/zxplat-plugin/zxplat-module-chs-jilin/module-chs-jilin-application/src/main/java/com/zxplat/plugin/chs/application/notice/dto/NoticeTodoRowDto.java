/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 催办待办列表行（R-04）。
 */
@Data
public class NoticeTodoRowDto {
    /** 待办主键。 */
    private Long id;
    /** 待办类型（STOP / PAY_CLASS）。 */
    private String todoType;
    /** 国家目录编码。 */
    private String hilistCode;
    /** 物价/项目代码（可空）。 */
    private String projectCode;
    /** 产品/目录展示名（可空）。 */
    private String productName;
    /** 生效/催办参考日。 */
    private LocalDate effectiveDate;
    /** 关联通知文件头主键（可空）。 */
    private Long docId;
    /** 待办状态（OPEN / CLOSED）。 */
    private String status;
}
