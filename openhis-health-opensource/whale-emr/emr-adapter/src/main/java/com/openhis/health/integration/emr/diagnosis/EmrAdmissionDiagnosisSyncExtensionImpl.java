/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.emr.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.integration.emr.EmrAdmissionDiagnosisSyncExtension;
import com.openhis.whale.emr.domain.entity.Document;
import com.openhis.whale.emr.domain.service.DocumentManager;
import com.openhis.whale.emr.domain.service.DocumentStructureManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 住院诊断变更 → 入院记录 {@code admissionDiagnosisStructured} JSON 回写。
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
@ConditionalOnProperty(name = "openhis.modules.emr.enabled", havingValue = "true")
public class EmrAdmissionDiagnosisSyncExtensionImpl implements EmrAdmissionDiagnosisSyncExtension {

    static final String DOCUMENT_TYPE_ADMISSION = "ADMISSION_RECORD";
    static final String STRUCTURED_DATA_ITEM = "admissionDiagnosisStructured";

    private final DocumentManager documentManager;
    private final DocumentStructureManager documentStructureManager;
    private final EncounterQueryService encounterQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public void syncAfterInpatientDiagnosisChanged(Long encounterId, Long patientId) {
        if (encounterId == null || patientId == null) {
            return;
        }
        Optional<Document> documentOpt = documentManager.findLatestByPatientAndEncounterAndType(
                String.valueOf(patientId),
                String.valueOf(encounterId),
                DOCUMENT_TYPE_ADMISSION);
        if (documentOpt.isEmpty()) {
            return;
        }
        Document document = documentOpt.get();
        Map<String, String> preservedAdmCond = parseExistingAdmCondMap(document.getContent());
        String structuredJson = buildStructuredJson(encounterId, preservedAdmCond);
        String newContent = documentStructureManager.setValuesByNames(
                document.getContent(),
                Map.of(STRUCTURED_DATA_ITEM, structuredJson));
        documentManager.saveContent(document.getId(), newContent, false);
    }

    private String buildStructuredJson(Long encounterId, Map<String, String> preservedAdmCond) {
        List<EncounterDiagnosisQueryModel> associations = new ArrayList<>(
                encounterQueryService.listEncounterDiagnoses(encounterId));
        if (associations.isEmpty()) {
            return "[]";
        }
        associations.sort(Comparator
                .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Long> conditionIds = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : associations) {
            if (row.getConditionId() != null) {
                conditionIds.add(row.getConditionId());
            }
        }
        if (conditionIds.isEmpty()) {
            return "[]";
        }

        Map<Long, ConditionBatchItemQueryModel> conditionById = new HashMap<>();
        for (ConditionBatchItemQueryModel item : conditionQueryService.getConditionListById(conditionIds)) {
            conditionById.put(item.getId(), item);
        }
        Set<Long> definitionIds = new LinkedHashSet<>();
        for (ConditionBatchItemQueryModel item : conditionById.values()) {
            if (item.getDefinitionId() != null) {
                definitionIds.add(item.getDefinitionId());
            }
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> definitionById = new HashMap<>();
        if (!definitionIds.isEmpty()) {
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(definitionIds))) {
                definitionById.put(def.getId(), def);
            }
        }

        ArrayNode rows = objectMapper.createArrayNode();
        for (EncounterDiagnosisQueryModel association : associations) {
            ConditionBatchItemQueryModel condition = conditionById.get(association.getConditionId());
            if (condition == null || condition.getDefinitionId() == null) {
                continue;
            }
            ConditionDefinitionBatchItemQueryModel definition = definitionById.get(condition.getDefinitionId());
            if (definition == null) {
                continue;
            }
            String diagCode = trimToNull(definition.getConditionCode());
            if (diagCode == null) {
                continue;
            }
            ObjectNode row = objectMapper.createObjectNode();
            row.put("diagCode", diagCode);
            if (trimToNull(definition.getName()) != null) {
                row.put("diagName", definition.getName().trim());
            }
            String admCond = preservedAdmCond.get(normalizeDiagCode(diagCode));
            if (admCond != null) {
                row.put("admCondCode", admCond);
            }
            rows.add(row);
        }
        try {
            return objectMapper.writeValueAsString(rows);
        } catch (Exception ex) {
            log.warn("serialize admissionDiagnosisStructured failed encounterId={}", encounterId, ex);
            return "[]";
        }
    }

    private Map<String, String> parseExistingAdmCondMap(String contentJson) {
        Map<String, String> result = new HashMap<>();
        if (trimToNull(contentJson) == null) {
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            JsonNode content = root != null ? root.get("content") : null;
            if (content != null && content.isArray()) {
                walkAndCollectAdmCond(content, result);
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    private void walkAndCollectAdmCond(JsonNode nodes, Map<String, String> result) {
        for (JsonNode node : nodes) {
            if (node == null || !node.isObject()) {
                continue;
            }
            JsonNode attrs = node.get("attrs");
            if (attrs != null && attrs.isObject()) {
                JsonNode valueNode = attrs.get("value");
                collectAdmCondFromValueNode(valueNode, result);
            }
            JsonNode childContent = node.get("content");
            if (childContent != null && childContent.isArray()) {
                walkAndCollectAdmCond(childContent, result);
            }
        }
    }

    private void collectAdmCondFromValueNode(JsonNode valueNode, Map<String, String> result) {
        if (valueNode == null || valueNode.isNull()) {
            return;
        }
        if (valueNode.isArray()) {
            for (JsonNode row : valueNode) {
                putAdmCondRow(row, result);
            }
            return;
        }
        if (valueNode.isTextual()) {
            String text = trimToNull(valueNode.asText());
            if (text != null && (text.startsWith("[") || text.startsWith("{"))) {
                try {
                    collectAdmCondFromValueNode(objectMapper.readTree(text), result);
                } catch (Exception ignored) {
                    // ignore malformed embedded json
                }
            }
        }
    }

    private void putAdmCondRow(JsonNode row, Map<String, String> result) {
        if (row == null || !row.isObject()) {
            return;
        }
        String diagCode = firstNonBlank(
                textOrNull(row.get("diagCode")),
                textOrNull(row.get("conditionCode")),
                textOrNull(row.get("icdCode")));
        String admCond = firstNonBlank(
                textOrNull(row.get("admCondCode")),
                textOrNull(row.get("admCond")),
                textOrNull(row.get("adm_cond_code")));
        String codeKey = normalizeDiagCode(diagCode);
        if (codeKey != null && trimToNull(admCond) != null) {
            result.putIfAbsent(codeKey, admCond.trim());
        }
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

    private String normalizeDiagCode(String raw) {
        String code = trimToNull(raw);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }
}
