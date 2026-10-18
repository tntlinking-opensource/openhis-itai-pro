/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.clinical.proceduredefinition.service;

import com.zxplat.domain.clinical.proceduredefinition.command.GeneralOpImportRowCommand;
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
 * 医保手术与操作（general_op）分批落库（独立短事务）；
 * 助记码空时按名称 {@link ProcedureDefinition#rebuildSearchKey()} 补全。
 */
@Service
@RequiredArgsConstructor
public class GeneralOpCatalogImportWriter {

    private final ProcedureDefinitionRepository repository;

    /**
     * 按医保手术操作代码 upsert；国临码仅在空时写入，不覆盖手工对照。
     *
     * @param batchCodes 本批医保码
     * @param byCode     编码 → 导入行
     * @return 本批处理条数
     */
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
                applyClinicWithoutOverwrite(entity, row.getBindClinicCode());
                entity.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                entity.rebuildSearchKey();
                entity.assertCoreFields();
                toSave.add(entity);
            } else {
                boolean nameChanged = !Objects.equals(entity.getName(), row.getName());
                boolean needSearchKey = StringUtils.isEmpty(entity.getPyStr()) || StringUtils.isEmpty(entity.getWbStr());
                String incomingClinic = catalogCode(row.getBindClinicCode());
                String existingClinic = catalogCode(entity.getClinicCode());
                boolean clinicFill = existingClinic == null && incomingClinic != null;
                // 历史导入曾把无国临行标成已对码，重导时纠正标记
                boolean matchFlagWrong = (existingClinic != null && !Boolean.TRUE.equals(entity.getYbMatchFlag()))
                    || (existingClinic == null && Boolean.TRUE.equals(entity.getYbMatchFlag()));
                boolean needUpsert = nameChanged
                    || !Objects.equals(entity.getYbNo(), code)
                    || !Boolean.TRUE.equals(entity.getYbFlag())
                    || clinicFill
                    || matchFlagWrong
                    || !PublicationStatus.ACTIVE.getCode().equals(entity.getStatusEnum())
                    || needSearchKey;
                // 无名称/医保码/国临回填变化则跳过
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
                applyClinicWithoutOverwrite(entity, incomingClinic);
                entity.setStatusEnum(PublicationStatus.ACTIVE.getCode());
                // 仅名称/助记变化才重算拼音五笔，避免一万行重导反复转码
                if (nameChanged || needSearchKey) {
                    entity.rebuildSearchKey();
                }
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
     * 医保行国临码：文件有临床版码且本行尚未对照时才写入；已有对照不覆盖。
     *
     * <p>文件没有临床版码时保持原值。国临码与医保码相同可以是权威对照，不能当成误刷清掉。</p>
     *
     * @param entity     医保目录实体
     * @param bindClinic 本次解析的国临码，可空
     */
    private void applyClinicWithoutOverwrite(ProcedureDefinition entity, String bindClinic) {
        String incoming = catalogCode(bindClinic);
        String existing = catalogCode(entity.getClinicCode());
        // 已有对照（含与医保码相同的权威同码）不覆盖
        if (existing != null) {
            entity.setClinicCode(existing);
            entity.setYbMatchFlag(Boolean.TRUE);
            return;
        }
        // 第 16 列给出临床版码才补空
        if (incoming != null) {
            entity.setClinicCode(incoming);
            entity.setYbMatchFlag(Boolean.TRUE);
            return;
        }
        entity.setClinicCode(null);
        entity.setYbMatchFlag(Boolean.FALSE);
    }

    /**
     * 目录码占位清洗：空白、{@code null}、{@code --} 视为无码。
     *
     * @param raw 原始码
     * @return 可用编码；无则 null
     */
    private static String catalogCode(String raw) {
        String value = StringUtils.trimToNull(raw);
        if (value == null) {
            return null;
        }
        if ("null".equalsIgnoreCase(value) || "--".equals(value)) {
            return null;
        }
        return value;
    }
}
