/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 医保协同运行参数（{@code insurance.coordination.*}）。
 *
 * <p>控制 Worker 调度、各 biz_type 异步协同开关与批次大小。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "insurance.coordination")
public class InsuranceCoordinationProperties {

    /** 是否启用协同单 Worker 调度。 */
    private boolean workerEnabled = true;

    /** Worker 扫描间隔（毫秒）。 */
    private long workerFixedDelayMs = 5000;

    /** 住院结算是否走 IP_SETTLE 异步协同单（默认 false 保持同步 MVP）。 */
    private boolean ipSettleAsyncEnabled = false;

    /** 门诊结算是否走 OP_SETTLE 异步协同单（默认 false 保持同步 MVP）。 */
    private boolean opSettleAsyncEnabled = false;

    /** 门诊退费前医保撤销是否走 OP_SETTLE_REVERSE 异步协同单（默认 false 保持同步 MVP）。 */
    private boolean opSettleReverseAsyncEnabled = false;

    /** 每批扫描协同单数量。 */
    private int workerBatchSize = 20;

    /** 发药/退药后进销存是否走 DISPENSE_UPLOAD 异步协同单（默认关，积压时可开）。 */
    private boolean dispenseUploadAsyncEnabled = false;
}
