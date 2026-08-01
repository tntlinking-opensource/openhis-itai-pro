/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 耗材目录导入前：按 Excel 采购/拆零/包装/库存原文，在统一术语 {@code unit_code} 下补齐缺失概念。
 *
 * <p>新建 code 取该体系下现有纯数字 code 的最大值 +1（字符串数字，如 {@code "11"}→{@code "12"}）；
 * display 为 Excel 原文 trim。与编码管理页 {@link CodeAppService#addConcept} 同路径。</p>
 *
 * <p>补写后须 {@link TerminologyOptionLoader#evict} 再 {@code index.load()}，
 * 避免同请求内仍命中旧下拉快照（对齐药品目录导入）。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCatalogImportConceptEnsureService {

    private final CodeAppService codeAppService;
    private final CodeSystemQueryService codeSystemQueryService;
    private final TerminologyOptionLoader terminologyOptionLoader;

    /**
     * 扫描导入行上四类单位原文：对 {@link DeviceCatalogImportTerminologyIndex#DICT_UNIT} 未命中的 label
     * 在 {@code unit_code} 下新建概念，并刷新术语索引。
     *
     * @param rows  已解析行
     * @param index 术语索引（方法内会 {@link DeviceCatalogImportTerminologyIndex#load()}）
     */
    public void ensureMissingConcepts(
        List<DeviceCatalogImportRow> rows,
        DeviceCatalogImportTerminologyIndex index
    ) {
        if (index == null) {
            return;
        }
        // Pre：先清 unit_code 软缓存再灌索引，避免上次导入已入库的单位仍被当成缺失
        terminologyOptionLoader.evict(TerminologyCodeSystemNameConstants.UNIT_CODE);
        index.load();
        Set<String> missingLabels = this.collectMissingUnitLabels(rows, index);
        if (missingLabels.isEmpty()) {
            return;
        }
        Optional<CodeSystem> codeSystemOpt =
            codeSystemQueryService.findByName(TerminologyCodeSystemNameConstants.UNIT_CODE);
        if (codeSystemOpt.isEmpty()) {
            log.warn("耗材目录导入补字典跳过：编码体系不存在 name={}",
                TerminologyCodeSystemNameConstants.UNIT_CODE);
            return;
        }
        CodeSystem codeSystem = codeSystemOpt.get();
        Long codeSystemId = codeSystem.getId();
        // Pre 续：取现有纯数字 code 最大值，后续新建依次 +1
        int nextNumeric = this.resolveMaxNumericCode(
            codeSystemQueryService.findConceptsByCodeSystem(codeSystemId)) + 1;
        int created = 0;
        for (String display : missingLabels) {
            // 库内已有同 display（仅缓存漏载）则跳过新建
            if (this.findActiveConceptByDisplay(codeSystemId, display) != null) {
                continue;
            }
            // Cmd：按数字序列分配 code；若 code 已被占用则继续 +1；创建失败则换下一码重试
            boolean written = false;
            int attempts = 0;
            while (!written && attempts < 50) {
                attempts = attempts + 1;
                String code = String.valueOf(nextNumeric);
                nextNumeric = nextNumeric + 1;
                if (codeSystemQueryService.findConceptByCodeSystemNameAndCode(
                    TerminologyCodeSystemNameConstants.UNIT_CODE, code) != null) {
                    continue;
                }
                if (this.createUnitConcept(codeSystemId, code, display)) {
                    created = created + 1;
                    written = true;
                }
            }
            if (!written) {
                log.warn("耗材目录导入补单位失败 display={}", display);
            }
        }
        if (created > 0) {
            log.info("耗材目录导入补齐 unit_code 概念 {} 条", created);
        }
        // Asm：清缓存后刷新索引，供后续校验解析
        terminologyOptionLoader.evict(TerminologyCodeSystemNameConstants.UNIT_CODE);
        index.load();
    }

    /**
     * 收集采购/拆零/包装/库存四列中，当前 DICT_UNIT 无法解析的中文 label（去重、保序）。
     */
    private Set<String> collectMissingUnitLabels(
        List<DeviceCatalogImportRow> rows,
        DeviceCatalogImportTerminologyIndex index
    ) {
        Set<String> missing = new LinkedHashSet<>();
        if (rows == null || rows.isEmpty()) {
            return missing;
        }
        for (DeviceCatalogImportRow row : rows) {
            this.collectOne(missing, index, row.getPurchaseUnit());
            this.collectOne(missing, index, row.getSplitUnit());
            this.collectOne(missing, index, row.getPackageUnit());
            this.collectOne(missing, index, row.getStockUnit());
        }
        return missing;
    }

    /**
     * 单列原文：空白跳过；已能 resolve 的跳过；否则记入缺失集合。
     */
    private void collectOne(
        Set<String> missing,
        DeviceCatalogImportTerminologyIndex index,
        String raw
    ) {
        if (StringUtils.isEmpty(raw)) {
            return;
        }
        String label = raw.trim();
        if (index.resolveCode(DeviceCatalogImportTerminologyIndex.DICT_UNIT, label) != null) {
            return;
        }
        missing.add(label);
    }

    /**
     * 按 display 查找未删除概念（避免重复建码）。
     */
    private Concept findActiveConceptByDisplay(Long codeSystemId, String display) {
        if (codeSystemId == null || StringUtils.isEmpty(display)) {
            return null;
        }
        List<Concept> concepts = codeSystemQueryService.findConceptsByCodeSystem(codeSystemId);
        if (concepts == null || concepts.isEmpty()) {
            return null;
        }
        String want = display.trim();
        for (Concept concept : concepts) {
            if (concept == null || Boolean.TRUE.equals(concept.getIsDeleted())) {
                continue;
            }
            String conceptDisplay = concept.getDisplay();
            if (conceptDisplay != null && want.equals(conceptDisplay.trim())) {
                return concept;
            }
        }
        return null;
    }

    /**
     * 调用编码管理同路径写入 unit_code 概念（调用方已保证 code 未被占用）。
     *
     * @return 是否创建成功
     */
    private boolean createUnitConcept(Long codeSystemId, String code, String display) {
        CreateConceptRequest request = new CreateConceptRequest();
        request.setCodeSystemId(codeSystemId);
        request.setCode(code);
        request.setDisplay(display);
        request.setDefinition("耗材目录导入自动补齐");
        request.setIsActive(Boolean.TRUE);
        try {
            codeAppService.addConcept(request);
            return true;
        } catch (RuntimeException ex) {
            log.warn("耗材目录导入补字典失败 system={}, code={}, display={}, err={}",
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
            // 仅统计纯数字 code（如 "11"），英文/混合码不参与序号
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
     * 将已确认的纯数字串转为 int（避免 Integer.parseInt，与枚举身份解析解耦）。
     */
    private int digitsToInt(String code) {
        int value = 0;
        for (int i = 0; i < code.length(); i++) {
            value = value * 10 + (code.charAt(i) - '0');
        }
        return value;
    }
}
