/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.service;

import com.zxplat.domain.clinical.conditiondefinition.command.ClinicalV2ImportRowCommand;
import com.zxplat.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.zxplat.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.zxplat.domain.share.constants.DoctorStationDiagnosisConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.framework.core.application.util.StringUtils;
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
 * 国家临床版 2.0 分批落库（独立短事务，供大批量导入避免 HTTP/长事务超时）；
 * 助记码空时按名称 {@link ConditionDefinition#rebuildSearchKey()} 补全。
 */
@Service
@RequiredArgsConstructor
public class ClinicalV2CatalogImportWriter {

    private final ConditionDefinitionRepository repository;

    /**
     * 单批 upsert。
     *
     * @param batchCodes 本批编码
     * @param byCode     全量去重后的行映射
     * @return 本批处理条数（含无需变更的已存在行）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeBatch(List<String> batchCodes, Map<String, ClinicalV2ImportRowCommand> byCode) {
        String source = DoctorStationDiagnosisConstants.SOURCE_CLINICAL_V2;
        Map<String, ConditionDefinition> existingByCode = new HashMap<>();
        for (ConditionDefinition entity : repository.findBySourceEnumAndConditionCodeIn(source, batchCodes)) {
            if (entity != null && !Boolean.TRUE.equals(entity.getIsDeleted())) {
                existingByCode.put(entity.getConditionCode(), entity);
            }
        }
        List<ConditionDefinition> toSave = new ArrayList<>(batchCodes.size());
        for (String code : batchCodes) {
            ClinicalV2ImportRowCommand row = byCode.get(code);
            ConditionDefinition entity = existingByCode.get(code);
            if (entity == null) {
                entity = new ConditionDefinition();
                entity.setSourceEnum(source);
                entity.setConditionCode(code);
                entity.setName(row.getName());
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
