/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms;

import com.openhis.health.integration.mrms.command.DischargeCaseRecordBootstrapCommand;

/**
 * 离院后 MRMS 病案主档幂等建档 Extension。
 *
 * <p><b>实现</b>：{@code MrmsCaseRecordBootstrapExtensionImpl}（mrms-adapter）。</p>
 */
public interface MrmsCaseRecordBootstrapExtension {

    default boolean isEnabled() {
        return false;
    }

    default void onDischargeRecorded(DischargeCaseRecordBootstrapCommand command) {
        // NoOp
    }
}
