/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.enums;

/** 枚举类通用接口 */
public interface EnumInterface {
    /**
     * 所属编码系统。
     */
    Long getCodeSystemId();

    /**
     * 编码（唯一标识，对应原来的 dictValue）。
     *
     * <p>例如：01, 02, A01.01。</p>
     */
    String getCode();

    /**
     * 显示名称（对应原来的 dictLabel）。
     *
     * <p>例如：中药饮片、中成药。</p>
     */
    String getDisplay();

    /**
     * 定义（详细描述，可选）。
     */
    String getDefinition();

    /**
     * 排序号（对应原来的 dictSort）。
     */
    Long getSortOrder();
}
