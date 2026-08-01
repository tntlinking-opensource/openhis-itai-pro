/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.medication.medicationrequest.prescriptionsplit;

import com.openhis.health.domain.medication.medicationrequest.command.PrescriptionSplitLineContext;
import com.openhis.health.domain.share.constants.DailySettlementSharedConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.health.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊药品处方分方装箱：按医保医疗类别、药品性质、饮片、西药/中成药目录分类、药房与金额/品种上限划分处方单元。
 *
 * <p>纯领域算法，无 Spring 依赖；由 {@code MedicationRequestDomainService#splitPrescription} 调用。
 * 特殊管理药品判读字段为 {@code pharmacologyCategoryCode}（术语 medicine_properties），
 * 禁止使用门诊拆分属性 {@code part_attribute_enum}。</p>
 *
 * <p>诊断：硬分区键不含诊断集；同分区内装箱时两包诊断集<strong>有交集即可同方</strong>
 * （两侧皆空亦相容；一侧空一侧非空不相容）。方头展示诊断并集由读侧按 {@code prescription_no} 聚合。</p>
 */
public final class OutpatientPrescriptionSplitPlanner {

    private OutpatientPrescriptionSplitPlanner() {
    }

    /**
     * 将行快照划分为处方单元（每单元含申请主键列表与推导的 {@code rx_type_code}）。
     *
     * @param lines           未收费药品行快照
     * @param amountLimitYuan 单张处方金额上限
     * @return 有序处方单元列表
     */
    public static List<PrescriptionUnitPlan> plan(List<PrescriptionSplitLineContext> lines, BigDecimal amountLimitYuan) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        BigDecimal limit = amountLimitYuan;
        if (limit == null || limit.compareTo(BigDecimal.ZERO) <= 0) {
            limit = PrescriptionSplitConstants.DEFAULT_OUTPATIENT_AMOUNT_LIMIT_YUAN;
        }
        // Pre：按 group_id 合并为不可拆原子包（组内每条 req 仍单独计入 ≤5 行上限）
        List<PrescriptionSplitPack> packs = buildAtomicPacks(lines);
        Map<String, List<PrescriptionSplitPack>> byPartition = new LinkedHashMap<>();
        for (PrescriptionSplitPack pack : packs) {
            String partitionKey = pack.partitionKey();
            List<PrescriptionSplitPack> bucket = byPartition.get(partitionKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                byPartition.put(partitionKey, bucket);
            }
            bucket.add(pack);
        }
        // Loop：各 med_type+轨道+库房分区内做 greedy 装箱（诊断在桶内按交集相容）
        List<PrescriptionUnitPlan> units = new ArrayList<>();
        for (List<PrescriptionSplitPack> partitionPacks : byPartition.values()) {
            List<PrescriptionUnitPlan> partitionUnits = packPartition(partitionPacks, limit);
            units.addAll(partitionUnits);
        }
        return units;
    }

    /**
     * 将同一分区内原子包做多轮装箱：每轮从待装队列中尽量填满一桶（≤5 行），
     * 仅因同药同用法或诊断不相容暂跳过的包可在同轮继续尝试后续包，避免过早封桶导致 3+4 而非 5+2。
     *
     * @param packs        同一 med_type+轨道+库房分区内的原子包
     * @param amountLimit  单张处方金额上限（元）
     * @return 分区内的处方单元列表
     */
    private static List<PrescriptionUnitPlan> packPartition(List<PrescriptionSplitPack> packs, BigDecimal amountLimit) {
        List<PrescriptionUnitPlan> result = new ArrayList<>();
        List<PrescriptionSplitPack> pending = new ArrayList<>(packs);
        while (!pending.isEmpty()) {
            PrescriptionBin currentBin = new PrescriptionBin();
            List<PrescriptionSplitPack> stillPending = new ArrayList<>();
            int absorbedCount = 0;
            // Loop：单轮扫描待装队列，能并入当前桶的并入，暂不能的留待下轮
            for (PrescriptionSplitPack pack : pending) {
                if (currentBin.canAbsorb(pack, amountLimit)) {
                    currentBin.absorb(pack);
                    absorbedCount++;
                } else {
                    stillPending.add(pack);
                }
            }
            // 一轮无任何并入说明队首包已无法单独成方
            if (absorbedCount == 0) {
                throw packAloneConflict(stillPending.get(0));
            }
            result.add(currentBin.toPlan());
            pending = stillPending;
        }
        return result;
    }

    /**
     * 原子包无法单独成方时，按违反规则给出业务拒绝语义。
     *
     * @param pack 不可拆原子包
     * @return 业务异常
     */
    private static BusinessException packAloneConflict(PrescriptionSplitPack pack) {
        if (pack.hasDuplicateMedicationUsage()) {
            return new BusinessException(
                ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                "同一处方不得包含相同药品目录与相同用法，无法分方");
        }
        return new BusinessException(
            ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
            "单组药品超出处方医嘱行数或金额上限，无法分方");
    }

    /**
     * 按 {@code group_id} 将行合并为不可拆原子包，无组成组号的单行亦为一包。
     *
     * @param lines 行快照
     * @return 原子包列表
     */
    private static List<PrescriptionSplitPack> buildAtomicPacks(List<PrescriptionSplitLineContext> lines) {
        Map<Long, PrescriptionSplitPack> groupPacks = new LinkedHashMap<>();
        List<PrescriptionSplitPack> singles = new ArrayList<>();
        for (PrescriptionSplitLineContext line : lines) {
            if (line == null || line.getRequestId() == null) {
                continue;
            }
            Long groupId = line.getGroupId();
            // 有 group_id 的行须合并为同一不可拆原子包
            if (groupId != null) {
                PrescriptionSplitPack existing = groupPacks.get(groupId);
                if (existing == null) {
                    PrescriptionSplitPack created = PrescriptionSplitPack.fromLine(line);
                    groupPacks.put(groupId, created);
                } else {
                    existing.mergeLine(line);
                }
            } else {
                // 无组成组号的单行独立成包
                singles.add(PrescriptionSplitPack.fromLine(line));
            }
        }
        List<PrescriptionSplitPack> all = new ArrayList<>(groupPacks.values());
        all.addAll(singles);
        return all;
    }

    /**
     * 统计参与行的医嘱条数：每条 {@code MedicationRequest} 计 1，相同目录主键不合并。
     *
     * @param entries 行条目
     * @return 医嘱行数
     */
    private static int countRequestLines(List<PrescriptionSplitLineEntry> entries) {
        return entries.size();
    }

    /**
     * 判定合并后的行集合是否出现「同药品目录主键 + 同用法签名」重复。
     *
     * @param entries 行条目
     * @return 存在重复时 true
     */
    private static boolean hasDuplicateMedicationUsage(List<PrescriptionSplitLineEntry> entries) {
        Set<String> medicationUsageKeys = new LinkedHashSet<>();
        for (PrescriptionSplitLineEntry entry : entries) {
            String key = entry.medicationUsageKey;
            if (key == null) {
                continue;
            }
            if (!medicationUsageKeys.add(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 西药/中成药/麻精/毒性轨道是否超出单张处方医嘱行上限。
     *
     * @param splitTrack   分方轨道
     * @param requestCount 医嘱行数
     * @return 超限时 true
     */
    private static boolean exceedsWesternRequestLineLimit(String splitTrack, int requestCount) {
        if (!PrescriptionSplitConstants.SPLIT_TRACK_WESTERN.equals(splitTrack)
            && !PrescriptionSplitConstants.SPLIT_TRACK_CHINESE_PATENT.equals(splitTrack)
            && !PrescriptionSplitConstants.SPLIT_TRACK_NARCOTIC_PSYCH_ONE.equals(splitTrack)
            && !PrescriptionSplitConstants.SPLIT_TRACK_TOXIC.equals(splitTrack)) {
            return false;
        }
        return requestCount > PrescriptionSplitConstants.MAX_WESTERN_PATENT_MEDICATION_KINDS_PER_PRESCRIPTION;
    }

    /**
     * 构建用法签名：给药途径 + 频次 + 用药说明 + 单次剂量 + 剂量单位。
     *
     * @param line 行快照
     * @return 归一化用法签名
     */
    private static String buildUsageSignature(PrescriptionSplitLineContext line) {
        if (line == null) {
            return "";
        }
        String method = normalizeSplitCode(line.getMethodCode());
        String rate = normalizeSplitCode(line.getRateCode());
        String dosageInstruction = normalizeSplitCode(line.getDosageInstruction());
        String dose = normalizeDose(line.getDose());
        String doseUnit = normalizeSplitCode(line.getDoseUnitCode());
        return method
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + rate
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + dosageInstruction
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + dose
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + doseUnit;
    }

    /**
     * 构建「药品目录主键 + 用法签名」键，供同方互斥判定。
     *
     * @param medicationId    药品目录主键
     * @param usageSignature  用法签名
     * @return 重复判定键；目录主键为空时返回 null
     */
    private static String buildMedicationUsageKey(Long medicationId, String usageSignature) {
        if (medicationId == null) {
            return null;
        }
        String usage;
        if (usageSignature == null) {
            usage = "";
        } else {
            usage = usageSignature;
        }
        return medicationId
            + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
            + usage;
    }

    /**
     * 归一化分方用法相关编码（trim；空为 ""）。
     *
     * @param code 原始编码
     * @return 归一化结果
     */
    private static String normalizeSplitCode(String code) {
        if (StringUtils.isEmpty(code)) {
            return "";
        }
        return code.trim();
    }

    /**
     * 归一化单次剂量为稳定字符串（空为 ""）。
     *
     * @param dose 单次剂量
     * @return 归一化结果
     */
    private static String normalizeDose(BigDecimal dose) {
        if (dose == null) {
            return "";
        }
        return dose.stripTrailingZeros().toPlainString();
    }

    /**
     * 处方单元计划：一组申请主键与同方 {@code rx_type_code}。
     */
    public static final class PrescriptionUnitPlan {
        private final List<Long> requestIds;
        private final String rxTypeCode;

        PrescriptionUnitPlan(List<Long> requestIds, String rxTypeCode) {
            this.requestIds = List.copyOf(requestIds);
            this.rxTypeCode = rxTypeCode;
        }

        public List<Long> getRequestIds() {
            return requestIds;
        }

        public String getRxTypeCode() {
            return rxTypeCode;
        }
    }

    /**
     * 参与分方的单行条目（一条 req 计一行，保留目录主键与用法签名供互斥判定）。
     */
    private static final class PrescriptionSplitLineEntry {
        private final Long requestId;
        private final Long medicationId;
        private final String medicationUsageKey;

        PrescriptionSplitLineEntry(Long requestId, Long medicationId, String medicationUsageKey) {
            this.requestId = requestId;
            this.medicationId = medicationId;
            this.medicationUsageKey = medicationUsageKey;
        }

        static PrescriptionSplitLineEntry fromLine(PrescriptionSplitLineContext line) {
            String usageSignature = buildUsageSignature(line);
            String medicationUsageKey = buildMedicationUsageKey(line.getMedicationId(), usageSignature);
            return new PrescriptionSplitLineEntry(line.getRequestId(), line.getMedicationId(), medicationUsageKey);
        }
    }

    /**
     * 装箱过程中的一个处方桶。
     */
    private static final class PrescriptionBin {
        private final List<PrescriptionSplitLineEntry> entries = new ArrayList<>();
        private BigDecimal amountSum = BigDecimal.ZERO;
        private String splitTrack;
        private String medType;
        /** 已装入行的诊断 id 并集（方头全集口径）。 */
        private final Set<Long> diagnosisIdUnion = new LinkedHashSet<>();

        boolean canAbsorb(PrescriptionSplitPack pack, BigDecimal amountLimit) {
            if (pack == null) {
                return false;
            }
            List<PrescriptionSplitLineEntry> mergedEntries = new ArrayList<>(entries);
            mergedEntries.addAll(pack.entries);
            int mergedRequestCount = countRequestLines(mergedEntries);
            // 西药/中成药/麻精/毒性轨道：合并后医嘱行数不得超过 5（相同目录主键的多条 req 仍分别计入）
            if (exceedsWesternRequestLineLimit(pack.splitTrack, mergedRequestCount)) {
                return false;
            }
            // 同药品目录主键 + 同用法不得落入同一处方
            if (hasDuplicateMedicationUsage(mergedEntries)) {
                return false;
            }
            // 合并后金额不得超过单张处方上限
            BigDecimal nextAmount = amountSum.add(pack.amountSum);
            if (nextAmount.compareTo(amountLimit) > 0) {
                return false;
            }
            // 空桶首包无诊断约束；其后须与桶内诊断并集有交集（或双侧皆空）
            if (!entries.isEmpty() && !diagnosisCompatibleWithBin(pack)) {
                return false;
            }
            return true;
        }

        /**
         * 待装包与桶内诊断并集是否相容（有交集 / 双侧皆空）。
         *
         * @param pack 待装原子包
         * @return 相容时 true
         */
        private boolean diagnosisCompatibleWithBin(PrescriptionSplitPack pack) {
            String binKey = EncounterDiagnosisIdsSupport.joinOrderedIds(new ArrayList<>(diagnosisIdUnion));
            String packKey = EncounterDiagnosisIdsSupport.joinOrderedIds(new ArrayList<>(pack.diagnosisIds));
            return EncounterDiagnosisIdsSupport.compatibleForSamePrescription(binKey, packKey);
        }

        void absorb(PrescriptionSplitPack pack) {
            entries.addAll(pack.entries);
            amountSum = amountSum.add(pack.amountSum);
            splitTrack = pack.splitTrack;
            medType = pack.medType;
            diagnosisIdUnion.addAll(pack.diagnosisIds);
        }

        PrescriptionUnitPlan toPlan() {
            List<Long> requestIds = new ArrayList<>();
            for (PrescriptionSplitLineEntry entry : entries) {
                requestIds.add(entry.requestId);
            }
            String rxType = deriveRxTypeCode(splitTrack, medType);
            return new PrescriptionUnitPlan(requestIds, rxType);
        }
    }

    /**
     * 不可再拆的原子包（单行或同一 {@code group_id}）。
     */
    private static final class PrescriptionSplitPack {
        private final List<PrescriptionSplitLineEntry> entries = new ArrayList<>();
        private BigDecimal amountSum = BigDecimal.ZERO;
        private String medType;
        private final Set<Long> diagnosisIds = new LinkedHashSet<>();
        private String splitTrack;
        private Long pharmacyLocationId;

        static PrescriptionSplitPack fromLine(PrescriptionSplitLineContext line) {
            PrescriptionSplitPack pack = new PrescriptionSplitPack();
            pack.mergeLine(line);
            return pack;
        }

        void mergeLine(PrescriptionSplitLineContext line) {
            entries.add(PrescriptionSplitLineEntry.fromLine(line));
            BigDecimal lineAmount = line.getChargeTotalPrice();
            if (lineAmount != null) {
                amountSum = amountSum.add(lineAmount);
            }
            medType = normalizeMedType(line.getMedType());
            // 成组多行：诊断取并集（入参可为 canonical key 或原始 ids 串）
            diagnosisIds.addAll(EncounterDiagnosisIdsSupport.toIdSetLenient(line.getDiagnosisSetKey()));
            splitTrack = resolveSplitTrack(line);
            pharmacyLocationId = line.getPharmacyLocationId();
        }

        boolean hasDuplicateMedicationUsage() {
            return OutpatientPrescriptionSplitPlanner.hasDuplicateMedicationUsage(entries);
        }

        /**
         * 硬分区键：仅 medType + 轨道 + 库房（诊断改为桶内交集，不入键）。
         *
         * @return 分区键
         */
        String partitionKey() {
            String med;
            if (medType == null) {
                med = "";
            } else {
                med = medType;
            }
            String track;
            if (splitTrack == null) {
                track = "";
            } else {
                track = splitTrack;
            }
            String loc;
            if (pharmacyLocationId == null) {
                loc = "";
            } else {
                loc = String.valueOf(pharmacyLocationId);
            }
            return med + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
                + track + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
                + loc;
        }
    }

    /**
     * 医保 medType 归一：空 → 普通门诊码，否则 trim。
     *
     * @param medType 原始 medType
     * @return 持久化 medType 码
     */
    private static String normalizeMedType(String medType) {
        if (StringUtils.isEmpty(medType)) {
            return YbMedType.GENERAL_OUTPATIENT.getCode();
        }
        return medType.trim();
    }

    /**
     * 按药品性质、饮片标志与目录分类判定分方轨道（特殊药品 / 饮片 / 中成药 / 西药）。
     *
     * <p>先看 {@code pharmacologyCategoryCode}（药品性质）：非普通药走麻/精一/毒性独占轨；
     * 普通药（含空值按普通）再按 {@code medicationCategoryCode} 分西药/中成药轨。
     * 饮片以 {@code tcmFlag} 优先。不得读取门诊拆分属性。</p>
     *
     * @param line 行快照
     * @return 分方轨道码
     */
    private static String resolveSplitTrack(PrescriptionSplitLineContext line) {
        // 饮片单独轨道，不与西药/中成药混方
        if (Boolean.TRUE.equals(line.getTcmFlag())) {
            return PrescriptionSplitConstants.SPLIT_TRACK_HERBAL;
        }
        String medicineProperty = line.getPharmacologyCategoryCode();
        // 未维护性质时按普通药，再走西药/中成药目录分类
        if (medicineProperty == null) {
            medicineProperty = PrescriptionSplitConstants.MEDICINE_PROPERTY_NORMAL;
        }
        medicineProperty = medicineProperty.trim();
        // 非普通：麻/精一须单独处方（码来自 medicine_properties，非拆零规则）
        if (PrescriptionSplitConstants.MEDICINE_PROPERTY_NARCOTIC.equals(medicineProperty)
            || PrescriptionSplitConstants.MEDICINE_PROPERTY_PSYCH_ONE.equals(medicineProperty)) {
            return PrescriptionSplitConstants.SPLIT_TRACK_NARCOTIC_PSYCH_ONE;
        }
        // 非普通：毒性药品单独轨道
        if (PrescriptionSplitConstants.MEDICINE_PROPERTY_TOXIC.equals(medicineProperty)) {
            return PrescriptionSplitConstants.SPLIT_TRACK_TOXIC;
        }
        // 普通药（含精二）：中成药与西药分轨
        if (isChinesePatentCategory(line.getMedicationCategoryCode())) {
            return PrescriptionSplitConstants.SPLIT_TRACK_CHINESE_PATENT;
        }
        return PrescriptionSplitConstants.SPLIT_TRACK_WESTERN;
    }

    /**
     * 判定药品目录分类是否为中成药。
     *
     * @param medicationCategoryCode {@code medication_category_code}
     * @return 中成药时 true
     */
    private static boolean isChinesePatentCategory(String medicationCategoryCode) {
        if (StringUtils.isEmpty(medicationCategoryCode)) {
            return false;
        }
        return DailySettlementSharedConstants.MEDICATION_CATEGORY_PATENT_CHINESE_CODE
            .equals(medicationCategoryCode.trim());
    }

    /**
     * 按轨道与医保医疗类别推导处方类别码（355）。
     *
     * @param splitTrack 分方轨道
     * @param medType    医保医疗类别 code
     * @return {@code rx_type_code}
     */
    private static String deriveRxTypeCode(String splitTrack, String medType) {
        // 麻/精一处方类别固定为 7
        if (PrescriptionSplitConstants.SPLIT_TRACK_NARCOTIC_PSYCH_ONE.equals(splitTrack)) {
            return PrescriptionSplitConstants.RX_TYPE_NARCOTIC_PSYCH_ONE;
        }
        // 毒性药品暂映射门诊西药类别
        if (PrescriptionSplitConstants.SPLIT_TRACK_TOXIC.equals(splitTrack)) {
            return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_WESTERN;
        }
        boolean emergency = YbMedType.EMERGENCY.getCode().equals(medType);
        boolean pediatric = isPediatricMedType(medType);
        // 中药饮片：急诊/儿科抬升
        if (PrescriptionSplitConstants.SPLIT_TRACK_HERBAL.equals(splitTrack)) {
            if (emergency) {
                return PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL;
            }
            if (pediatric) {
                return PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_HERBAL;
            }
            return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_HERBAL;
        }
        // 中成药：与西药对称抬升急诊/儿科类别
        if (PrescriptionSplitConstants.SPLIT_TRACK_CHINESE_PATENT.equals(splitTrack)) {
            if (emergency) {
                return PrescriptionSplitConstants.RX_TYPE_EMERGENCY_PATENT;
            }
            if (pediatric) {
                return PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_PATENT;
            }
            return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_PATENT;
        }
        if (emergency) {
            return PrescriptionSplitConstants.RX_TYPE_EMERGENCY_WESTERN;
        }
        if (pediatric) {
            return PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_WESTERN;
        }
        return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_WESTERN;
    }

    /**
     * 判定是否儿科处方类别（355 码 5/6）。
     *
     * <p>当前 {@link YbMedType} 无独立儿科医疗类别码；待产品定义儿科 med_type 映射前恒为 false，
     * 儿科处方类别码不在分方阶段自动抬升。</p>
     *
     * @param medType 医保医疗类别 code
     * @return 是否儿科
     */
    private static boolean isPediatricMedType(String medType) {
        return false;
    }
}
