/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.application.notice.request;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 确认扫描结果入库请求（IF-CHS-CAT-005-W-02）。
 */
@Data
public class ConfirmNoticeScanImportRequest {

    /** 扫描会话令牌；必填。 */
    private String scanToken;

    /**
     * 要入库的子目录名列表；仅 NEW（force 时含 DUPLICATE）。
     * <p>空或 null = 全部 NEW。</p>
     */
    private List<String> packDirs = new ArrayList<>();

    /** 契约主键；可选，覆盖扫描时传入值。 */
    private Long contractId;

    /** 为 true 时允许 DUPLICATE 重导。 */
    private Boolean force;
}
