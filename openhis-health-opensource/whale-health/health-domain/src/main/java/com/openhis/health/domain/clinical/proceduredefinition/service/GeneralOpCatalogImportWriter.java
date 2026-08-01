/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.clinical.proceduredefinition.service;

import com.openhis.health.domain.clinical.proceduredefinition.command.GeneralOpImportRowCommand;
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
 * 医保手术与操作（general_op）分批落库（独立短事务）；
 * 助记码空时按名称 {@link ProcedureDefinition#rebuildSearchKey()} 补全。
 */
@Service
@RequiredArgsConstructor
public class GeneralOpCatalogImportWriter {

    private final ProcedureDefinitionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeBatch(List<String> batchCodes, Map<String, GeneralOpImportRowCommand> byCode) {
        String source = ProcedureDefinitionConstants.SOURCE_GENERAL_OP;
        Map<String, ProcedureDefinition> existingByCode = new HashMap<>();
        for (ProcedureDefinition entity : repository.findBySourceEnumAndProcedureCodeIn(source, batchCodes)) {
            if (entity != null && !Boolean.TRUE.equals(entity.getIsDeleted())) {
                existingByCode.put(entity.getProcedureCode(), entity);
            }
        }
        List<ProcedureDefinition> toSave = new ArrayList<>(batchCodes.size());
        for (String code : batchCodes) {
            GeneralOpImportRowCommand row = byCode.get(code);
            ProcedureDefinition entity = existingByCode.get(code);
            if (entity == null) {
                entity = new ProcedureDefinition();
                entity.setSourceEnum(source);
                entity.setProcedureCode(code);
                entity.setName(row.getName());
                entity.setPyStr(row.getPyStr());
                entity.setYbFlag(Boolean.TRUE);
                entity.setYbNo(code);
                entity.setYbMatchFlag(Boolean.TRUE);
                entity.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                entity.rebuildSearchKey();
                entity.assertCoreFields();
                toSave.add(entity);
            } else {
                boolean nameChanged = !Objects.equals(entity.getName(), row.getName());
                boolean needSearchKey = StringUtils.isEmpty(entity.getPyStr()) || StringUtils.isEmpty(entity.getWbStr());
                boolean needUpsert = nameChanged
                    || !Objects.equals(entity.getYbNo(), code)
                    || !Boolean.TRUE.equals(entity.getYbFlag())
                    || !Boolean.TRUE.equals(entity.getYbMatchFlag())
                    || !PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())
                    || needSearchKey;
                if (!needUpsert) {
                    continue;
                }
                entity.setName(row.getName());
                if (row.getPyStr() != null && !row.getPyStr().isBlank()) {
                    entity.setPyStr(row.getPyStr());
                } else if (nameChanged) {
                    entity.setPyStr(null);
                }
                if (nameChanged || StringUtils.isEmpty(entity.getWbStr())) {
                    entity.setWbStr(null);
                }
                entity.setYbFlag(Boolean.TRUE);
                entity.setYbNo(code);
                entity.setYbMatchFlag(Boolean.TRUE);
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
