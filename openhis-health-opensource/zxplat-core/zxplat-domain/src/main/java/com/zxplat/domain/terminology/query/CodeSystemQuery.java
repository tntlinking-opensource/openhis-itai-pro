/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.terminology.query;

import com.zxplat.domain.share.enums.CodeSystemStatus;
import lombok.Data;

/**
 * 编码系统查询条件
 *
 * <p>用于封装查询编码系统的领域查询参数</p>
 *
 */
@Data
public class CodeSystemQuery {

    /**
     * 关键词（搜索名称、标题）
     */
    private String keyword;

    /**
     * 编码系统名称（精确匹配）
     */
    private String name;

    /**
     * 状态
     */
    private CodeSystemStatus status;
}
