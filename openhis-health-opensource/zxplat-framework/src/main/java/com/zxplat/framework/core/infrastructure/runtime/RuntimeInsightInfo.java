/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.runtime;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 框架运行时洞察信息（与存储/同步解耦）。
 */
@Getter
@Setter
@Builder
public class RuntimeInsightInfo {

    /** 洞察编码，如 adm_organization.create */
    private String insightCode;
    /** 分类，如 business */
    private String category;
    /** 操作用户 ID */
    private Long operatorUserId;
    /** 扩展属性 JSON */
    private String payloadJson;
    /** 客户端观测时间 */
    private LocalDateTime observedAt;
}
