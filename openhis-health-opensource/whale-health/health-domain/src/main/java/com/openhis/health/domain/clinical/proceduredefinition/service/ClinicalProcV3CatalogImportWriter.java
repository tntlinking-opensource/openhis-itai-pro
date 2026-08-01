/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.service;

import com.openhis.health.domain.clinical.proceduredefinition.command.ClinicalProcV3ImportRowCommand;
import com.openhis.health.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.openhis.health.domain.clinical.proceduredefinition.repository.ProcedureDefinitionRepository;
import com.openhis.health.domain.share.constants.ProcedureDefinitionConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 国临手术 3.0 分批落库（独立短事务）；
 * 助记码空时按名称 {@link ProcedureDefinition#rebuildSearchKey()} 补全。
 */
@Service
@RequiredArgsConstructor
public class ClinicalProcV3CatalogImportWriter {

    private final ProcedureDefinitionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeBatch(List<String> batchCodes, Map<String, ClinicalProcV3ImportRowCommand> byCode) {
        String source = ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3;
        Map<String, ProcedureDefinition> existingByCode = new HashMap<>();
        for (ProcedureDefinition entity : repository.findBySourceEnumAndProcedureCodeIn(source, batchCodes)) {
            if (entity != null && !Boolean.TRUE.equals(entity.getIsDeleted())) {
                existingByCode.put(entity.getProcedureCode(), entity);
            }
        }
        List<ProcedureDefinition> toSave = new ArrayList<>(batchCodes.size());
        for (String code : batchCodes) {
            ClinicalProcV3ImportRowCommand row = byCode.get(code);
            ProcedureDefinition entity = existingByCode.get(code);
            if (entity == null) {
                entity = new ProcedureDefinition();
                entity.setSourceEnum(source);
                entity.setProcedureCode(code);
                entity.setName(row.getName());
                entity.setTypeCode(row.getTypeCode());
                entity.setClinicCode(code);
                entity.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                entity.rebuildSearchKey();
                entity.assertCoreFields();
                toSave.add(entity);
            } else {
                boolean nameChanged = !Objects.equals(entity.getName(), row.getName());
                boolean needSearchKey = StringUtils.isEmpty(entity.getPyStr()) || StringUtils.isEmpty(entity.getWbStr());
                boolean needUpsert = nameChanged
                    || !Objects.equals(entity.getClinicCode(), code)
                    || !Objects.equals(entity.getTypeCode(), row.getTypeCode())
                    || !PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())
                    || needSearchKey;
                if (!needUpsert) {
                    continue;
                }
                if (nameChanged) {
                    entity.setPyStr(null);
                    entity.setWbStr(null);
                }
                entity.setName(row.getName());
                entity.setTypeCode(row.getTypeCode());
                entity.setClinicCode(code);
                entity.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                entity.rebuildSearchKey();
                entity.assertCoreFields();
                toSave.add(entity);
            }
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
        return batchCodes.size();
    }
}
