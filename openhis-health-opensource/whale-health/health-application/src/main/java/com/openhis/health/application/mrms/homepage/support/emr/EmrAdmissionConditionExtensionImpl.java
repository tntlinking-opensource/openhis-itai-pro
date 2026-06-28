/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.mrms.homepage.support.emr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.share.constants.MrmsConstants;
import com.openhis.health.integration.emr.EmrAdmissionConditionExtension;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import jakarta.persistence.criteria.Predicate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link EmrAdmissionConditionExtension} 实现：从 EMR 文书结构化内容解析入院病情。
 *
 * <p>支持 Tiptap JSON 中 JSON 数组、命名表格单元格、{@code emrDataElement}/{@code dataElement} 数据项。</p>
 */
@ConditionalOnClass(name = "com.openhis.whale.emr.domain.entity.Document")
@Component
@Primary
@RequiredArgsConstructor
public class EmrAdmissionConditionExtensionImpl implements EmrAdmissionConditionExtension {

    private final DocumentRepository documentRepository;
    private final EncounterQueryService encounterQueryService;
    private final ObjectMapper objectMapper;

    /**
     * EMR classpath 已装配且本 Bean 已注册。
     *
     * @return 恒为 true
     */
    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * 按就诊解析入院病情（ICD 编码 → adm_cond_type / adm_cond_code）。
     *
     * @param encounterId 就诊主键
     * @return 诊断编码（大写）→ 入院病情码；无数据时空 Map
     */
    @Override
    public Map<String, String> resolveByEncounterId(Long encounterId) {
        // 就诊主键缺失时无法定位文书，返回空映射
        if (encounterId == null) {
            return Map.of();
        }
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(encounterId).orElse(null);
        // 就诊不存在或无 patientId 时无法按 patient 维度查文书
        if (encounter == null || encounter.getPatientId() == null) {
            return Map.of();
        }
        List<Document> documents = loadCandidateDocuments(encounter.getPatientId(), encounterId);
        Map<String, String> merged = new HashMap<>();
        for (Document document : documents) {
            mergeDiagnosisAdmCond(merged, parseDocumentContent(document.getContent()));
        }
        return merged;
    }

