/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.entity.CaseRecordHomepage;
import com.openhis.whale.mrms.domain.entity.HomepageDiagnosis;
import com.openhis.whale.mrms.domain.entity.HomepageProcedure;
import com.openhis.whale.mrms.domain.enums.FieldFillSource;
import com.openhis.whale.mrms.domain.model.HomepageSaveCommand;
import com.openhis.whale.mrms.domain.repository.CaseRecordHomepageRepository;
import com.openhis.whale.mrms.domain.repository.CaseRecordRepository;
import com.openhis.whale.mrms.domain.support.HomepageChildTableSupport;
import com.openhis.whale.mrms.domain.support.MrmsErrorCodes;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 病案首页聚合写侧领域服务：懒创建、保存、冻结。
 */
@Service
@RequiredArgsConstructor
public class CaseRecordHomepageDomainService {

    private static final TypeReference<Map<String, Object>> EXT_TYPE = new TypeReference<>() {
    };

    private final CaseRecordRepository caseRecordRepository;
    private final CaseRecordHomepageRepository homepageRepository;
    private final HomepageChildTableSupport childTableSupport;
    private final FieldSourcesJsonHelper fieldSourcesJsonHelper;
    private final ObjectMapper objectMapper;

    /**
     * 获取或创建首页。
     *
     * @param caseRecordId 主档主键
     * @param tenantId 租户
     * @param operatorId 操作人
     * @return 首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage ensureHomepage(Long caseRecordId, Long tenantId, Long operatorId) {
        return homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElseGet(() -> {
                    if (tenantId == null) {
                        throw new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "创建首页时租户不能为空");
                    }
                    CaseRecordHomepage homepage = new CaseRecordHomepage();
                    homepage.initEmpty(caseRecordId, tenantId, operatorId);
                    return homepageRepository.save(homepage);
                });
    }

    /**
     * 保存首页标量、扩展与子表。
     *
     * @param command 保存命令
     * @return 保存后首页
     */
    @Transactional(rollbackFor = Exception.class)
    public CaseRecordHomepage save(HomepageSaveCommand command) {
        CaseRecord record = caseRecordRepository.findByIdAndIsDeletedFalse(command.getCaseRecordId())
                .orElseThrow(() -> new BusinessException(MrmsErrorCodes.CASE_RECORD_NOT_FOUND, "病案主档不存在"));
        CaseRecordHomepage homepage = ensureHomepage(command.getCaseRecordId(), record.getTenantId(), command.getOperatorId());
        homepage.assertEditable();
        Map<String, String> sources = fieldSourcesJsonHelper.parse(homepage.getFieldSourcesJson());
        homepage.applyManualFields(command, sources);
        homepage.setExtensionJson(mergeExtension(homepage.getExtensionJson(), command.getExtension()));
        if (command.getDiagnoses() != null) {
            sources.put("diagnoses", FieldFillSource.MANUAL.getCode());
        }
        if (command.getProcedures() != null) {
            sources.put("procedures", FieldFillSource.MANUAL.getCode());
        }
        homepage.setFieldSourcesJson(fieldSourcesJsonHelper.toJson(sources));
        homepage.bumpVersion(command.getOperatorId());
        homepageRepository.save(homepage);
        if (command.getDiagnoses() != null) {
            childTableSupport.replaceDiagnoses(command.getCaseRecordId(), homepage.getTenantId(), command.getDiagnoses());
        }
        if (command.getProcedures() != null) {
            childTableSupport.replaceProcedures(command.getCaseRecordId(), homepage.getTenantId(), command.getProcedures());
        }
        return homepage;
    }

    /**
     * 查首页诊断行。
     *
     * @param caseRecordId 主档主键
     * @return 诊断行
     */
    public List<HomepageDiagnosis> listDiagnoses(Long caseRecordId) {
        return childTableSupport.listDiagnoses(caseRecordId);
    }

    /**
     * 查首页手术行。
     *
     * @param caseRecordId 主档主键
     * @return 手术行
     */
    public List<HomepageProcedure> listProcedures(Long caseRecordId) {
        return childTableSupport.listProcedures(caseRecordId);
    }

    /**
     * 归档冻结首页。
     *
     * @param caseRecordId 主档主键
     * @param operatorId 操作人
     */
    @Transactional(rollbackFor = Exception.class)
    public void freeze(Long caseRecordId, Long operatorId) {
        CaseRecordHomepage homepage = homepageRepository.findByCaseRecordIdAndIsDeletedFalse(caseRecordId)
                .orElse(null);
        if (homepage == null) {
            return;
        }
        homepage.freezeHomepage(operatorId);
        homepageRepository.save(homepage);
    }

    private String mergeExtension(String existingJson, Map<String, Object> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return existingJson;
        }
        Map<String, Object> merged = new HashMap<>();
        if (existingJson != null && !existingJson.isBlank()) {
            try {
                Map<String, Object> old = objectMapper.readValue(existingJson, EXT_TYPE);
                if (old != null) {
                    merged.putAll(old);
                }
            } catch (Exception ignored) {
                // 扩展 JSON 损坏时以本次入参为准重建
            }
        }
        merged.putAll(incoming);
        try {
            return objectMapper.writeValueAsString(merged);
        } catch (Exception ex) {
            return existingJson;
        }
    }
}
