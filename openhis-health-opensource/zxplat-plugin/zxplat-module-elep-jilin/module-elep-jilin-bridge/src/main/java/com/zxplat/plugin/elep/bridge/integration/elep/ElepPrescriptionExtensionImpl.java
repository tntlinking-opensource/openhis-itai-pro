/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.bridge.integration.elep;


import com.zxplat.domain.infrastructure.integration.elep.ElepPrescriptionExtension;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.CloneElepPrescriptionExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepMedResultQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepQueryExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepRevokeExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepUploadExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepRxNoByPrescriptionExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.ElepRxNoByPrescriptionNoExtensionIn;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionIn.PatientElepHistoryQueryExtensionIn;
import com.zxplat.plugin.elep.bridge.integration.elep.mapper.ElepIntegrationMapper;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepMedExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepPageExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.CloneElepPrescriptionExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepRevokeExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepRxNoQueryExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.ElepUploadExtensionOut;
import com.zxplat.domain.infrastructure.integration.elep.ExtensionOut.PatientElepPrescriptionSummaryQueryExtensionOut;
import com.zxplat.plugin.elep.domain.client.JilinElepPrescriptionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link ElepPrescriptionExtension} 实现（委托 {@link JilinElepPrescriptionClient}）。省份差异由 Maven 模块隔离。
 * <p>只跟 {@code zxplat.modules.elep.enabled}；Client / Operations 由 application 侧按 deployment.mode 装配。</p>
 */
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "zxplat.modules.elep.enabled", havingValue = "true", matchIfMissing = true)
public class ElepPrescriptionExtensionImpl implements ElepPrescriptionExtension {

    private final JilinElepPrescriptionClient jilinElepPrescriptionClient;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean supportsPrescription(Long prescriptionGroupId) {
        return prescriptionGroupId != null;
    }

    @Override
    public ElepUploadExtensionOut upload(ElepUploadExtensionIn command) {
        return ElepIntegrationMapper.toExtensionUploadResult(
                jilinElepPrescriptionClient.upload(ElepIntegrationMapper.toContractsUploadCommand(command)));
    }

    @Override
    public ElepRevokeExtensionOut revoke(ElepRevokeExtensionIn command) {
        return ElepIntegrationMapper.toExtensionRevokeResult(
                jilinElepPrescriptionClient.revoke(ElepIntegrationMapper.toContractsRevokeCommand(command)));
    }

    @Override
    public ElepQueryExtensionOut query(ElepQueryExtensionIn command) {
        return ElepIntegrationMapper.toExtensionQueryResult(
                jilinElepPrescriptionClient.query(ElepIntegrationMapper.toContractsQueryCommand(command)));
    }

    @Override
    public ElepMedExtensionOut queryMedResult(ElepMedResultQueryExtensionIn command) {
        return ElepIntegrationMapper.toExtensionMedResult(
                jilinElepPrescriptionClient.queryMedResult(
                        ElepIntegrationMapper.toContractsMedResultQuery(command)));
    }

    @Override
    public Optional<ElepRxNoQueryExtensionOut> findRxNoByPrescription(ElepRxNoByPrescriptionExtensionIn query) {
        if (query == null || query.getPrescriptionGroupId() == null) {
            return Optional.empty();
        }
        return jilinElepPrescriptionClient.findRxNoByPrescription(query.getPrescriptionGroupId())
                .map(ElepIntegrationMapper::toExtensionRxNoSnapshot);
    }

    @Override
    public Optional<ElepRxNoQueryExtensionOut> findRxNoByPrescriptionNo(ElepRxNoByPrescriptionNoExtensionIn query) {
        if (query == null || query.getPrescriptionNo() == null) {
            return Optional.empty();
        }
        return jilinElepPrescriptionClient.findRxNoByPrescriptionNo(query.getPrescriptionNo())
                .map(ElepIntegrationMapper::toExtensionRxNoSnapshot);
    }

    @Override
    public ElepPageExtensionOut<PatientElepPrescriptionSummaryQueryExtensionOut> listPatientHistory(PatientElepHistoryQueryExtensionIn query) {
        return ElepIntegrationMapper.toExtensionPatientHistoryPage(
                jilinElepPrescriptionClient.listPatientHistory(
                        ElepIntegrationMapper.toContractsPatientHistoryQuery(query)));
    }

    @Override
    public CloneElepPrescriptionExtensionOut cloneToNewEncounter(CloneElepPrescriptionExtensionIn command) {
        return ElepIntegrationMapper.toExtensionPrescriptionDraftDto(
                jilinElepPrescriptionClient.cloneToNewEncounter(
                        ElepIntegrationMapper.toContractsCloneCommand(command)));
    }
}