    /**
     * 按 patient+encounter 加载入院/出院/全部候选文书，patient 维度无结果时降级仅 encounter。
     *
     * @param patientId 患者主键
     * @param encounterId 就诊主键
     * @return 候选文书列表（去重保序）
     */
    private List<Document> loadCandidateDocuments(Long patientId, Long encounterId) {
        String patientKey = String.valueOf(patientId);
        String encounterKey = String.valueOf(encounterId);
        LinkedHashSet<Document> ordered = new LinkedHashSet<>();
        appendDocuments(ordered, documentRepository.findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
                patientKey, encounterKey, MrmsConstants.EMR_DOC_TYPE_ADMISSION_RECORD));
        appendDocuments(ordered, documentRepository.findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
                patientKey, encounterKey, MrmsConstants.EMR_DOC_TYPE_DISCHARGE_SUMMARY));
        appendDocuments(ordered, documentRepository.findByPatientIdAndEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                patientKey, encounterKey));
        // patient 维度无文书时降级仅按 encounterId 查
        if (ordered.isEmpty()) {
            appendDocuments(ordered, findDocumentsByEncounterOnly(encounterKey));
        }
        return new ArrayList<>(ordered);
    }

    /**
     * 仅按 encounterId 查文书（patient 维度无结果时的降级路径）。
     *
     * @param encounterKey 就诊主键字符串
     * @return 文书列表
     */
    private List<Document> findDocumentsByEncounterOnly(String encounterKey) {
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("encounterId"), encounterKey));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return documentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    /**
     * 将查询结果追加到有序集合（去重保序）。
     *
     * @param target 目标集合
     * @param source 待追加列表
     */
    private void appendDocuments(Set<Document> target, List<Document> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }

    /**
     * 解析 Tiptap JSON 文书内容为 diagCode → admCond 映射。
     *
     * @param contentJson 文书 content JSON
     * @return 诊断编码（大写）→ 入院病情码
     */
    private Map<String, String> parseDocumentContent(String contentJson) {
        Map<String, String> result = new HashMap<>();
        if (trimToNull(contentJson) == null) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (root == null || !root.isObject()) {
                return result;
            }
            JsonNode content = root.get("content");
            if (content != null && content.isArray()) {
                walkNodes(content, result);
            }
        } catch (Exception ignored) {
            // JSON 格式错误时返回已收集部分
            return result;
        }
        return result;
    }

    /**
     * 递归遍历 Tiptap 节点树，收集入院病情数据。
     *
     * @param nodes 节点数组
     * @param result 累积结果
     */
    private void walkNodes(JsonNode nodes, Map<String, String> result) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            collectFromNode(node, result);
            JsonNode childContent = node.get("content");
            if (childContent != null && childContent.isArray()) {
                walkNodes(childContent, result);
            }
        }
    }

    /**
     * 从单个 Tiptap 节点提取入院病情（表格/数据元/value 节点）。
     *
     * @param node 当前节点
     * @param result 累积结果
     */
    private void collectFromNode(JsonNode node, Map<String, String> result) {
        String type = textOrNull(node.get("type"));
        JsonNode attrs = node.get("attrs");
        if (attrs == null || !attrs.isObject()) {
            return;
        }
        // 命名表格：按行解析 diagCode + admCond
        if ("table".equals(type)) {
            collectFromTable(node, result);
            return;
        }
        // 数据元节点：elementId 含 admCond 关键字时解析
        if ("emrDataElement".equals(type) || "dataElement".equals(type)) {
            collectFromDataElement(attrs, result);
        }
        JsonNode valueNode = attrs.get("value");
        if (valueNode != null) {
            collectFromValueNode(valueNode, result);
        }
    }

    /**
     * 从命名表格解析诊断行（按 cell name 匹配 diagCode/admCond）。
     *
     * @param tableNode 表格节点
     * @param result 累积结果
     */
    private void collectFromTable(JsonNode tableNode, Map<String, String> result) {
        JsonNode attrs = tableNode.get("attrs");
        String tableName = null;
        if (attrs != null && attrs.isObject()) {
            tableName = textOrNull(attrs.get("name"));
            // 诊断表 attrs.value 可能直接携带 JSON 数组
            if (tableName != null && isDiagnosisTableName(tableName)) {
                collectFromValueNode(attrs.get("value"), result);
            }
        }
        JsonNode rows = tableNode.get("content");
        if (rows == null || !rows.isArray()) {
            return;
        }
        for (JsonNode row : rows) {
            if (row == null || !"tableRow".equals(textOrNull(row.get("type")))) {
                continue;
            }
            Map<String, String> rowCells = new HashMap<>();
            JsonNode cells = row.get("content");
            if (cells == null || !cells.isArray()) {
                continue;
            }
            for (JsonNode cell : cells) {
                if (cell == null || !cell.isObject()) {
                    continue;
                }
                JsonNode cellAttrs = cell.get("attrs");
                if (cellAttrs == null) {
                    continue;
                }
                String cellName = textOrNull(cellAttrs.get("name"));
                if (cellName == null) {
                    continue;
                }
                rowCells.put(normalizeKey(cellName), extractCellText(cell));
            }
            putRowIfComplete(rowCells, result);
        }
    }

    /**
     * 从 emrDataElement/dataElement 节点解析入院病情（elementId 含 admCond 关键字）。
     *
     * @param attrs 节点 attrs
     * @param result 累积结果
     */
    private void collectFromDataElement(JsonNode attrs, Map<String, String> result) {
        String elementId = textOrNull(attrs.get("elementId"));
        if (elementId == null) {
            elementId = textOrNull(attrs.get("name"));
        }
        if (elementId == null) {
            return;
        }
        String normalizedId = normalizeKey(elementId);
        if (!normalizedId.contains("admcond") && !normalizedId.contains("admissioncondition")) {
            return;
        }
        String value = textOrNull(attrs.get("value"));
        if (value == null) {
            return;
        }
        if (value.startsWith("[") || value.startsWith("{")) {
            try {
                JsonNode parsed = objectMapper.readTree(value);
                collectFromValueNode(parsed, result);
            } catch (Exception ignored) {
                // 单值 dataElement 无法关联 diagCode，忽略
            }
        }
    }

    /**
     * 从 value 节点（数组/嵌套 JSON 文本）解析诊断行。
     *
     * @param valueNode value 节点
     * @param result 累积结果
     */
    private void collectFromValueNode(JsonNode valueNode, Map<String, String> result) {
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        if (valueNode.isArray()) {
            for (JsonNode row : valueNode) {
                putDiagnosisRow(row, result);
            }
            return;
        }
        if (valueNode.isTextual()) {
            String text = trimToNull(valueNode.asText());
            if (text == null) {
                return;
            }
            if (text.startsWith("[") || text.startsWith("{")) {
                try {
                    collectFromValueNode(objectMapper.readTree(text), result);
                } catch (Exception ignored) {
                    // ignore malformed embedded json
                }
            }
        }
    }

    /**
     * 从 JSON 对象行写入 diagCode → admCond（多字段名兼容）。
     *
     * @param row JSON 行对象
     * @param result 累积结果
     */
    private void putDiagnosisRow(JsonNode row, Map<String, String> result) {
        if (row == null || !row.isObject()) {
            return;
        }
        String diagCode = firstNonBlank(
                textOrNull(row.get("diagCode")),
                textOrNull(row.get("conditionCode")),
                textOrNull(row.get("icdCode")),
                textOrNull(row.get("diagnosisCode")));
        String admCond = firstNonBlank(
                textOrNull(row.get("admCondCode")),
                textOrNull(row.get("admCond")),
                textOrNull(row.get("adm_cond_code")),
                textOrNull(row.get("admCondType")),
                textOrNull(row.get("adm_cond_type")),
                textOrNull(row.get("admissionConditionCode")));
        putPair(diagCode, admCond, result);
    }

    /**
     * 表格行 cell 映射完整时写入 diagCode → admCond。
     *
     * @param rowCells 归一化 cell name → 文本
     * @param result 累积结果
     */
    private void putRowIfComplete(Map<String, String> rowCells, Map<String, String> result) {
        String diagCode = firstNonBlank(
                rowCells.get("diagcode"),
                rowCells.get("icdcode"),
                rowCells.get("conditioncode"),
                rowCells.get("diagnosiscode"));
        String admCond = firstNonBlank(
                rowCells.get("admcondcode"),
                rowCells.get("admcond"),
                rowCells.get("admcondtype"),
                rowCells.get("admissionconditioncode"));
        putPair(diagCode, admCond, result);
    }

    /**
     * 写入单对 diagCode → admCond（putIfAbsent，先出现的优先）。
     *
     * @param diagCode 诊断编码
     * @param admCond 入院病情码
     * @param result 累积结果
     */
    private void putPair(String diagCode, String admCond, Map<String, String> result) {
        String codeKey = normalizeDiagCode(diagCode);
        String cond = trimToNull(admCond);
        if (codeKey == null || cond == null) {
            return;
        }
        result.putIfAbsent(codeKey, cond);
    }

    /**
     * 合并 source 到 target（putIfAbsent，保留先解析到的值）。
     *
     * @param target 目标映射
     * @param source 待合并映射
     */
    private void mergeDiagnosisAdmCond(Map<String, String> target, Map<String, String> source) {
        for (Map.Entry<String, String> entry : source.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 判断表格名称是否为诊断列表表（diagnosis/diaglist/admissiondiag）。
     *
     * @param tableName 表格 attrs.name
     * @return 是诊断表则 true
     */
    private boolean isDiagnosisTableName(String tableName) {
        String key = normalizeKey(tableName);
        if (key.contains("diagnosis") || key.contains("diaglist")) {
            return true;
        }
        return key.contains("admissiondiag");
    }

    /**
     * 提取表格单元格内联文本（拼接 text 节点）。
     *
     * @param cell 单元格节点
     * @return trim 后文本
     */
    private String extractCellText(JsonNode cell) {
        StringBuilder sb = new StringBuilder();
        JsonNode blocks = cell.get("content");
        if (blocks == null || !blocks.isArray()) {
            return "";
        }
        for (JsonNode block : blocks) {
            JsonNode inlines = block.get("content");
            if (inlines == null || !inlines.isArray()) {
                continue;
            }
            for (JsonNode inline : inlines) {
                if ("text".equals(textOrNull(inline.get("type")))) {
                    sb.append(inline.path("text").asText(""));
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * JsonNode 转字符串；非文本/数字节点返回 null。
     *
     * @param node JSON 节点
     * @return trim 后文本或 null
     */
    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            return trimToNull(node.asText());
        }
        return null;
    }

    /**
     * 返回首个非空白字符串。
     *
     * @param values 候选值
     * @return 首个非 null trimmed 值，或 null
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    /**
     * 归一化字段名/表格名（小写、去下划线/连字符）。
     *
     * @param raw 原始名称
     * @return 归一化键
     */
    private String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /**
     * 诊断编码归一化（trim + 大写，作为 Map 键）。
     *
     * @param raw 原始编码
     * @return 大写编码或 null
     */
    private String normalizeDiagCode(String raw) {
        String code = trimToNull(raw);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }
}
