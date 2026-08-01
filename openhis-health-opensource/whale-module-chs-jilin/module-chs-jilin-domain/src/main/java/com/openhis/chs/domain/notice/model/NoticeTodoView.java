/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.notice.model;

import lombok.Data;

import java.time.LocalDate;

/**
 * 催办待办领域视图。
 */
@Data
public class NoticeTodoView {
    /** 待办主键。 */
    private Long id;
    /** 待办类型（STOP / PAY_CLASS）。 */
    private String todoType;
    /** 国家目录编码。 */
    private String hilistCode;
    /** 物价/项目代码；可空。 */
    private String projectCode;
    /** 产品/目录展示名；可空。 */
    private String productName;
    /** 生效/催办参考日。 */
    private LocalDate effectiveDate;
    /** 关联通知文件头；可空。 */
    private Long docId;
    /** 状态（OPEN / CLOSED）。 */
    private String status;
}
