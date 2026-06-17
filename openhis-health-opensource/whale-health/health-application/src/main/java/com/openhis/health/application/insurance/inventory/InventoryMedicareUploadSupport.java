/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.insurance.inventory;

import com.openhis.whale.chs.jilin.port.InventoryInsurancePort;
import lombok.extern.slf4j.Slf4j;

/** CHS 进销存 Port 调用辅助：模块启用即尝试上传，失败不阻断本地业务。 */
@Slf4j
public final class InventoryMedicareUploadSupport {

    private InventoryMedicareUploadSupport() {
    }

    public static void uploadIfPortPresent(InventoryInsurancePort port, Runnable uploadAction) {
        if (port == null || uploadAction == null) {
            return;
        }
        try {
            uploadAction.run();
        } catch (RuntimeException ex) {
            log.warn("医保进销存上传失败（本地业务已成功）", ex);
        }
    }
}
