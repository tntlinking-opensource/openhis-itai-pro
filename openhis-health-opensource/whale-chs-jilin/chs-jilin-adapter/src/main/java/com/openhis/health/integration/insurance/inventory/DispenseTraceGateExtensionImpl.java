/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inventory;

import com.openhis.health.integration.insurance.inventory.command.DispenseTraceGateSnapshot;
import com.openhis.whale.chs.jilin.contracts.client.ChsDispenseTraceGateClient;
import com.openhis.whale.chs.jilin.contracts.command.DispenseTraceGateCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/** {@link DispenseTraceGateExtension} 实现（委托 {@link ChsDispenseTraceGateClient}）。 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnBean(ChsDispenseTraceGateClient.class)
@ConditionalOnProperty(name = "openhis.modules.chs.enabled", havingValue = "true")
public class DispenseTraceGateExtensionImpl implements DispenseTraceGateExtension {

    private final ChsDispenseTraceGateClient dispenseTraceGateClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void assertTraceAllowedForPrepare(DispenseTraceGateSnapshot snapshot, List<String> submittedTraceCodes) {
        dispenseTraceGateClient.assertTraceAllowedForPrepare(toChsCommand(snapshot), submittedTraceCodes);
    }

    @Override
    public void assertTraceAllowedForDispense(DispenseTraceGateSnapshot snapshot) {
        dispenseTraceGateClient.assertTraceAllowedForDispense(toChsCommand(snapshot));
    }

    private static DispenseTraceGateCommand toChsCommand(DispenseTraceGateSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new DispenseTraceGateCommand()
            .setMedicationId(snapshot.getMedicationId())
            .setUnitCode(snapshot.getUnitCode())
            .setLotNumber(snapshot.getLotNumber())
            .setLocationId(snapshot.getLocationId())
            .setBusNo(snapshot.getBusNo());
    }
}
