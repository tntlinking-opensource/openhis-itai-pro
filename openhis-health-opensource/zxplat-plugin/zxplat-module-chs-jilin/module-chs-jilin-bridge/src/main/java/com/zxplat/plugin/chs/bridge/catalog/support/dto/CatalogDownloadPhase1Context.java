/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support.dto;

import com.zxplat.plugin.chs.gateway.client.InvokeContext;

/**
 * 目录下载阶段一工作流上下文。
 * <p>由 {@link com.zxplat.plugin.chs.bridge.catalog.workflow.CatalogDownloadPhase1Workflow}
 * 消费，封装契约、接口号、版本链起点与出站调用上下文。</p>
 *
 * @param contractBusNo  契约业务编号
 * @param contractId     费用性质/契约主键
 * @param tenantId       租户 ID
 * @param infno          医保接口编号（如 1301、1305）
 * @param startVer       版本链起始 ver（通常来自本地游标）
 * @param fixmedinsCode  定点医药机构编号
 * @param invokeContext  签到/操作员等 gateway 出站上下文
 * @param clearZipCache  是否在开始前清空本地 ZIP 缓存
 * @param maxRounds      最大轮次上限，null 表示不限制（探测模式可传入）
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
