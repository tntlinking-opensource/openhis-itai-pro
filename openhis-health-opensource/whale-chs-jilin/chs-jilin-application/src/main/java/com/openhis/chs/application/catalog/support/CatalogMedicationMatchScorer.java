/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.application.catalog.support;

import com.openhis.chs.domain.catalog.dto.request.SuggestMedicationCatalogMappingRequest;
import com.openhis.chs.domain.catalog.dto.response.CatalogMatchCandidateDto;
import com.openhis.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.openhis.chs.domain.support.ChsTextSupport;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 药品与国家目录行匹配打分（仅推荐，不自动落库）。
 */
public final class CatalogMedicationMatchScorer {

    private static final int SCORE_NATIONAL_CODE_EXACT = 80;
    private static final int SCORE_APRVNO_EXACT = 50;
    private static final int SCORE_APRVNO_CONTAINS = 30;
    private static final int SCORE_SPEC_EXACT = 25;
    private static final int SCORE_SPEC_CONTAINS = 15;
    private static final int SCORE_NAME_CONTAINS = 15;

    private CatalogMedicationMatchScorer() {
    }

    public static List<CatalogMatchCandidateDto> scoreAndSort(
            List<YbNationalCatalogItem> pool,
            SuggestMedicationCatalogMappingRequest request,
            int maxCandidates) {
        List<CatalogMatchCandidateDto> scored = new ArrayList<>();
        for (YbNationalCatalogItem item : pool) {
            ScoreResult result = scoreOne(item, request);
            if (result.score() <= 0) {
                continue;
            }
            CatalogMatchCandidateDto dto = toCandidate(item);
            dto.setMatchScore(result.score());
            dto.setMatchReason(result.reason());
            scored.add(dto);
        }
        scored.sort(Comparator
            .comparingInt(CatalogMatchCandidateDto::getMatchScore)
            .reversed()
            .thenComparing(CatalogMatchCandidateDto::getHilistCode, Comparator.nullsLast(String::compareTo)));
        int limit = ChsTextSupport.positiveOrDefault(maxCandidates, 20);
        if (scored.size() <= limit) {
            return scored;
        }
        return new ArrayList<>(scored.subList(0, limit));
    }

    public static String resolveRecommendLevel(List<CatalogMatchCandidateDto> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return "NO_MATCH";
        }
        int top = candidates.get(0).getMatchScore();
        int second = 0;
        if (candidates.size() > 1) {
            second = candidates.get(1).getMatchScore();
        }
        if (top >= 80 && top - second >= 20) {
            return "UNIQUE_SUGGEST";
        }
        return "MULTI_CHOICE";
    }

    public static String buildHint(String recommendLevel, List<CatalogMatchCandidateDto> candidates) {
        return switch (recommendLevel) {
            case "UNIQUE_SUGGEST" -> "系统找到一条高置信候选（得分 "
                + candidates.get(0).getMatchScore()
                + "），请核对后确认选用；不会自动保存。";
            case "NO_MATCH" -> "未找到可用候选。批准文号/规格需本地 1301 目录明细，请先执行 1301 下载后再试。";
            default -> "存在多条相近候选（最高得分 " + candidates.get(0).getMatchScore()
                + "），请人工选择正确医保编码。";
        };
    }

    private static ScoreResult scoreOne(
            YbNationalCatalogItem item,
            SuggestMedicationCatalogMappingRequest request) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        String nationalCode = trim(request.getNationalDrugCode());
        if (StringUtils.isNotBlank(nationalCode)
            && nationalCode.equalsIgnoreCase(trim(item.getHilistCode()))) {
            score += SCORE_NATIONAL_CODE_EXACT;
            reasons.add("贯标码一致");
        }
        String approval = trim(request.getApprovalNumber());
        String aprvno = trim(item.getAprvno());
        if (StringUtils.isNotBlank(approval) && StringUtils.isNotBlank(aprvno)) {
            if (equalsIgnoreCaseNormalized(approval, aprvno)) {
                score += SCORE_APRVNO_EXACT;
                reasons.add("批准文号一致");
            } else if (containsNormalized(aprvno, approval) || containsNormalized(approval, aprvno)) {
                score += SCORE_APRVNO_CONTAINS;
                reasons.add("批准文号相近");
            }
        }
        String spec = trim(request.getTotalVolume());
        String catalogSpec = trim(item.getSpec());
        if (StringUtils.isNotBlank(spec) && StringUtils.isNotBlank(catalogSpec)) {
            if (equalsIgnoreCaseNormalized(spec, catalogSpec)) {
                score += SCORE_SPEC_EXACT;
                reasons.add("规格一致");
            } else if (containsNormalized(catalogSpec, spec) || containsNormalized(spec, catalogSpec)) {
                score += SCORE_SPEC_CONTAINS;
                reasons.add("规格相近");
            }
        }
        String name = trim(request.getName());
        String catalogName = trim(item.getHilistName());
        if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(catalogName)
            && containsNormalized(catalogName, name)) {
            score += SCORE_NAME_CONTAINS;
            reasons.add("名称匹配");
        }
        if (score == 0 && StringUtils.isNotBlank(name) && StringUtils.isBlank(approval)
            && StringUtils.isBlank(nationalCode)) {
            return new ScoreResult(0, "");
        }
        return new ScoreResult(score, String.join("；", reasons));
    }

    private static CatalogMatchCandidateDto toCandidate(YbNationalCatalogItem item) {
        CatalogMatchCandidateDto dto = new CatalogMatchCandidateDto();
        dto.setId(item.getId());
        dto.setContractId(item.getContractId());
        dto.setListType(item.getListType());
        dto.setHilistCode(item.getHilistCode());
        dto.setHilistName(item.getHilistName());
        dto.setChrgitmLv(item.getChrgitmLv());
        dto.setLmtUsedFlag(item.getLmtUsedFlag());
        dto.setBegndate(item.getBegndate());
        dto.setEnddate(item.getEnddate());
        dto.setValiFlag(item.getValiFlag());
        dto.setPinyin(item.getPinyin());
        dto.setSpec(item.getSpec());
        dto.setDosform(item.getDosform());
        dto.setAprvno(item.getAprvno());
        dto.setProdentpName(item.getProdentpName());
        dto.setSourceInfno(item.getSourceInfno());
        dto.setUpdtTime(item.getUpdtTime());
        return dto;
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }

    private static boolean equalsIgnoreCaseNormalized(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private static boolean containsNormalized(String haystack, String needle) {
        return normalize(haystack).contains(normalize(needle));
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record ScoreResult(int score, String reason) {
    }
}
