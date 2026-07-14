/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 进销存上报配置（对齐 11-医保进销存 §9）。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openhis.chs.inventory")
public class ChsInventoryProperties {

    /** 总开关：关闭后 Client 与 Worker 均不上传；CHS 模块启用时默认 true */
    private boolean uploadEnabled = true;

    /** 无 encounter 库存场景默认医保契约 busNo（可空，回落租户首条非自费契约） */
    private String defaultContractBusNo;

    /** 未对码跳过平台调用 */
    private boolean skipWithoutYbNo = true;

    /** 最大重试次数（不含），达到后不再调度 nextRetryAt */
    private int maxRetry = 5;

    /** 重试退避间隔（秒），按重试次数依次取用，末位复用 */
    private List<Integer> retryBackoffSeconds = new ArrayList<>(List.of(60, 300, 900));

    /** 默认是否要求采集追溯码 */
    private boolean traceRequiredDefault = true;

    /** 拆零发药场景是否允许无追溯码 */
    private boolean allowNoTraceOnSplit = true;

    /** 结算时是否拦截缺少追溯码 */
    private boolean blockSettleWithoutTrace = false;

    /** 政策豁免品种目录类型（categoryCode 白名单） */
    private List<String> policyExemptCatalogTypes = new ArrayList<>();

    /** 无码库品种目录类型（categoryCode 白名单） */
    private List<String> noCodeLibraryCatalogTypes = new ArrayList<>();

    /** 是否强制追溯码条数与发药数量一致 */
    private boolean enforceTraceCountEqualsQty = true;

    /** 定时扫描 FAILED/PENDING 并重试 */
    private boolean retryWorkerEnabled = true;

    /** 重试 Worker 固定延迟（毫秒） */
    private long retryWorkerFixedDelayMs = 60_000L;

    /** 重试 Worker 单次捞取条数上限 */
    private int retryWorkerBatchSize = 50;
}
