/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.support.insurance.inventory;

import com.openhis.health.integration.insurance.inventory.InventoryThirdPartyUploadExtension;
import lombok.extern.slf4j.Slf4j;

/**
 * 第三方进销存扩展调用辅助：模块启用即尝试上传，失败不阻断本地业务。
 *
 * <p>供库房/药房入库、出库、盘点等 AppService 在本地事务成功后调用。</p>
 */
@Slf4j
public final class InventoryMedicareUploadSupport {

    private InventoryMedicareUploadSupport() {
    }

    /**
     * 扩展已启用时执行上传动作；异常仅记录 warn，不向上抛出。
     *
     * @param extension 进销存上传 Extension；为 null 或未启用时静默返回
     * @param uploadAction 实际上传逻辑（由调用方封装 Extension 调用）
     */
    public static void uploadIfEnabled(InventoryThirdPartyUploadExtension extension, Runnable uploadAction) {
        // Gate：未装配扩展或无上传动作则跳过
        if (extension == null || !extension.isEnabled() || uploadAction == null) {
            return;
        }
        try {
            uploadAction.run();
        } catch (RuntimeException ex) {
            // 本地业务已成功，上传失败仅告警不阻断
            log.warn("医保进销存上传失败（本地业务已成功）", ex);
        }
    }
}
