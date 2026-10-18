/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.notice.request;

import lombok.Data;

/**
 * 目录检查请求（只读，不落待办）。
 */
@Data
public class CheckCatalogIssuesRequest {
    /** 契约主键；空则不按契约过滤国家目录镜像。 */
    private Long contractId;
    /** 契约 bus_no；与 contractId 二选一。 */
    private String contractBusNo;
    /** ACTIVITY / MEDICATION / DEVICE；空表示全部。 */
    private String productKind;
}
