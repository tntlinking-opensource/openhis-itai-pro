/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.support.dto;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次「重新扫描」会话：持有 scanToken 与全部子目录解析结果，TTL 内二次确认入库。
 */
@Data
public class NoticeScanSession {

    /** 扫描会话令牌。 */
    private String scanToken;

    /** 过期时刻。 */
    private Instant expireAt;

    /** 实际扫描根目录绝对路径。 */
    private String scanRoot;

    /** 扫描时传入的契约主键（确认时可覆盖）。 */
    private Long contractId;

    /** packDir → 缓存项（保持扫描顺序）。 */
    private Map<String, NoticeScanCachedItem> itemsByPackDir = new LinkedHashMap<>();
}
