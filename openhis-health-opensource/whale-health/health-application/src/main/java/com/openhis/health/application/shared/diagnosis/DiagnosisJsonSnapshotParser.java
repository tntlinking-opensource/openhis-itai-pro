/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.utils.DiagnosisKindPresentationUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 解析读模型 {@code diagnosisJson} 快照数组。
 * <p>供 {@code module.shared.diagnosis} 展示路径（宽松 {@link #parse}）与医生站 Mapper（严格 {@link #parseStrict}）共用；
 * 字段键名与 {@link com.openhis.health.domain.share.constants.FieldNameConstants}、
 * {@link com.openhis.health.domain.share.constants.DoctorStationDiagnosisConstants} 双端契约一致。</p>
 */
@Component
@RequiredArgsConstructor
public class DiagnosisJsonSnapshotParser {

    /** Jackson 解析器：读取 diagnosisJson 数组。 */
    private final ObjectMapper objectMapper;

    /**
     * 宽松解析：JSON 非法或根非数组时返回空列表（护士站展示等读路径）。
     *
     * @param diagnosisJson 诊断快照 JSON 数组字符串
     * @return 解析行列表；空/非法时返回空列表
     */
    public List<ParsedDiagnosisRow> parse(String diagnosisJson) {
        try {
            return parseInternal(diagnosisJson);
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    /**
     * 严格解析：JSON 非法时抛出 {@link BusinessException}（医生站 IF 契约）。
     *
     * @param diagnosisJson 诊断快照 JSON 数组字符串
     * @return 解析行列表
     * @throws BusinessException JSON 非法或解析失败
     */
    public List<ParsedDiagnosisRow> parseStrict(String diagnosisJson) {
        try {
            return parseInternal(diagnosisJson);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "诊断快照解析失败，请稍后重试");
        }
    }

    /**
     * 诊断快照 JSON 数组解析内核：逐元素映射字段并 enrich 展示回落。
     *
     * @param diagnosisJson 诊断快照 JSON 数组字符串
     * @return 解析行列表；空串或非数组时空列表
     * @throws Exception Jackson 解析异常等
     */
    private List<ParsedDiagnosisRow> parseInternal(String diagnosisJson) throws Exception {
        if (StringUtils.isEmpty(diagnosisJson)) {
            return Collections.emptyList();
        }
        JsonNode root = objectMapper.readTree(diagnosisJson);
        if (!root.isArray()) {
            return Collections.emptyList();
        }
        List<ParsedDiagnosisRow> result = new ArrayList<>();
        for (JsonNode node : root) {
            ParsedDiagnosisRow row = parseNode(node);
            enrichDisplayFields(row);
            result.add(row);
        }
        return result;
    }

    /**
     * 将快照 JSON 单节点映射为 {@link ParsedDiagnosisRow}（字段键与医生站契约一致）。
     *
     * @param node 数组元素节点
     * @return 解析行（未 enrich 展示回落）
     */
    private static ParsedDiagnosisRow parseNode(JsonNode node) {
        ParsedDiagnosisRow row = new ParsedDiagnosisRow();
        if (node.hasNonNull(FieldNameConstants.CONDITION_ID)) {
            row.setConditionId(node.get(FieldNameConstants.CONDITION_ID).asLong());
        }
        if (node.hasNonNull(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID)) {
            row.setEncounterDiagnosisId(node.get(FieldNameConstants.ENCOUNTER_DIAGNOSIS_ID).asLong());
        }
        if (node.hasNonNull(FieldNameConstants.CONDITION_DEFINITION_ID)) {
            row.setConditionDefinitionId(node.get(FieldNameConstants.CONDITION_DEFINITION_ID).asLong());
        }
        if (node.hasNonNull(FieldNameConstants.CONDITION_CODE)) {
            row.setConditionCode(node.get(FieldNameConstants.CONDITION_CODE).asText());
        }
        if (node.hasNonNull(FieldNameConstants.CONDITION_DEFINITION_NAME)) {
            row.setConditionDefinitionName(node.get(FieldNameConstants.CONDITION_DEFINITION_NAME).asText());
        }
        if (node.hasNonNull(FieldNameConstants.DIAGNOSIS_KIND)) {
            row.setDiagnosisKind(node.get(FieldNameConstants.DIAGNOSIS_KIND).asText());
        }
        if (node.hasNonNull(FieldNameConstants.DIAGNOSIS_KIND_TEXT)) {
            row.setDiagnosisKindText(node.get(FieldNameConstants.DIAGNOSIS_KIND_TEXT).asText());
        }
        if (StringUtils.isEmpty(row.getDiagnosisKindText())) {
            row.setDiagnosisKindText(DiagnosisKindPresentationUtil.toDisplay(row.getDiagnosisKind()));
        }
        if (node.hasNonNull(FieldNameConstants.VERIFICATION_STATUS_ENUM)
                && node.get(FieldNameConstants.VERIFICATION_STATUS_ENUM).isInt()) {
            row.setVerificationStatusEnum(node.get(FieldNameConstants.VERIFICATION_STATUS_ENUM).asInt());
        }
        if (node.hasNonNull(FieldNameConstants.VERIFICATION_STATUS_ENUM_TEXT)) {
            row.setVerificationStatusEnumText(node.get(FieldNameConstants.VERIFICATION_STATUS_ENUM_TEXT).asText());
        }
        if (node.hasNonNull(FieldNameConstants.MAIN_DIAGNOSIS_FLAG)) {
            row.setMainDiagnosisFlag(node.get(FieldNameConstants.MAIN_DIAGNOSIS_FLAG).asInt());
        }
        if (node.hasNonNull(FieldNameConstants.DIAGNOSIS_SORT_NO)) {
            row.setDiagnosisSortNo(node.get(FieldNameConstants.DIAGNOSIS_SORT_NO).asInt());
        }
        if (node.hasNonNull(FieldNameConstants.MEDICAL_TYPE_CODE)) {
            row.setMedicalTypeCode(node.get(FieldNameConstants.MEDICAL_TYPE_CODE).asText());
        }
        if (node.hasNonNull(FieldNameConstants.DIAGNOSIS_DESC)) {
            row.setDiagnosisDesc(node.get(FieldNameConstants.DIAGNOSIS_DESC).asText());
        }
        if (node.hasNonNull(FieldNameConstants.IPT_DISEASE_TYPE_CODE)) {
            row.setIptDiseaseTypeCode(node.get(FieldNameConstants.IPT_DISEASE_TYPE_CODE).asText());
        }
        if (node.hasNonNull(FieldNameConstants.YB_NO)) {
            row.setYbNo(node.get(FieldNameConstants.YB_NO).asText());
        }
        if (node.hasNonNull(FieldNameConstants.TCM_SYNDROME_GROUP)) {
            row.setTcmSyndromeGroup(node.get(FieldNameConstants.TCM_SYNDROME_GROUP).asText());
        }
        if (node.hasNonNull(FieldNameConstants.SOURCE_ENUM)) {
            row.setSourceEnum(node.get(FieldNameConstants.SOURCE_ENUM).asText());
        }
        return row;
    }

    /**
     * 解析后展示字段回落：住院疾病类型文案、验证状态 UI 文案。
     *
     * @param row 解析行（就地修改）
     */
    private static void enrichDisplayFields(ParsedDiagnosisRow row) {
        if (row == null) {
            return;
        }
        if (StringUtils.isNotEmpty(row.getIptDiseaseTypeCode())) {
            IptPatientDiseaseDiagType parsed =
                EnumUtil.fromCode(IptPatientDiseaseDiagType.class, row.getIptDiseaseTypeCode().trim());
            if (parsed != null) {
                row.setIptDiseaseTypeText(parsed.getDisplay());
            }
        }
        if (row.getVerificationStatusEnum() != null
                && StringUtils.isEmpty(row.getVerificationStatusEnumText())) {
            row.setVerificationStatusEnumText(verificationDisplayText(row.getVerificationStatusEnum()));
        }
    }

    /**
     * 医生站验证状态 UI 整数码 → 展示文案；未知码回落「已确认」。
     *
     * @param code {@link DoctorStationDiagnosisConstants.VerificationUiCode} 整数码
     * @return 验证状态展示文本
     */
    private static String verificationDisplayText(int code) {
        if (code == DoctorStationDiagnosisConstants.VerificationUiCode.UNCONFIRMED) {
            return "未确认";
        }
        if (code == DoctorStationDiagnosisConstants.VerificationUiCode.PROVISIONAL) {
            return "疑似";
        }
        if (code == DoctorStationDiagnosisConstants.VerificationUiCode.DIFFERENTIAL) {
            return "鉴别诊断";
        }
        if (code == DoctorStationDiagnosisConstants.VerificationUiCode.CONFIRMED) {
            return "已确认";
        }
        if (code == DoctorStationDiagnosisConstants.VerificationUiCode.REFUTED) {
            return "已反驳";
        }
        if (code == DoctorStationDiagnosisConstants.VerificationUiCode.ENTERED_IN_ERROR) {
            return "录入错误";
        }
        return "已确认";
    }

    /**
     * 诊断快照行（医生站/护士站读模型 JSON 数组元素）。
     */
    public static final class ParsedDiagnosisRow {

        /** 病情事实主键 {@code cln_condition.id}。 */
        private Long conditionId;

        /** 就诊诊断关联主键 {@code adm_encounter_diagnosis.id}。 */
        private Long encounterDiagnosisId;

        /** 诊断目录定义主键。 */
        private Long conditionDefinitionId;

        /** 诊断目录编码。 */
        private String conditionCode;

        /** 诊断目录展示名。 */
        private String conditionDefinitionName;

        /** 中西医维度标签 {@code WESTERN}/{@code TCM}。 */
        private String diagnosisKind;

        /** 中西医维度展示文本。 */
        private String diagnosisKindText;

        /** 验证状态 UI 整数码。 */
        private Integer verificationStatusEnum;

        /** 验证状态展示文本。 */
        private String verificationStatusEnumText;

        /** 主诊断标记（1=是，0=否）。 */
        private Integer mainDiagnosisFlag;

        /** 诊断排序号。 */
        private Integer diagnosisSortNo;

        /** 医疗类别码。 */
        private String medicalTypeCode;

        /** 诊断描述/补充说明。 */
        private String diagnosisDesc;

        /** 住院疾病类型码。 */
        private String iptDiseaseTypeCode;

        /** 住院疾病类型展示名。 */
        private String iptDiseaseTypeText;

        /** 医保目录编码。 */
        private String ybNo;

        /** 中医病证组号（快照 JSON 字段 {@code tcmSyndromeGroup}）。 */
        private String tcmSyndromeGroup;

        /** 诊断定义目录来源码（如 {@code tcm_dx}/{@code tcm_syndrome}）。 */
        private String sourceEnum;

        /** 获取病情事实主键。 */
        public Long getConditionId() {
            return conditionId;
        }

        /** 设置病情事实主键。 */
        public void setConditionId(Long conditionId) {
            this.conditionId = conditionId;
        }

        /** 获取就诊诊断关联主键。 */
        public Long getEncounterDiagnosisId() {
            return encounterDiagnosisId;
        }

        /** 设置就诊诊断关联主键。 */
        public void setEncounterDiagnosisId(Long encounterDiagnosisId) {
            this.encounterDiagnosisId = encounterDiagnosisId;
        }

        /** 获取诊断目录定义主键。 */
        public Long getConditionDefinitionId() {
            return conditionDefinitionId;
        }

        /** 设置诊断目录定义主键。 */
        public void setConditionDefinitionId(Long conditionDefinitionId) {
            this.conditionDefinitionId = conditionDefinitionId;
        }

        /** 获取诊断目录编码。 */
        public String getConditionCode() {
            return conditionCode;
        }

        /** 设置诊断目录编码。 */
        public void setConditionCode(String conditionCode) {
            this.conditionCode = conditionCode;
        }

        /** 获取诊断目录展示名。 */
        public String getConditionDefinitionName() {
            return conditionDefinitionName;
        }

        /** 设置诊断目录展示名。 */
        public void setConditionDefinitionName(String conditionDefinitionName) {
            this.conditionDefinitionName = conditionDefinitionName;
        }

        /** 获取中西医维度标签。 */
        public String getDiagnosisKind() {
            return diagnosisKind;
        }

        /** 设置中西医维度标签。 */
        public void setDiagnosisKind(String diagnosisKind) {
            this.diagnosisKind = diagnosisKind;
        }

        /** 获取中西医维度展示文本。 */
        public String getDiagnosisKindText() {
            return diagnosisKindText;
        }

        /** 设置中西医维度展示文本。 */
        public void setDiagnosisKindText(String diagnosisKindText) {
            this.diagnosisKindText = diagnosisKindText;
        }

        /** 获取验证状态 UI 整数码。 */
        public Integer getVerificationStatusEnum() {
            return verificationStatusEnum;
        }

        /** 设置验证状态 UI 整数码。 */
        public void setVerificationStatusEnum(Integer verificationStatusEnum) {
            this.verificationStatusEnum = verificationStatusEnum;
        }

        /** 获取验证状态展示文本。 */
        public String getVerificationStatusEnumText() {
            return verificationStatusEnumText;
        }

        /** 设置验证状态展示文本。 */
        public void setVerificationStatusEnumText(String verificationStatusEnumText) {
            this.verificationStatusEnumText = verificationStatusEnumText;
        }

        /** 获取主诊断标记。 */
        public Integer getMainDiagnosisFlag() {
            return mainDiagnosisFlag;
        }

        /** 设置主诊断标记。 */
        public void setMainDiagnosisFlag(Integer mainDiagnosisFlag) {
            this.mainDiagnosisFlag = mainDiagnosisFlag;
        }

        /** 获取诊断排序号。 */
        public Integer getDiagnosisSortNo() {
            return diagnosisSortNo;
        }

        /** 设置诊断排序号。 */
        public void setDiagnosisSortNo(Integer diagnosisSortNo) {
            this.diagnosisSortNo = diagnosisSortNo;
        }

        /** 获取医疗类别码。 */
        public String getMedicalTypeCode() {
            return medicalTypeCode;
        }

        /** 设置医疗类别码。 */
        public void setMedicalTypeCode(String medicalTypeCode) {
            this.medicalTypeCode = medicalTypeCode;
        }

        /** 获取诊断描述。 */
        public String getDiagnosisDesc() {
            return diagnosisDesc;
        }

        /** 设置诊断描述。 */
        public void setDiagnosisDesc(String diagnosisDesc) {
            this.diagnosisDesc = diagnosisDesc;
        }

        /** 获取住院疾病类型码。 */
        public String getIptDiseaseTypeCode() {
            return iptDiseaseTypeCode;
        }

        /** 设置住院疾病类型码。 */
        public void setIptDiseaseTypeCode(String iptDiseaseTypeCode) {
            this.iptDiseaseTypeCode = iptDiseaseTypeCode;
        }

        /** 获取住院疾病类型展示名。 */
        public String getIptDiseaseTypeText() {
            return iptDiseaseTypeText;
        }

        /** 设置住院疾病类型展示名。 */
        public void setIptDiseaseTypeText(String iptDiseaseTypeText) {
            this.iptDiseaseTypeText = iptDiseaseTypeText;
        }

        /** 获取医保目录编码。 */
        public String getYbNo() {
            return ybNo;
        }

        /** 设置医保目录编码。 */
        public void setYbNo(String ybNo) {
            this.ybNo = ybNo;
        }

        /** 获取中医病证组号。 */
        public String getTcmSyndromeGroup() {
            return tcmSyndromeGroup;
        }

        /** 设置中医病证组号。 */
        public void setTcmSyndromeGroup(String tcmSyndromeGroup) {
            this.tcmSyndromeGroup = tcmSyndromeGroup;
        }

        /** 获取诊断定义目录来源码。 */
        public String getSourceEnum() {
            return sourceEnum;
        }

        /** 设置诊断定义目录来源码。 */
        public void setSourceEnum(String sourceEnum) {
            this.sourceEnum = sourceEnum;
        }
    }
}
