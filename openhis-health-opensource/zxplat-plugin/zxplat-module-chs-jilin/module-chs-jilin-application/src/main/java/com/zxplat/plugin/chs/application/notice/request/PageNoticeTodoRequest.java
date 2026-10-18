/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 催办待办分页请求（IF-CHS-CAT-005-R-04）。
 */
@Data
public class PageNoticeTodoRequest {
    /** 页码（从 1 开始）；空时按 1。 */
    private Integer page;
    /** 每页条数；空时按 20。 */
    private Integer size;
    /** 待办类型（STOP / PAY_CLASS）；空表示不限。 */
    private String todoType;
    /** 待办状态；空时领域默认 OPEN。 */
    private String status;
}
