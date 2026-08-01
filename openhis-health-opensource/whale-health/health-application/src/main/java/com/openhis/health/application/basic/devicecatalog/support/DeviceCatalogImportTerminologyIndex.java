/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.devicecatalog.support;

import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 耗材目录导入术语索引：一次加载码表 label/code 双向映射，供模板下拉与导入解析共用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCatalogImportTerminologyIndex {

    /** 字典键：耗材分类。 */
    public static final String DICT_CATEGORY = "category";

    /** 字典键：耗材种类。 */
    public static final String DICT_DEVICE_TYPE = "deviceType";

    /** 字典键：计量单位（术语 {@code unit_code}；采购/拆零/包装/库存共用）。 */
    public static final String DICT_UNIT = "unit";

    /** 字典键：财务类型。 */
    public static final String DICT_FIN_TYPE = "finType";

    /** 字典键：是/否。 */
    public static final String DICT_YES_NO = "yesNo";

    /** 字典键：医保等级。 */
    public static final String DICT_CHRGITM_LV = "chrgitmLv";

    /** 字典键：病案结算类型。 */
    public static final String DICT_MR_SETTLEMENT = "mrSettlement";

    /** 字典键：医保类别。 */
    public static final String DICT_YB_TYPE = "ybType";

    /** 术语下拉加载器。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** label → code（同 label 多码时保留稳定顺序第一条）。 */
    private final Map<String, Map<String, String>> labelToCodeByDict = new HashMap<>();

    /** code → code（精确匹配 code 直填容错）。 */
    private final Map<String, Map<String, String>> codeToCodeByDict = new HashMap<>();

    /** 各字典中文 label 列表（下拉数据源）。 */
    private final Map<String, List<String>> labelsByDict = new HashMap<>();

    /**
     * 从术语源构建索引（模板生成与导入校验前各调用一次即可）。
     */
    public void load() {
        // Gate：清空旧索引
        labelToCodeByDict.clear();
        codeToCodeByDict.clear();
        labelsByDict.clear();
        // Pre：按码表来源灌入各字典
        this.registerDict(
            DICT_CATEGORY,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE),
            true);
        this.registerDict(
            DICT_DEVICE_TYPE,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DEVICE_TYPE_CODE),
            false);
        this.registerDict(
            DICT_UNIT,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE),
            false);
        this.registerDict(
            DICT_FIN_TYPE,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE),
            false);
        this.registerDict(
            DICT_CHRGITM_LV,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.CHRGITM_LV),
            false);
        this.registerDict(
            DICT_MR_SETTLEMENT,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MR_SETTLEMENT_TYPE_CODE),
            false);
        this.registerDict(
            DICT_YB_TYPE,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MED_CHRGITM_TYPE),
            false);
        this.registerYesNoDict();
    }

    /**
     * 将持久化 code 解析为术语 label（导出中文列）；无法解析返回 {@code null}。
     *
     * @param dictKey 字典键
     * @param code    持久化 code
     * @return label；空白或未知为 {@code null}
     */
    public String resolveLabel(String dictKey, String code) {
        if (StringUtils.isEmpty(dictKey) || StringUtils.isEmpty(code)) {
            return null;
        }
        String normalized = code.trim();
        Map<String, String> labelToCode = labelToCodeByDict.get(dictKey);
        if (labelToCode == null || labelToCode.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : labelToCode.entrySet()) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            if (normalized.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * 将原始填写值解析为术语 code：trim 后先 label 再 code；无法解析返回 {@code null}。
     *
     * @param dictKey 字典键（{@link #DICT_CATEGORY} 等）
     * @param raw     用户填写原文
     * @return 解析到的 code；空白或未知为 {@code null}
     */
    public String resolveCode(String dictKey, String raw) {
        if (StringUtils.isEmpty(dictKey) || StringUtils.isEmpty(raw)) {
            return null;
        }
        String normalized = raw.trim();
        Map<String, String> labelMap = labelToCodeByDict.get(dictKey);
        if (labelMap != null) {
            String byLabel = labelMap.get(normalized);
            if (byLabel != null) {
                return byLabel;
            }
        }
        Map<String, String> codeMap = codeToCodeByDict.get(dictKey);
        if (codeMap != null) {
            return codeMap.get(normalized);
        }
        return null;
    }

    /**
     * 返回指定字典的全部中文 label（模板下拉与「③可选值一览」灌入）。
     *
     * @param dictKey 字典键
     * @return label 列表；未知字典返回空列表
     */
    public List<String> labelsFor(String dictKey) {
        List<String> labels = labelsByDict.get(dictKey);
        if (labels == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(labels);
    }

    /**
     * 注册平铺或树形术语选项到指定字典。
     *
     * @param dictKey     字典键
     * @param options     选项列表
     * @param flattenTree 是否展平 children 仅取叶子
     */
    private void registerDict(String dictKey, List<OptionDto> options, boolean flattenTree) {
        Map<String, String> labelToCode = new LinkedHashMap<>();
        Map<String, String> codeToCode = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        if (options != null) {
            if (flattenTree) {
                this.collectLeafOptions(options, labelToCode, codeToCode, labels);
            } else {
                this.collectFlatOptions(options, labelToCode, codeToCode, labels);
            }
        }
        labelToCodeByDict.put(dictKey, labelToCode);
        codeToCodeByDict.put(dictKey, codeToCode);
        labelsByDict.put(dictKey, labels);
    }

    /**
     * 是/否字典：术语 YES_NO_FLAG 归一为「是」「否」展示。
     */
    private void registerYesNoDict() {
        List<OptionDto> options = terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.YES_NO_FLAG);
        Map<String, String> labelToCode = new LinkedHashMap<>();
        Map<String, String> codeToCode = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        labels.add("是");
        labels.add("否");
        if (options != null) {
            for (OptionDto option : options) {
                if (option == null || StringUtils.isEmpty(option.getValue())) {
                    continue;
                }
                String code = option.getValue().trim();
                codeToCode.put(code, code);
                String display = this.normalizeYesNoLabel(option.getLabel());
                if (StringUtils.isNotEmpty(display)) {
                    this.putLabelMapping(labelToCode, display, code, DICT_YES_NO);
                }
            }
        }
        // 兜底：常见布尔文案
        this.putLabelMapping(labelToCode, "是", "1", DICT_YES_NO);
        this.putLabelMapping(labelToCode, "否", "0", DICT_YES_NO);
        labelToCodeByDict.put(DICT_YES_NO, labelToCode);
        codeToCodeByDict.put(DICT_YES_NO, codeToCode);
        labelsByDict.put(DICT_YES_NO, labels);
    }

    /**
     * 展平树形分类，仅叶子节点进入下拉与映射。
     */
    private void collectLeafOptions(
        List<OptionDto> options,
        Map<String, String> labelToCode,
        Map<String, String> codeToCode,
        List<String> labels
    ) {
        if (options == null) {
            return;
        }
        for (OptionDto option : options) {
            if (option == null) {
                continue;
            }
            if (option.getChildren() != null && !option.getChildren().isEmpty()) {
                this.collectLeafOptions(option.getChildren(), labelToCode, codeToCode, labels);
                continue;
            }
            this.collectOneOption(option, labelToCode, codeToCode, labels, DICT_CATEGORY);
        }
    }

    /**
     * 平铺选项收集。
     */
    private void collectFlatOptions(
        List<OptionDto> options,
        Map<String, String> labelToCode,
        Map<String, String> codeToCode,
        List<String> labels
    ) {
        if (options == null) {
            return;
        }
        for (OptionDto option : options) {
            this.collectOneOption(option, labelToCode, codeToCode, labels, null);
        }
    }

    /**
     * 单条选项写入 label/code 映射。
     */
    private void collectOneOption(
        OptionDto option,
        Map<String, String> labelToCode,
        Map<String, String> codeToCode,
        List<String> labels,
        String dictKeyForWarn
    ) {
        if (option == null || StringUtils.isEmpty(option.getValue())) {
            return;
        }
        String code = option.getValue().trim();
        codeToCode.put(code, code);
        if (StringUtils.isNotEmpty(option.getLabel())) {
            String label = option.getLabel().trim();
            this.putLabelMapping(labelToCode, label, code, dictKeyForWarn);
            if (!labels.contains(label)) {
                labels.add(label);
            }
        }
    }

    /**
     * label → code 写入；同 label 多码保留第一条并 warn。
     */
    private void putLabelMapping(Map<String, String> labelToCode, String label, String code, String dictKeyForWarn) {
        if (StringUtils.isEmpty(label) || StringUtils.isEmpty(code)) {
            return;
        }
        if (labelToCode.containsKey(label)) {
            if (!code.equals(labelToCode.get(label)) && dictKeyForWarn != null) {
                log.warn("耗材目录导入术语 label 重复：dict={}, label={}, 保留 code={}, 忽略 code={}",
                    dictKeyForWarn, label, labelToCode.get(label), code);
            }
            return;
        }
        labelToCode.put(label, code);
    }

    /**
     * 将术语文案归一为「是」或「否」；无法识别时返回 trim 后原文。
     */
    private String normalizeYesNoLabel(String label) {
        if (StringUtils.isEmpty(label)) {
            return null;
        }
        String trimmed = label.trim();
        if ("是".equals(trimmed) || "Y".equalsIgnoreCase(trimmed) || "YES".equalsIgnoreCase(trimmed)
            || "1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
            return "是";
        }
        if ("否".equals(trimmed) || "N".equalsIgnoreCase(trimmed) || "NO".equalsIgnoreCase(trimmed)
            || "0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return "否";
        }
        return trimmed;
    }
}
