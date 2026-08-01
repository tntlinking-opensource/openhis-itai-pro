/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.servicecatalog.support;

import com.openhis.health.application.basic.code.request.CreateConceptRequest;
import com.openhis.health.application.basic.code.service.CodeAppService;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.terminology.entity.CodeSystem;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 诊疗目录导入前：按 Excel 销售/计价单位原文，在统一术语 {@code unit_code} 下补齐缺失概念。
 *
 * <p>新建 code 取该体系下现有纯数字 code 的最大值 +1；display 为 Excel 原文 trim。
 * 与编码管理页 {@link CodeAppService#addConcept} 同路径。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServiceCatalogImportConceptEnsureService {

    private final CodeAppService codeAppService;
    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 扫描导入行销售/计价单位：对 {@link ServiceCatalogImportTerminologyIndex#DICT_UNIT} 未命中的 label
     * 在 {@code unit_code} 下新建概念，并刷新术语索引。
     *
     * @param rows  已解析行
     * @param index 术语索引（方法内会 {@link ServiceCatalogImportTerminologyIndex#load()}）
     */
    public void ensureMissingConcepts(
        List<ServiceCatalogImportRow> rows,
        ServiceCatalogImportTerminologyIndex index
    ) {
        if (index == null) {
            return;
        }
        // Pre：先灌索引，再按 label 收集缺口
        index.load();
        Set<String> missingLabels = this.collectMissingUnitLabels(rows, index);
        if (missingLabels.isEmpty()) {
            return;
        }
        Optional<CodeSystem> codeSystemOpt =
            codeSystemQueryService.findByName(TerminologyCodeSystemNameConstants.UNIT_CODE);
        if (codeSystemOpt.isEmpty()) {
            log.warn("诊疗目录导入补字典跳过：编码体系不存在 name={}",
                TerminologyCodeSystemNameConstants.UNIT_CODE);
            return;
        }
        Long codeSystemId = codeSystemOpt.get().getId();
        // Pre 续：取现有纯数字 code 最大值，后续新建依次 +1
        int nextNumeric = this.resolveMaxNumericCode(
            codeSystemQueryService.findConceptsByCodeSystem(codeSystemId)) + 1;
        int created = 0;
        for (String display : missingLabels) {
            // Cmd：按数字序列分配 code；若 code 已被占用则继续 +1
            boolean written = false;
            while (!written) {
                String code = String.valueOf(nextNumeric);
                nextNumeric = nextNumeric + 1;
                if (codeSystemQueryService.findConceptByCodeSystemNameAndCode(
                    TerminologyCodeSystemNameConstants.UNIT_CODE, code) != null) {
                    continue;
                }
                if (this.createUnitConcept(codeSystemId, code, display)) {
                    created = created + 1;
                }
                written = true;
            }
        }
        if (created > 0) {
            log.info("诊疗目录导入补齐 unit_code 概念 {} 条", created);
        }
        // Asm：刷新索引供后续校验解析
        index.load();
    }

    /**
     * 收集销售/计价单位列中，当前 DICT_UNIT 无法解析的中文 label（去重、保序）。
     */
    private Set<String> collectMissingUnitLabels(
        List<ServiceCatalogImportRow> rows,
        ServiceCatalogImportTerminologyIndex index
    ) {
        Set<String> missing = new LinkedHashSet<>();
        if (rows == null || rows.isEmpty()) {
            return missing;
        }
        for (ServiceCatalogImportRow row : rows) {
            if (row == null) {
                continue;
            }
            this.collectOne(missing, index, row.getPackageUnit());
        }
        return missing;
    }

    /**
     * 单列原文：空白跳过；已能 resolve 的跳过；否则记入缺失集合。
     */
    private void collectOne(
        Set<String> missing,
        ServiceCatalogImportTerminologyIndex index,
        String raw
    ) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String label = raw.trim();
        if (index.resolveCode(ServiceCatalogImportTerminologyIndex.DICT_UNIT, label) != null) {
            return;
        }
        missing.add(label);
    }

    /**
     * 调用编码管理同路径写入 unit_code 概念。
     *
     * @return 是否创建成功
     */
    private boolean createUnitConcept(Long codeSystemId, String code, String display) {
        CreateConceptRequest request = new CreateConceptRequest();
        request.setCodeSystemId(codeSystemId);
        request.setCode(code);
        request.setDisplay(display);
        request.setDefinition("诊疗目录导入自动补齐");
        request.setIsActive(Boolean.TRUE);
        try {
            codeAppService.addConcept(request);
            return true;
        } catch (RuntimeException ex) {
            log.warn("诊疗目录导入补字典失败 system={}, code={}, display={}, err={}",
                TerminologyCodeSystemNameConstants.UNIT_CODE, code, display, ex.getMessage());
            return false;
        }
    }

    /**
     * 扫描概念列表，取纯数字 code 的最大值；无纯数字时返回 0（下一步从 1 起）。
     */
    private int resolveMaxNumericCode(List<Concept> concepts) {
        int max = 0;
        if (concepts == null || concepts.isEmpty()) {
            return max;
        }
        for (Concept concept : concepts) {
            if (concept == null || StringUtils.isEmpty(concept.getCode())) {
                continue;
            }
            String code = concept.getCode().trim();
            if (!this.isPureDigits(code)) {
                continue;
            }
            int value = this.digitsToInt(code);
            if (value > max) {
                max = value;
            }
        }
        return max;
    }

    /**
     * 判断字符串是否每一位均为十进制数字。
     */
    private boolean isPureDigits(String code) {
        if (StringUtils.isEmpty(code)) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将已确认的纯数字串转为 int。
     */
    private int digitsToInt(String code) {
        int value = 0;
        for (int i = 0; i < code.length(); i++) {
            value = value * 10 + (code.charAt(i) - '0');
        }
        return value;
    }
}
