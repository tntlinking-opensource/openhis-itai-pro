/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.dto;

import com.openhis.chs.api.InvokeContext;

/**
 * 目录下载阶段一上下文。
 */
public record CatalogDownloadPhase1Context(
    String contractBusNo,
    Long contractId,
    Long tenantId,
    String infno,
    String startVer,
    String fixmedinsCode,
    InvokeContext invokeContext,
    boolean clearZipCache,
    Integer maxRounds) {
}
