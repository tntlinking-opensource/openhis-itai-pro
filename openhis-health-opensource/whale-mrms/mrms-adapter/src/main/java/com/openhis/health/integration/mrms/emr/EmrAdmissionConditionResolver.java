/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.emr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
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
 * 从 EMR 文书结构化内容解析「诊断编码 → 入院病情码」映射。
 *
 * <p>支持 Tiptap JSON 中 JSON 数组、命名表格单元格、{@code emrDataElement}/{@code dataElement} 数据项。</p>
 */
@Component
@RequiredArgsConstructor
public class EmrAdmissionConditionResolver {

    private static final String DOC_TYPE_ADMISSION = "ADMISSION_RECORD";
    private static final String DOC_TYPE_DISCHARGE = "DISCHARGE_SUMMARY";

    private final DocumentRepository documentRepository;
    private final EncounterQueryService encounterQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 按就诊解析入院病情（ICD 编码 → adm_cond_type / adm_cond_code）。
     *
     * @param encounterId 就诊主键
     * @return 诊断编码（大写）→ 入院病情码；无数据时空 Map
     */
    public Map<String, String> resolveByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Map.of();
        }
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(encounterId).orElse(null);
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

    private List<Document> loadCandidateDocuments(Long patientId, Long encounterId) {
        String patientKey = String.valueOf(patientId);
        String encounterKey = String.valueOf(encounterId);
        LinkedHashSet<Document> ordered = new LinkedHashSet<>();
        appendDocuments(ordered, documentRepository.findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
                patientKey, encounterKey, DOC_TYPE_ADMISSION));
        appendDocuments(ordered, documentRepository.findByPatientIdAndEncounterIdAndDocumentTypeCodeAndIsDeletedFalse(
                patientKey, encounterKey, DOC_TYPE_DISCHARGE));
        appendDocuments(ordered, documentRepository.findByPatientIdAndEncounterIdAndIsDeletedFalseOrderByUpdatedAtDesc(
                patientKey, encounterKey));
        if (ordered.isEmpty()) {
            appendDocuments(ordered, findDocumentsByEncounterOnly(encounterKey));
        }
        return new ArrayList<>(ordered);
    }

    private List<Document> findDocumentsByEncounterOnly(String encounterKey) {
        Specification<Document> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("encounterId"), encounterKey));
            predicates.add(cb.equal(root.get("isDeleted"), false));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return documentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private void appendDocuments(Set<Document> target, List<Document> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        target.addAll(source);
    }

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
            return result;
        }
        return result;
    }

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

    private void collectFromNode(JsonNode node, Map<String, String> result) {
        String type = textOrNull(node.get("type"));
        JsonNode attrs = node.get("attrs");
        if (attrs == null || !attrs.isObject()) {
            return;
        }
        if ("table".equals(type)) {
            collectFromTable(node, result);
            return;
        }
        if ("emrDataElement".equals(type) || "dataElement".equals(type)) {
            collectFromDataElement(attrs, result);
        }
        JsonNode valueNode = attrs.get("value");
        if (valueNode != null) {
            collectFromValueNode(valueNode, result);
        }
    }

    private void collectFromTable(JsonNode tableNode, Map<String, String> result) {
        JsonNode attrs = tableNode.get("attrs");
        String tableName = null;
        if (attrs != null && attrs.isObject()) {
            tableName = textOrNull(attrs.get("name"));
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

    private void putPair(String diagCode, String admCond, Map<String, String> result) {
        String codeKey = normalizeDiagCode(diagCode);
        String cond = trimToNull(admCond);
        if (codeKey == null || cond == null) {
            return;
        }
        result.putIfAbsent(codeKey, cond);
    }

    private void mergeDiagnosisAdmCond(Map<String, String> target, Map<String, String> source) {
        for (Map.Entry<String, String> entry : source.entrySet()) {
            target.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private boolean isDiagnosisTableName(String tableName) {
        String key = normalizeKey(tableName);
        if (key.contains("diagnosis") || key.contains("diaglist")) {
            return true;
        }
        return key.contains("admissiondiag");
    }

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

    private String textOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber()) {
            return trimToNull(node.asText());
        }
        return null;
    }

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

    private String normalizeKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    private String normalizeDiagCode(String raw) {
        String code = trimToNull(raw);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }
}
