/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inventory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/** 进销存上报配置（对齐 11-医保进销存 §9）。 */
@Getter
@Setter
@ConfigurationProperties(prefix = "openhis.chs.inventory")
public class ChsInventoryProperties {

    /** 总开关：关闭后 Port 与 Worker 均不上传；CHS 模块启用时默认 true。 */
    private boolean uploadEnabled = true;

    /** 无 encounter 库存场景默认医保契约 busNo（可空，回落租户首条非自费契约）。 */
    private String defaultContractBusNo;

    /** 未对码跳过平台调用。 */
    private boolean skipWithoutYbNo = true;

    /** 3505 成功后是否额外上报 3502（默认关）。 */
    private boolean saleAlsoInvChange3502 = false;

    private int maxRetry = 5;

    private List<Integer> retryBackoffSeconds = new ArrayList<>(List.of(60, 300, 900));

    private boolean traceRequiredDefault = true;

    private boolean allowNoTraceOnSplit = true;

    private boolean blockSettleWithoutTrace = false;

    private List<String> policyExemptCatalogTypes = new ArrayList<>();

    /** 无码库品种目录类型（categoryCode 白名单）。 */
    private List<String> noCodeLibraryCatalogTypes = new ArrayList<>();

    private boolean enforceTraceCountEqualsQty = true;

    /** 定时扫描 FAILED/PENDING 并重试。 */
    private boolean retryWorkerEnabled = true;

    private long retryWorkerFixedDelayMs = 60_000L;

    private int retryWorkerBatchSize = 50;
}
