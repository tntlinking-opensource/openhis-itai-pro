/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.medication.medicationrequest.prescriptionsplit;

import com.openhis.health.domain.medication.medicationrequest.command.PrescriptionSplitLineContext;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.PrescriptionSplitConstants;
import com.openhis.health.domain.share.enums.YbMedType;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 门诊药品处方分方装箱：按医保医疗类别、特殊管理药品、饮片、药房与金额/品种上限划分处方单元。
 *
 * <p>纯领域算法，无 Spring 依赖；由 {@code MedicationRequestDomainService#splitPrescription} 调用。</p>
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
        // Pre：按 group_id 合并为不可拆原子包
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
        // Loop：各 med_type+轨道+库房分区内做 greedy 装箱
        List<PrescriptionUnitPlan> units = new ArrayList<>();
        for (List<PrescriptionSplitPack> partitionPacks : byPartition.values()) {
            List<PrescriptionUnitPlan> partitionUnits = packPartition(partitionPacks, limit);
            units.addAll(partitionUnits);
        }
        return units;
    }

    /**
     * 将同一分区内原子包做 greedy 装箱（尊重成组不可拆与品种/金额上限）。
     *
     * @param packs        同一 med_type+轨道+库房分区内的原子包
     * @param amountLimit  单张处方金额上限（元）
     * @return 分区内的处方单元列表
     */
    private static List<PrescriptionUnitPlan> packPartition(List<PrescriptionSplitPack> packs, BigDecimal amountLimit) {
        List<PrescriptionUnitPlan> result = new ArrayList<>();
        PrescriptionBin currentBin = null;
        for (PrescriptionSplitPack pack : packs) {
            // 当前桶尚能容纳则合并（成组包不可拆，仅做 greedy 追加）
            if (currentBin != null && currentBin.canAbsorb(pack, amountLimit)) {
                currentBin.absorb(pack);
            } else {
                // 封桶并开新桶
                if (currentBin != null) {
                    result.add(currentBin.toPlan());
                }
                currentBin = new PrescriptionBin();
                // 单包本身超品种/金额上限则无法分方
                if (!currentBin.canAbsorb(pack, amountLimit)) {
                    throw new BusinessException(
                        ErrorCodeConstants.MEDICATION_REQUEST_RULE_CONFLICT,
                        "单组药品超出处方品种或金额上限，无法分方");
                }
                currentBin.absorb(pack);
            }
        }
        if (currentBin != null) {
            result.add(currentBin.toPlan());
        }
        return result;
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
     * 装箱过程中的一个处方桶。
     */
    private static final class PrescriptionBin {
        private final List<Long> requestIds = new ArrayList<>();
        private final Set<Long> medicationIds = new LinkedHashSet<>();
        private BigDecimal amountSum = BigDecimal.ZERO;
        private String splitTrack;
        private String medType;
        private boolean herbalFlag;

        boolean canAbsorb(PrescriptionSplitPack pack, BigDecimal amountLimit) {
            if (pack == null) {
                return false;
            }
            Set<Long> mergedMedIds = new LinkedHashSet<>(medicationIds);
            mergedMedIds.addAll(pack.medicationIds);
            // 西药/麻精/毒性轨道：合并后品种数不得超过 5
            if (PrescriptionSplitConstants.SPLIT_TRACK_WESTERN_PATENT.equals(pack.splitTrack)
                || PrescriptionSplitConstants.SPLIT_TRACK_NARCOTIC_PSYCH_ONE.equals(pack.splitTrack)
                || PrescriptionSplitConstants.SPLIT_TRACK_TOXIC.equals(pack.splitTrack)) {
                if (mergedMedIds.size() > PrescriptionSplitConstants.MAX_WESTERN_PATENT_MEDICATION_KINDS_PER_PRESCRIPTION) {
                    return false;
                }
            }
            // 合并后金额不得超过单张处方上限
            BigDecimal nextAmount = amountSum.add(pack.amountSum);
            if (nextAmount.compareTo(amountLimit) > 0) {
                return false;
            }
            return true;
        }

        void absorb(PrescriptionSplitPack pack) {
            requestIds.addAll(pack.requestIds);
            medicationIds.addAll(pack.medicationIds);
            amountSum = amountSum.add(pack.amountSum);
            splitTrack = pack.splitTrack;
            medType = pack.medType;
            herbalFlag = pack.herbalFlag;
        }

        PrescriptionUnitPlan toPlan() {
            String rxType = deriveRxTypeCode(splitTrack, herbalFlag, medType);
            return new PrescriptionUnitPlan(requestIds, rxType);
        }
    }

    /**
     * 不可再拆的原子包（单行或同一 {@code group_id}）。
     */
    private static final class PrescriptionSplitPack {
        private final List<Long> requestIds = new ArrayList<>();
        private final Set<Long> medicationIds = new LinkedHashSet<>();
        private BigDecimal amountSum = BigDecimal.ZERO;
        private String medType;
        private String splitTrack;
        private Long pharmacyLocationId;
        private boolean herbalFlag;

        static PrescriptionSplitPack fromLine(PrescriptionSplitLineContext line) {
            PrescriptionSplitPack pack = new PrescriptionSplitPack();
            pack.mergeLine(line);
            return pack;
        }

        void mergeLine(PrescriptionSplitLineContext line) {
            requestIds.add(line.getRequestId());
            if (line.getMedicationId() != null) {
                medicationIds.add(line.getMedicationId());
            }
            BigDecimal lineAmount = line.getChargeTotalPrice();
            if (lineAmount != null) {
                amountSum = amountSum.add(lineAmount);
            }
            medType = normalizeMedType(line.getMedType());
            splitTrack = resolveSplitTrack(line);
            pharmacyLocationId = line.getPharmacyLocationId();
            herbalFlag = Boolean.TRUE.equals(line.getTcmFlag());
        }

        String partitionKey() {
            String med = medType == null ? "" : medType;
            String track = splitTrack == null ? "" : splitTrack;
            String loc = pharmacyLocationId == null ? "" : String.valueOf(pharmacyLocationId);
            return med + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
                + track + PrescriptionSplitConstants.PARTITION_KEY_SEPARATOR
                + loc;
        }
    }

    private static String normalizeMedType(String medType) {
        if (StringUtils.isBlank(medType)) {
            return YbMedType.GENERAL_OUTPATIENT.getCode();
        }
        return medType.trim();
    }

    /**
     * 按药品性质与饮片标志判定分方轨道（P0′ 特殊药品 / 饮片 / 西药中成药）。
     *
     * @param line 行快照
     * @return 分方轨道码
     */
    private static String resolveSplitTrack(PrescriptionSplitLineContext line) {
        // 饮片单独轨道，不与西药混方
        if (Boolean.TRUE.equals(line.getTcmFlag())) {
            return PrescriptionSplitConstants.SPLIT_TRACK_HERBAL;
        }
        String part = line.getPartAttributeEnum();
        if (part == null) {
            part = PrescriptionSplitConstants.PART_ATTRIBUTE_NORMAL;
        }
        part = part.trim();
        // 麻/精一须单独处方
        if (PrescriptionSplitConstants.PART_ATTRIBUTE_NARCOTIC.equals(part)
            || PrescriptionSplitConstants.PART_ATTRIBUTE_PSYCH_ONE.equals(part)) {
            return PrescriptionSplitConstants.SPLIT_TRACK_NARCOTIC_PSYCH_ONE;
        }
        // 毒性药品单独轨道
        if (PrescriptionSplitConstants.PART_ATTRIBUTE_TOXIC.equals(part)) {
            return PrescriptionSplitConstants.SPLIT_TRACK_TOXIC;
        }
        return PrescriptionSplitConstants.SPLIT_TRACK_WESTERN_PATENT;
    }

    /**
     * 按轨道与医保医疗类别推导处方类别码（355）。
     *
     * @param splitTrack 分方轨道
     * @param herbalFlag 是否饮片方
     * @param medType    医保医疗类别 code
     * @return {@code rx_type_code}
     */
    private static String deriveRxTypeCode(String splitTrack, boolean herbalFlag, String medType) {
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
        if (herbalFlag) {
            if (emergency) {
                return PrescriptionSplitConstants.RX_TYPE_EMERGENCY_HERBAL;
            }
            if (pediatric) {
                return PrescriptionSplitConstants.RX_TYPE_PEDIATRIC_HERBAL;
            }
            return PrescriptionSplitConstants.RX_TYPE_OUTPATIENT_HERBAL;
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
