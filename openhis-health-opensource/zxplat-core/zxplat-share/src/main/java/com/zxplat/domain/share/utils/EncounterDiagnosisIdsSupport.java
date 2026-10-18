/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 就诊诊断多值有序 ids 串（{@code encounter_diagnosis_ids}）解析与集合比较协议。
 *
 * <p>存储有序：首位为主诊行 id（便于统计/展示打 maindiag_flag）；门诊分方同方用
 * {@link #compatibleForSamePrescription(String, String)}（有交集即可，不必全等）；
 * {@link #toDiagnosisSetKey(String)} 仅作规范化键。结算 2203 上传<strong>全部</strong>绑定诊断，
 * 不以首位截断。禁止对原始 ids 串直接 {@code equals}（见 DD-诊断绑定改造 §4 / §7）。</p>
 */
public final class EncounterDiagnosisIdsSupport {

    /** ids 串分隔符（英文逗号）。 */
    public static final String IDS_SEPARATOR = ",";

    private EncounterDiagnosisIdsSupport() {
    }

    /**
     * 解析有序 ids 串为主键列表（保留重复与顺序；空段丢弃）。
     *
     * @param idsText 逗号分隔主键串；可空
     * @return 解析后的 id 列表；入参空时返回空列表
     * @throws NumberFormatException 某段无法解析为 Long 时抛出
     */
    public static List<Long> parseOrderedIds(String idsText) {
        return parseOrderedIds(idsText, false);
    }

    /**
     * 解析有序 ids 串；{@code lenient=true} 时跳过非法段（读侧/分方收集用）。
     *
     * @param idsText 逗号分隔主键串
     * @param lenient 是否跳过非法段
     * @return id 列表
     */
    public static List<Long> parseOrderedIds(String idsText, boolean lenient) {
        String normalized = trimToNull(idsText);
        if (normalized == null) {
            return List.of();
        }
        String[] parts = normalized.split(IDS_SEPARATOR, -1);
        List<Long> ids = new ArrayList<>(parts.length);
        for (String part : parts) {
            String token = trimToNull(part);
            if (token == null) {
                continue;
            }
            try {
                ids.add(Long.valueOf(token));
            } catch (NumberFormatException ex) {
                if (!lenient) {
                    throw ex;
                }
            }
        }
        return ids;
    }

    /**
     * 解析为去重集合（保留首次出现顺序）。
     *
     * @param idsText 逗号分隔主键串；可空
     * @return 去重后的有序 id 列表
     */
    public static List<Long> parseUniqueOrderedIds(String idsText) {
        return parseUniqueOrderedIds(idsText, false);
    }

    /**
     * 解析为去重集合（保留首次出现顺序）。
     *
     * @param idsText 逗号分隔主键串；可空
     * @param lenient 是否跳过非法段
     * @return 去重后的有序 id 列表
     */
    public static List<Long> parseUniqueOrderedIds(String idsText, boolean lenient) {
        List<Long> ordered = parseOrderedIds(idsText, lenient);
        if (ordered.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(ordered.size());
        unique.addAll(ordered);
        return new ArrayList<>(unique);
    }

    /**
     * 分方/相等比较用诊断集键：去重 → 升序 → join。
     *
     * <p>同方判定已改为「诊断集有交集即可」（见
     * {@link #compatibleForSamePrescription(String, String)}）；本键仍可用于调试/落库对照，
     * 禁止再当作硬分区 equals。</p>
     *
     * @param idsText 落库或入参 ids 串
     * @return canonical key；空输入返回空串
     */
    public static String toDiagnosisSetKey(String idsText) {
        List<Long> unique = parseUniqueOrderedIds(idsText, true);
        if (unique.isEmpty()) {
            return "";
        }
        List<Long> sorted = new ArrayList<>(unique);
        Collections.sort(sorted);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(IDS_SEPARATOR);
            }
            sb.append(sorted.get(i));
        }
        return sb.toString();
    }

    /**
     * 门诊分方同方诊断相容：两侧均未绑诊断时相容；仅一侧有绑定时不相容；
     * 两侧均有绑定时须至少共享一个就诊诊断行 id（有交集即可，不必集合全等）。
     *
     * @param idsTextA 药品行 A 的 {@code encounter_diagnosis_ids}
     * @param idsTextB 药品行 B 的 {@code encounter_diagnosis_ids}
     * @return 可同方时 true
     */
    public static boolean compatibleForSamePrescription(String idsTextA, String idsTextB) {
        Set<Long> setA = toIdSetLenient(idsTextA);
        Set<Long> setB = toIdSetLenient(idsTextB);
        // 均未绑诊断：不因诊断维度拆方
        if (setA.isEmpty() && setB.isEmpty()) {
            return true;
        }
        // 一侧有诊断、一侧无：无法「对上」任一诊断
        if (setA.isEmpty() || setB.isEmpty()) {
            return false;
        }
        for (Long id : setA) {
            if (setB.contains(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 多个 ids 串做有序并集（去重保首次出现顺序），再拼回落库口径串。
     *
     * <p>供处方方头诊断「全集」展示：同 {@code prescription_no} 下各药行绑定诊断取并集。</p>
     *
     * @param idsTexts 多行 {@code encounter_diagnosis_ids}；可含 null/空
     * @return 并集 ids 串；全空时返回 null
     */
    public static String unionOrderedIdsTexts(Iterable<String> idsTexts) {
        if (idsTexts == null) {
            return null;
        }
        LinkedHashSet<Long> union = new LinkedHashSet<>();
        for (String idsText : idsTexts) {
            List<Long> ordered = parseUniqueOrderedIds(idsText, true);
            union.addAll(ordered);
        }
        if (union.isEmpty()) {
            return null;
        }
        return joinOrderedIds(new ArrayList<>(union));
    }

    /**
     * 宽松解析为 Set（非法段跳过），供分方相容判定。
     *
     * @param idsText 原始 ids 串
     * @return 去重集合；空输入返回空集
     */
    public static Set<Long> toIdSetLenient(String idsText) {
        return new LinkedHashSet<>(parseUniqueOrderedIds(idsText, true));
    }

    /**
     * 判断 ids 串是否引用指定就诊诊断行 id（精确 token，避免 {@code 12} 误匹配 {@code 112}）。
     *
     * @param idsText              原始 ids 串
     * @param encounterDiagnosisId 就诊诊断行主键
     * @return 引用时 true
     */
    public static boolean containsId(String idsText, Long encounterDiagnosisId) {
        if (encounterDiagnosisId == null) {
            return false;
        }
        List<Long> ids = parseOrderedIds(idsText);
        for (Long id : ids) {
            if (encounterDiagnosisId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 将有序 id 列表规范为落库 ids 串（不去重；调用方保证主诊首位）。
     *
     * @param orderedIds 有序主键
     * @return ids 串；空列表返回 null
     */
    public static String joinOrderedIds(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Long id : orderedIds) {
            if (id == null) {
                continue;
            }
            if (!first) {
                sb.append(IDS_SEPARATOR);
            }
            sb.append(id);
            first = false;
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 取 ids 串首位 id（主诊位，供统计/分组代表；结算多诊上传不限于此位）。
     *
     * @param idsText 原始 ids 串
     * @return 首位主键或 null
     */
    public static Long firstId(String idsText) {
        List<Long> ids = parseOrderedIds(idsText, true);
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(0);
    }

    /**
     * 读侧回显：按有序去重 ids 解析展示名并以顿号拼接（与前端 {@code formatEncounterDiagnosisDisplayNames} 一致）。
     *
     * @param idsText      就诊诊断 ids 串；可空
     * @param nameResolver encounterDiagnosisId → 展示名；可返回 null/空
     * @return 拼接后的名称；无有效名时 null
     */
    public static String joinOrderedDisplayNames(String idsText, Function<Long, String> nameResolver) {
        if (nameResolver == null) {
            return null;
        }
        List<Long> ids = parseUniqueOrderedIds(idsText, true);
        if (ids.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) {
            if (id == null) {
                continue;
            }
            String name = trimToNull(nameResolver.apply(id));
            if (name == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('、');
            }
            sb.append(name);
        }
        if (sb.length() == 0) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 将解析结果转为 Set（相等比较辅助）。
     *
     * @param idsText 原始 ids 串
     * @return 去重集合
     */
    public static Set<Long> toIdSet(String idsText) {
        return new LinkedHashSet<>(parseUniqueOrderedIds(idsText));
    }
}
