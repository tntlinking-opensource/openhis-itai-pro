/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.application.catalog.mapping.support;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.positiveOrDefault;

import com.zxplat.plugin.chs.application.catalog.mapping.mapper.InsuranceCatalogMappingMapper;
import com.zxplat.plugin.chs.domain.catalog.command.SuggestMedicationCatalogMappingCommand;
import com.zxplat.plugin.chs.domain.catalog.entity.YbNationalCatalogItem;
import com.zxplat.plugin.chs.domain.catalog.result.CatalogMatchCandidateResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 药品与国家目录行匹配打分（仅推荐，不自动落库）。 */
public final class CatalogMedicationMatchScorer {

    private static final int SCORE_NATIONAL_CODE_EXACT = 80;
    private static final int SCORE_APRVNO_EXACT = 50;
    private static final int SCORE_APRVNO_CONTAINS = 30;
    private static final int SCORE_SPEC_EXACT = 25;
    private static final int SCORE_SPEC_CONTAINS = 15;
    private static final int SCORE_NAME_CONTAINS = 15;
    private static final int SCORE_PINYIN_CONTAINS = 12;
    private static final int SCORE_CODE_CONTAINS = 10;

    private CatalogMedicationMatchScorer() {
    }

    /**
     * 对候选池逐行打分、降序排序并截断至 maxCandidates。
     */
    public static List<CatalogMatchCandidateResult> scoreAndSort(
            List<YbNationalCatalogItem> pool,
            SuggestMedicationCatalogMappingCommand command,
            int maxCandidates) {
        List<CatalogMatchCandidateResult> scored = new ArrayList<>();
        for (YbNationalCatalogItem item : pool) {
            ScoreResult result = scoreOne(item, command);
            if (result.score() <= 0) {
                continue;
            }
            CatalogMatchCandidateResult candidate = InsuranceCatalogMappingMapper.toMatchCandidate(item);
            candidate.setMatchScore(result.score());
            candidate.setMatchReason(result.reason());
            scored.add(candidate);
        }
        scored.sort(Comparator
            .comparingInt(CatalogMatchCandidateResult::getMatchScore)
            .reversed()
            .thenComparing(CatalogMatchCandidateResult::getHilistCode, Comparator.nullsLast(String::compareTo)));
        int limit = positiveOrDefault(maxCandidates, 20);
        if (scored.size() <= limit) {
            return scored;
        }
        return new ArrayList<>(scored.subList(0, limit));
    }

    /**
     * 根据 Top1/Top2 分差判定推荐级别：UNIQUE_SUGGEST / MULTI_CHOICE / NO_MATCH。
     */
    public static String resolveRecommendLevel(List<CatalogMatchCandidateResult> candidates) {
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

    /** 按推荐级别生成面向用户的提示文案。 */
    public static String buildHint(String recommendLevel, List<CatalogMatchCandidateResult> candidates) {
        return switch (recommendLevel) {
            case "UNIQUE_SUGGEST" -> "系统找到一条高置信候选（得分 "
                + candidates.get(0).getMatchScore()
                + "），请核对后确认选用；不会自动保存。";
            case "NO_MATCH" -> "未找到可用候选（已排除平台停用/过期）。可调整关键字后再搜，"
                + "或先到「国家目录」下载对应镜像（西药/中成药 1301/1312，中药饮片 1302，"
                + "诊疗 1305/1312，耗材 1306/1312）后再试。";
            default -> {
                if (candidates == null || candidates.isEmpty()) {
                    yield "未找到可用候选，请人工检索或补库后再选。";
                }
                yield "存在多条相近候选（最高得分 " + candidates.get(0).getMatchScore()
                    + "），请人工选择正确医保编码。";
            }
        };
    }

    private static ScoreResult scoreOne(
            YbNationalCatalogItem item,
            SuggestMedicationCatalogMappingCommand command) {
        int score = 0;
        List<String> reasons = new ArrayList<>();
        String nationalCode = trim(command.getNationalDrugCode());
        if (isNotEmpty(nationalCode)
            && nationalCode.equalsIgnoreCase(trim(item.getHilistCode()))) {
            score += SCORE_NATIONAL_CODE_EXACT;
            reasons.add("贯标码一致");
        }
        String approval = trim(command.getApprovalNumber());
        String aprvno = trim(item.getAprvno());
        if (isNotEmpty(approval) && isNotEmpty(aprvno)) {
            if (equalsIgnoreCaseNormalized(approval, aprvno)) {
                score += SCORE_APRVNO_EXACT;
                reasons.add("批准文号一致");
            } else if (containsNormalized(aprvno, approval) || containsNormalized(approval, aprvno)) {
                score += SCORE_APRVNO_CONTAINS;
                reasons.add("批准文号相近");
            }
        }
        String spec = trim(command.getTotalVolume());
        String catalogSpec = trim(item.getSpec());
        if (isNotEmpty(spec) && isNotEmpty(catalogSpec)) {
            if (equalsIgnoreCaseNormalized(spec, catalogSpec)) {
                score += SCORE_SPEC_EXACT;
                reasons.add("规格一致");
            } else if (containsNormalized(catalogSpec, spec) || containsNormalized(spec, catalogSpec)) {
                score += SCORE_SPEC_CONTAINS;
                reasons.add("规格相近");
            }
        }
        String name = trim(command.getName());
        String catalogName = trim(item.getHilistName());
        if (isNotEmpty(name) && isNotEmpty(catalogName)
            && containsNormalized(catalogName, name)) {
            score += SCORE_NAME_CONTAINS;
            reasons.add("名称匹配");
        }
        String catalogPinyin = trim(item.getPinyin());
        if (isNotEmpty(name) && isNotEmpty(catalogPinyin)
            && containsNormalized(catalogPinyin, name)) {
            score += SCORE_PINYIN_CONTAINS;
            reasons.add("拼音匹配");
        }
        String catalogCode = trim(item.getHilistCode());
        // 名称框也可直接输医保码：精确命中等同贯标码，前缀/包含给编码分
        if (isNotEmpty(name) && isNotEmpty(catalogCode)) {
            if (name.equalsIgnoreCase(catalogCode)) {
                score += SCORE_NATIONAL_CODE_EXACT;
                reasons.add("医保编码一致");
            } else if (containsNormalized(catalogCode, name)) {
                score += SCORE_CODE_CONTAINS;
                reasons.add("编码匹配");
            }
        }
        if (score == 0 && isNotEmpty(name) && !isNotEmpty(approval) && !isNotEmpty(nationalCode)) {
            return new ScoreResult(0, "");
        }
        return new ScoreResult(score, String.join("；", reasons));
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
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
