/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.conditiondefinition.service;

import com.zxplat.domain.clinical.conditiondefinition.command.YbConditionImportRowCommand;
import com.zxplat.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.zxplat.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
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
 * 医保诊断目录分批落库（独立短事务）：按 {@code (sourceEnum, conditionCode)} upsert，
 * {@code yb_no=conditionCode}，{@code yb_flag/yb_match_flag=true}；
 * 助记码空时按名称 {@link ConditionDefinition#rebuildSearchKey()} 补全。
 */
@Service
@RequiredArgsConstructor
public class YbConditionCatalogImportWriter {

    private final ConditionDefinitionRepository repository;

    /**
     * 单批 upsert。
     *
     * @param sourceEnum 目录来源（如 general_dx）
     * @param batchCodes 本批编码
     * @param byCode     全量去重后的行映射
     * @return 本批处理条数（含无需变更的已存在行）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int writeBatch(
            String sourceEnum,
            List<String> batchCodes,
            Map<String, YbConditionImportRowCommand> byCode) {
        Map<String, ConditionDefinition> existingByCode = new HashMap<>();
        for (ConditionDefinition entity : repository.findBySourceEnumAndConditionCodeIn(sourceEnum, batchCodes)) {
            if (entity != null && !Boolean.TRUE.equals(entity.getIsDeleted())) {
                existingByCode.put(entity.getConditionCode(), entity);
            }
        }
        List<ConditionDefinition> toSave = new ArrayList<>(batchCodes.size());
        for (String code : batchCodes) {
            YbConditionImportRowCommand row = byCode.get(code);
            ConditionDefinition entity = existingByCode.get(code);
            if (entity == null) {
                entity = new ConditionDefinition();
                entity.setSourceEnum(sourceEnum);
                entity.setConditionCode(code);
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
