/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.service;

import com.zxplat.domain.clinical.proceduredefinition.command.ClinicalProcV3ImportRowCommand;
import com.zxplat.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.zxplat.domain.clinical.proceduredefinition.repository.ProcedureDefinitionRepository;
import com.zxplat.domain.share.constants.ProcedureDefinitionConstants;
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
 * 国临手术 3.0 分批落库（独立短事务）；
 * 助记码空时按名称 {@link ProcedureDefinition#rebuildSearchKey()} 补全。
 */
@Service
@RequiredArgsConstructor
public class ClinicalProcV3CatalogImportWriter {

    private final ProcedureDefinitionRepository repository;

    /**
     * Excel 国临导入：按编码 upsert，已有行会更新名称/类别（BR-PD-009）。
     *
     * @param batchCodes 本批国临码
     * @param byCode     编码 → 导入行
     * @return 本批处理条数
     */
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
                // 无字段变化则跳过写库
                if (!needUpsert) {
                    continue;
                }
                // 名称变了则清助记，后续 rebuildSearchKey 重算
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

    /**
     * 医保导入一次加载本批国临：缺则插入（不改已有名称），{@code yb_no} 空则补缺省上传码。
     *
     * @param batchCodes 本批国临码（待建 ∪ 待填 yb）
     * @param toCreate   16 列待建行
     * @param clinicToYb 国临码 → 缺省医保码
     * @return 本批处理条数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int syncFromInsurance(
            List<String> batchCodes,
            Map<String, ClinicalProcV3ImportRowCommand> toCreate,
            Map<String, String> clinicToYb) {
        String source = ProcedureDefinitionConstants.SOURCE_CLINICAL_PROC_V3;
        Map<String, ProcedureDefinition> existingByCode = new HashMap<>();
        for (ProcedureDefinition entity : repository.findBySourceEnumAndProcedureCodeIn(source, batchCodes)) {
            if (entity != null && !Boolean.TRUE.equals(entity.getIsDeleted())) {
                existingByCode.put(entity.getProcedureCode(), entity);
            }
        }
        List<ProcedureDefinition> toSave = new ArrayList<>();
        for (String code : batchCodes) {
            ProcedureDefinition entity = existingByCode.get(code);
            if (entity == null) {
                ClinicalProcV3ImportRowCommand row = toCreate.get(code);
                // 无 16 列蓝图则不允许用医保码冒充新建国临
                if (row == null || StringUtils.isEmpty(row.getName())) {
                    continue;
                }
                entity = new ProcedureDefinition();
                entity.setSourceEnum(source);
                entity.setProcedureCode(code);
                entity.setName(row.getName());
                entity.setClinicCode(code);
                applyDefaultYbIfEmpty(entity, clinicToYb);
                entity.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                entity.rebuildSearchKey();
                entity.assertCoreFields();
                toSave.add(entity);
                continue;
            }
            String beforeYb = StringUtils.trimToNull(entity.getYbNo());
            applyDefaultYbIfEmpty(entity, clinicToYb);
            String afterYb = StringUtils.trimToNull(entity.getYbNo());
            // 已有行只可能补缺省医保码，名称不动
            if (!Objects.equals(beforeYb, afterYb)) {
                toSave.add(entity);
            }
        }
        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
        }
        return batchCodes.size();
    }

    /**
     * 国临 {@code yb_no} 为空时写入缺省医保码（不覆盖已有对照）。
     *
     * @param entity     国临行
     * @param clinicToYb 国临码 → 缺省医保码
     */
    private void applyDefaultYbIfEmpty(ProcedureDefinition entity, Map<String, String> clinicToYb) {
        if (StringUtils.isNotEmpty(entity.getYbNo())) {
            return;
        }
        String yb = clinicToYb.get(entity.getProcedureCode());
        if (StringUtils.isEmpty(yb)) {
            entity.setYbFlag(Boolean.FALSE);
            entity.setYbMatchFlag(Boolean.FALSE);
            return;
        }
        entity.setYbNo(yb);
        entity.setYbFlag(Boolean.TRUE);
        entity.setYbMatchFlag(Boolean.TRUE);
        entity.setClinicCode(entity.getProcedureCode());
    }
}
