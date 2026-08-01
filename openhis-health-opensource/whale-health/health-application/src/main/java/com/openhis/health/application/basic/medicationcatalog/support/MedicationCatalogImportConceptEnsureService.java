/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.medicationcatalog.support;

import com.openhis.health.application.basic.code.request.CreateConceptRequest;
import com.openhis.health.application.basic.code.service.CodeAppService;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.terminology.entity.CodeSystem;
import com.openhis.health.domain.terminology.entity.Concept;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 药品目录导入前：按 Excel 原文补齐缺失的单位/用法/剂型术语（与编码管理页 {@link CodeAppService#addConcept} 同路径）。
 *
 * <p>计量单位（采购/拆零/包装/库存）统一写入 {@code unit_code}，code 为该体系纯数字 max+1；
 * 剂量/DDD 仍分别写入 {@code dose_unit}/{@code ddd_unit}，可用中文→英文映射，否则 display 作 code。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicationCatalogImportConceptEnsureService {

    /** 肌肉注射：与种子 401 皮下 / 402 皮内 / 404 静脉编号对齐。 */
    public static final String METHOD_INTRAMUSCULAR_DISPLAY = "肌肉注射";

    private static final String METHOD_INTRAMUSCULAR_CODE = "403";

    /**
     * 剂量/DDD 常用中文 → 英文 code；仅用于 {@code dose_unit}/{@code ddd_unit}，不用于计量 {@code unit_code}。
     */
    private static final Map<String, String> DOSE_OR_DDD_PREFERRED_CODES = Map.ofEntries(
        Map.entry("盒", "box"),
        Map.entry("支", "branch"),
        Map.entry("袋", "bag"),
        Map.entry("瓶", "bottle"),
        Map.entry("箱", "case"),
        Map.entry("包", "pack"),
        Map.entry("片", "piece"),
        Map.entry("粒", "tablet"),
        Map.entry("个", "pcs"),
        Map.entry("丸", "pill"),
        Map.entry("克", "g"),
        Map.entry("贴", "patch"),
        Map.entry("滴", "drop"),
        Map.entry("毫升", "ml"),
        Map.entry("毫克", "mg"),
        Map.entry("微克", "ug"),
        Map.entry("升", "l"),
        Map.entry("国际单位", "IU")
    );

    private final CodeAppService codeAppService;
    private final CodeSystemQueryService codeSystemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 扫描导入行：缺失的剂量/计量/DDD 单位与默认用法、剂型写入术语，并刷新索引。
     *
     * <p>始终确保「肌肉注射」存在于 {@code method_code}。</p>
     *
     * @param rows  已解析行
     * @param index 术语索引（方法内会 {@link MedicationCatalogImportTerminologyIndex#load()}）
     */
    public void ensureMissingConcepts(
        List<MedicationCatalogImportRow> rows,
        MedicationCatalogImportTerminologyIndex index
    ) {
        if (index == null) {
            return;
        }
        index.load();
        Map<String, Set<String>> missingByDict = this.collectMissingLabels(rows, index);
        // 肌肉注射：不论文件是否出现，导入前补齐用法字典
        if (index.resolveCode(
            MedicationCatalogImportTerminologyIndex.DICT_METHOD, METHOD_INTRAMUSCULAR_DISPLAY) == null) {
            missingByDict
                .computeIfAbsent(MedicationCatalogImportTerminologyIndex.DICT_METHOD, key -> new LinkedHashSet<>())
                .add(METHOD_INTRAMUSCULAR_DISPLAY);
        }
        int created = 0;
        // unit_code 批内递增：同一批多个缺失 display 各取连续数字码
        int nextUnitNumericCode = -1;
        for (Map.Entry<String, Set<String>> entry : missingByDict.entrySet()) {
            String dictKey = entry.getKey();
            String codeSystemName = this.codeSystemNameForDict(dictKey);
            if (StringUtils.isEmpty(codeSystemName)) {
                continue;
            }
            Optional<CodeSystem> codeSystemOpt = codeSystemQueryService.findByName(codeSystemName);
            if (codeSystemOpt.isEmpty()) {
                log.warn("药品目录导入补字典跳过：编码体系不存在 name={}", codeSystemName);
                continue;
            }
            Long codeSystemId = codeSystemOpt.get().getId();
            // 计量单位：先算现有纯数字 max，再按批内顺序 +1
            if (MedicationCatalogImportTerminologyIndex.DICT_UNIT.equals(dictKey)
                && nextUnitNumericCode < 0) {
                nextUnitNumericCode = this.resolveNextUnitNumericCode(codeSystemId);
            }
            for (String display : entry.getValue()) {
                if (MedicationCatalogImportTerminologyIndex.DICT_UNIT.equals(dictKey)) {
                    // 计量：数字码冲突则继续 +1，直到写入成功
                    boolean written = false;
                    while (!written) {
                        String code = String.valueOf(nextUnitNumericCode);
                        nextUnitNumericCode = nextUnitNumericCode + 1;
                        if (codeSystemQueryService.findConceptByCodeSystemNameAndCode(
                            codeSystemName, code) != null) {
                            continue;
                        }
                        if (this.createConcept(codeSystemId, code, display, "药品目录导入自动补齐")) {
                            created = created + 1;
                        }
                        written = true;
                    }
                } else {
                    String code = this.preferredCode(dictKey, display);
                    if (this.createIfAbsent(dictKey, codeSystemName, codeSystemId, display, code)) {
                        created = created + 1;
                    }
                }
            }
        }
        if (created > 0) {
            log.info("药品目录导入补齐术语概念 {} 条", created);
        }
        // 补写后强制刷下拉软缓存，避免同请求内 index.load 仍命中旧快照
        terminologyOptionLoader.evict(
            TerminologyCodeSystemNameConstants.DOSE_UNIT);
        terminologyOptionLoader.evict(
            TerminologyCodeSystemNameConstants.UNIT_CODE);
        terminologyOptionLoader.evict(
            TerminologyCodeSystemNameConstants.DDD_UNIT);
        terminologyOptionLoader.evict(
            TerminologyCodeSystemNameConstants.METHOD_CODE);
        terminologyOptionLoader.evict(
            TerminologyCodeSystemNameConstants.DOSE_FORM_CODE);
        index.load();
    }

    /**
     * 收集行内无法解析的 display；采购/拆零/包装/追溯码/库存合并进 {@link MedicationCatalogImportTerminologyIndex#DICT_UNIT}。
     */
    private Map<String, Set<String>> collectMissingLabels(
        List<MedicationCatalogImportRow> rows,
        MedicationCatalogImportTerminologyIndex index
    ) {
        Map<String, Set<String>> missing = new LinkedHashMap<>();
        if (rows == null || rows.isEmpty()) {
            return missing;
        }
        for (MedicationCatalogImportRow row : rows) {
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT, row.getDoseUnit());
            // 四类计量列均补到 unit_code
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT, row.getPurchaseUnit());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT, row.getSplitUnit());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT, row.getPackageUnit());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT, row.getTraceUnit());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_UNIT, row.getStockUnit());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT, row.getDddUnit());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_METHOD, row.getMethod());
            this.collectOne(missing, index, MedicationCatalogImportTerminologyIndex.DICT_DOSE_FORM, row.getDoseForm());
        }
        return missing;
    }

    private void collectOne(
        Map<String, Set<String>> missing,
        MedicationCatalogImportTerminologyIndex index,
        String dictKey,
        String raw
    ) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String label = raw.trim();
        if (index.resolveCode(dictKey, label) != null) {
            return;
        }
        missing.computeIfAbsent(dictKey, key -> new LinkedHashSet<>()).add(label);
    }

    /**
     * 直接按给定 code/display 写入概念（调用方已保证 code 未被占用）。
     */
    private boolean createConcept(Long codeSystemId, String code, String display, String definition) {
        CreateConceptRequest request = new CreateConceptRequest();
        request.setCodeSystemId(codeSystemId);
        request.setCode(code);
        request.setDisplay(display);
        request.setDefinition(definition);
        request.setIsActive(Boolean.TRUE);
        try {
            codeAppService.addConcept(request);
            return true;
        } catch (RuntimeException ex) {
            log.warn("药品目录导入补字典失败 codeSystemId={}, code={}, display={}, err={}",
                codeSystemId, code, display, ex.getMessage());
            return false;
        }
    }

    /**
     * 若 code 已存在则尽量改用 display 作 code（剂量/DDD/用法/剂型）；计量单位不走本方法。
     *
     * @param dictKey        字典键
     * @param codeSystemName 编码体系名
     * @param codeSystemId   编码体系主键
     * @param display        Excel 原文（概念 display）
     * @param preferredCode  首选业务码
     * @return 是否新建成功
     */
    private boolean createIfAbsent(
        String dictKey,
        String codeSystemName,
        Long codeSystemId,
        String display,
        String preferredCode
    ) {
        String codeToCreate = preferredCode;
        if (codeSystemQueryService.findConceptByCodeSystemNameAndCode(codeSystemName, codeToCreate) != null) {
            if (codeToCreate.equals(display)) {
                log.warn("药品目录导入补字典跳过：code 已存在 system={}, code={}, display={}",
                    codeSystemName, codeToCreate, display);
                return false;
            }
            codeToCreate = display;
            if (codeSystemQueryService.findConceptByCodeSystemNameAndCode(codeSystemName, codeToCreate) != null) {
                log.warn("药品目录导入补字典跳过：display 作 code 仍冲突 system={}, display={}",
                    codeSystemName, display);
                return false;
            }
        }
        return this.createConcept(codeSystemId, codeToCreate, display, "药品目录导入自动补齐");
    }

    /**
     * 剂量/DDD：已知中文→英文；用法肌肉注射固定 403；其余（含剂型）用 display 自身。
     */
    private String preferredCode(String dictKey, String display) {
        if (MedicationCatalogImportTerminologyIndex.DICT_METHOD.equals(dictKey)
            && METHOD_INTRAMUSCULAR_DISPLAY.equals(display)) {
            return METHOD_INTRAMUSCULAR_CODE;
        }
        // 仅剂量与 DDD 走英文映射；计量单位不走本方法
        if (MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT.equals(dictKey)
            || MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT.equals(dictKey)) {
            String mapped = DOSE_OR_DDD_PREFERRED_CODES.get(display);
            if (StringUtils.isNotEmpty(mapped)) {
                return mapped;
            }
        }
        return display;
    }

    /**
     * 扫描 {@code unit_code} 体系现有概念，取纯数字 code 的最大值 + 1；无数字码时从 1 起。
     *
     * @param codeSystemId unit_code 体系主键
     * @return 下一个可用纯数字码
     */
    private int resolveNextUnitNumericCode(Long codeSystemId) {
        List<Concept> concepts = codeSystemQueryService.findConceptsByCodeSystem(codeSystemId);
        int max = 0;
        if (concepts != null) {
            for (Concept concept : concepts) {
                if (concept == null || StringUtils.isEmpty(concept.getCode())) {
                    continue;
                }
                String code = concept.getCode().trim();
                if (!this.isPureDigits(code)) {
                    continue;
                }
                int value = Integer.parseInt(code);
                if (value > max) {
                    max = value;
                }
            }
        }
        return max + 1;
    }

    /**
     * 是否为不含符号的纯十进制数字串（用于 unit_code 序号分配）。
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

    private String codeSystemNameForDict(String dictKey) {
        if (MedicationCatalogImportTerminologyIndex.DICT_DOSE_UNIT.equals(dictKey)) {
            return TerminologyCodeSystemNameConstants.DOSE_UNIT;
        }
        if (MedicationCatalogImportTerminologyIndex.DICT_UNIT.equals(dictKey)) {
            return TerminologyCodeSystemNameConstants.UNIT_CODE;
        }
        if (MedicationCatalogImportTerminologyIndex.DICT_DDD_UNIT.equals(dictKey)) {
            return TerminologyCodeSystemNameConstants.DDD_UNIT;
        }
        if (MedicationCatalogImportTerminologyIndex.DICT_METHOD.equals(dictKey)) {
            return TerminologyCodeSystemNameConstants.METHOD_CODE;
        }
        if (MedicationCatalogImportTerminologyIndex.DICT_DOSE_FORM.equals(dictKey)) {
            return TerminologyCodeSystemNameConstants.DOSE_FORM_CODE;
        }
        return null;
    }
}
