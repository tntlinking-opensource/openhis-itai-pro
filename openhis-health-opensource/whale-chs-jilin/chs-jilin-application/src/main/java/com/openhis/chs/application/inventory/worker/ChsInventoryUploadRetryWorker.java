/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.inventory.worker;

import com.openhis.chs.application.inventory.ChsInventoryAppService;
import com.openhis.chs.domain.inventory.config.ChsInventoryProperties;
import com.openhis.chs.domain.inventory.dto.request.RetryPendingInvUploadsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 扫描 {@code yb_inv_upload} 中 FAILED/PENDING 并重试平台调用。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
@ConditionalOnProperty(name = "openhis.chs.inventory.retry-worker-enabled", havingValue = "true", matchIfMissing = true)
public class ChsInventoryUploadRetryWorker {

    private final ChsInventoryAppService inventoryAppService;
    private final ChsInventoryProperties inventoryProperties;

    @Scheduled(fixedDelayString = "${openhis.chs.inventory.retry-worker-fixed-delay-ms:60000}")
    public void tick() {
        if (!inventoryProperties.isUploadEnabled()) {
            return;
        }
        try {
            RetryPendingInvUploadsRequest request = new RetryPendingInvUploadsRequest()
                .setBatchSize(inventoryProperties.getRetryWorkerBatchSize());
            var result = inventoryAppService.retryPendingUploads(request);
            if (result.getAttempted() > 0) {
                log.info(
                    "进销存重试 Worker：attempted={} succeeded={} failed={} skipped={}",
                    result.getAttempted(),
                    result.getSucceeded(),
                    result.getFailed(),
                    result.getSkipped());
            }
        } catch (Exception ex) {
            log.warn("进销存重试 Worker 执行异常", ex);
        }
    }
}
