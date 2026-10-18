/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.medicationcatalog.support;

import com.zxplat.application.shared.option.FrequencyOptionLoader;
import com.zxplat.application.shared.option.TerminologyOptionLoader;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.OrderCalcRuleEnum;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 药品目录导入术语索引：一次加载码表 label/code 双向映射，供模板下拉与导入解析共用。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MedicationCatalogImportTerminologyIndex {

    /** 字典键：药品分类。 */
    public static final String DICT_CATEGORY = "category";

    /** 字典键：剂量单位（术语 {@code dose_unit}）。 */
    public static final String DICT_DOSE_UNIT = "doseUnit";

    /**
     * 字典键：统一计量单位（术语 {@code unit_code}）。
     * <p>采购/拆零/包装/库存四列 Excel 均解析到本字典，不再分四分码表。</p>
     */
    public static final String DICT_UNIT = "unit";

    /** 字典键：DDD 单位（术语 {@code ddd_unit}）。 */
    public static final String DICT_DDD_UNIT = "dddUnit";

    /** 字典键：剂型。 */
    public static final String DICT_DOSE_FORM = "doseForm";

    /** 字典键：默认用法。 */
    public static final String DICT_METHOD = "method";

    /** 字典键：默认频次。 */
    public static final String DICT_RATE = "rate";

    /** 字典键：财务类型。 */
    public static final String DICT_FIN_TYPE = "finType";

    /** 字典键：是/否。 */
    public static final String DICT_YES_NO = "yesNo";

    /** 字典键：药品性质。 */
    public static final String DICT_MEDICINE_PROPERTIES = "medicineProperties";

    /** 字典键：医保等级。 */
    public static final String DICT_CHRGITM_LV = "chrgitmLv";

    /** 字典键：病案结算类型。 */
    public static final String DICT_MR_SETTLEMENT = "mrSettlement";

    /** 字典键：医保类别。 */
    public static final String DICT_YB_TYPE = "ybType";

    /** 字典键：用法说明。 */
    public static final String DICT_DOSAGE_INSTRUCTION = "dosageInstruction";

    /** 字典键：DDD 值。 */
    public static final String DICT_DDD_CODE = "dddCode";

    /** 字典键：门诊/住院拆分属性（OrderCalcRuleEnum）。 */
    public static final String DICT_ORDER_CALC_RULE = "orderCalcRule";

    /** 术语下拉加载器。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 频次下拉加载器。 */
    private final FrequencyOptionLoader frequencyOptionLoader;

    /** label → code（同 label 多码时保留稳定顺序第一条）。 */
    private final Map<String, Map<String, String>> labelToCodeByDict = new HashMap<>();

    /** code → code（精确匹配 code 直填容错）。 */
    private final Map<String, Map<String, String>> codeToCodeByDict = new HashMap<>();

    /** code → 中文 label（导出行回填下拉文案）。 */
    private final Map<String, Map<String, String>> codeToLabelByDict = new HashMap<>();

    /** 各字典中文 label 列表（下拉数据源）。 */
    private final Map<String, List<String>> labelsByDict = new HashMap<>();

    /**
     * 从术语与频次源构建索引（模板生成、导入校验、导出回填前各调用一次即可）。
     */
    public void load() {
        // Gate：清空旧索引
        labelToCodeByDict.clear();
        codeToCodeByDict.clear();
        codeToLabelByDict.clear();
        labelsByDict.clear();
        // Pre：按 SDD 码表来源灌入各字典
        this.registerDict(
            DICT_CATEGORY,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICATION_CATEGORY_CODE),
            true);
        this.registerDict(
            DICT_DOSE_UNIT,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSE_UNIT),
            false);
        // 采购/拆零/包装/库存共用 unit_code
        this.registerDict(
            DICT_UNIT,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.UNIT_CODE),
            false);
        this.registerDict(
            DICT_DOSE_FORM,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSE_FORM_CODE),
            false);
        this.registerDict(
            DICT_METHOD,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.METHOD_CODE),
            false);
        this.registerDict(
            DICT_RATE,
            frequencyOptionLoader.loadActiveRateOptions(),
            false);
        this.registerDict(
            DICT_FIN_TYPE,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.FIN_TYPE_CODE),
            false);
        this.registerDict(
            DICT_MEDICINE_PROPERTIES,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.MEDICINE_PROPERTIES),
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
        this.registerDict(
            DICT_DOSAGE_INSTRUCTION,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DOSAGE_INSTRUCTION),
            false);
        this.registerDict(
            DICT_DDD_CODE,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DDD_CODE),
            false);
        this.registerDict(
            DICT_DDD_UNIT,
            terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.DDD_UNIT),
            false);
        this.registerDict(
            DICT_ORDER_CALC_RULE,
            EnumUtil.toOptions(OrderCalcRuleEnum.class),
            false);
        this.registerYesNoDict();
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
     * 将术语 code 解析为导出用中文 label；未知码回落为原 code，避免单元格空白。
     *
     * @param dictKey 字典键（{@link #DICT_CATEGORY} 等）
     * @param code    持久化 code
     * @return label；code 空白时 {@code null}
     */
    public String resolveLabel(String dictKey, String code) {
        if (StringUtils.isEmpty(dictKey) || StringUtils.isEmpty(code)) {
            return null;
        }
        String normalized = code.trim();
        Map<String, String> codeToLabel = codeToLabelByDict.get(dictKey);
        if (codeToLabel != null) {
            String label = codeToLabel.get(normalized);
            if (StringUtils.isNotEmpty(label)) {
                return label;
            }
        }
        // 码表未命中时仍写出原 code，便于人工对照
        return normalized;
    }

    /**
     * 注册平铺或树形术语选项到指定字典。
     *
     * @param dictKey   字典键
     * @param options   选项列表
     * @param flattenTree 是否展平 children 仅取叶子
     */
    private void registerDict(String dictKey, List<OptionDto> options, boolean flattenTree) {
        Map<String, String> labelToCode = new LinkedHashMap<>();
        Map<String, String> codeToCode = new LinkedHashMap<>();
        Map<String, String> codeToLabel = new LinkedHashMap<>();
        List<String> labels = new ArrayList<>();
        if (options != null) {
            if (flattenTree) {
                this.collectLeafOptions(options, labelToCode, codeToCode, codeToLabel, labels);
            } else {
                this.collectFlatOptions(options, labelToCode, codeToCode, codeToLabel, labels);
            }
        }
        labelToCodeByDict.put(dictKey, labelToCode);
        codeToCodeByDict.put(dictKey, codeToCode);
        codeToLabelByDict.put(dictKey, codeToLabel);
        labelsByDict.put(dictKey, labels);
    }

    /**
     * 是/否字典：术语 YES_NO_FLAG 归一为「是」「否」展示。
     */
    private void registerYesNoDict() {
        List<OptionDto> options = terminologyOptionLoader.loadOptions(TerminologyCodeSystemNameConstants.YES_NO_FLAG);
        Map<String, String> labelToCode = new LinkedHashMap<>();
        Map<String, String> codeToCode = new LinkedHashMap<>();
        Map<String, String> codeToLabel = new LinkedHashMap<>();
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
                    codeToLabel.putIfAbsent(code, display);
                }
            }
        }
        // 兜底：常见布尔文案
        this.putLabelMapping(labelToCode, "是", "1", DICT_YES_NO);
        this.putLabelMapping(labelToCode, "否", "0", DICT_YES_NO);
        codeToLabel.putIfAbsent("1", "是");
        codeToLabel.putIfAbsent("0", "否");
        labelToCodeByDict.put(DICT_YES_NO, labelToCode);
        codeToCodeByDict.put(DICT_YES_NO, codeToCode);
        codeToLabelByDict.put(DICT_YES_NO, codeToLabel);
        labelsByDict.put(DICT_YES_NO, labels);
    }

    /**
     * 展平树形分类，仅叶子节点进入下拉与映射。
     *
     * @param options     术语选项树
     * @param labelToCode label→code
     * @param codeToCode  code→code
     * @param codeToLabel code→label
     * @param labels      下拉 label 列表
     */
    private void collectLeafOptions(
        List<OptionDto> options,
        Map<String, String> labelToCode,
        Map<String, String> codeToCode,
        Map<String, String> codeToLabel,
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
                this.collectLeafOptions(option.getChildren(), labelToCode, codeToCode, codeToLabel, labels);
                continue;
            }
            this.collectOneOption(option, labelToCode, codeToCode, codeToLabel, labels, DICT_CATEGORY);
        }
    }

    /**
     * 平铺选项收集。
     *
     * @param options     术语选项
     * @param labelToCode label→code
     * @param codeToCode  code→code
     * @param codeToLabel code→label
     * @param labels      下拉 label 列表
     */
    private void collectFlatOptions(
        List<OptionDto> options,
        Map<String, String> labelToCode,
        Map<String, String> codeToCode,
        Map<String, String> codeToLabel,
        List<String> labels
    ) {
        if (options == null) {
            return;
        }
        for (OptionDto option : options) {
            this.collectOneOption(option, labelToCode, codeToCode, codeToLabel, labels, null);
        }
    }

    /**
     * 单条选项写入 label/code 双向映射。
     *
     * @param option         选项
     * @param labelToCode    label→code
     * @param codeToCode     code→code
     * @param codeToLabel    code→label
     * @param labels         下拉 label 列表
     * @param dictKeyForWarn 重复 label 告警用字典键；可空
     */
    private void collectOneOption(
        OptionDto option,
        Map<String, String> labelToCode,
        Map<String, String> codeToCode,
        Map<String, String> codeToLabel,
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
            codeToLabel.putIfAbsent(code, label);
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
                log.warn("药品目录导入术语 label 重复：dict={}, label={}, 保留 code={}, 忽略 code={}",
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
