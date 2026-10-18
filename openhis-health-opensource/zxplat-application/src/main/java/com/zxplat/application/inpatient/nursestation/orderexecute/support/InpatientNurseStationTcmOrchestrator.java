/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.orderexecute.support;

import static com.zxplat.framework.core.application.util.StringUtils.trimToEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.application.shared.advice.InpatientOrderListSkeleton;
import com.zxplat.domain.share.constants.InpatientNurseStationConstants;
import com.zxplat.domain.share.constants.ReadModelOrderTypeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站药嘱写扩行与中药方折叠：中西药 Tab 筛选、中药按 {@code prescription_no} 整方扩行/折叠、
 * 西药成组按 {@code group_id} 写扩行（与门诊护士站成组展示 + 住院校对/执行一起操作对齐；AS-IP-DOR-006 / AS-IP-NUR-003/004）。
 */
@Component
public class InpatientNurseStationTcmOrchestrator {

    /**
     * 解析中西药 Tab，非法值抛业务异常。
     *
     * @param medicineCategoryTab west / tcm / all；空则默认 west
     * @return 规范化 tab 码
     */
    public String resolveMedicineCategoryTabOrDefault(String medicineCategoryTab) {
        String tab = trimToEmpty(medicineCategoryTab);
        if (StringUtils.isEmpty(tab)) {
            return InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST;
        }
        if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)
            || InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)
            || InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_ALL.equals(tab)) {
            return tab;
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "中西药 Tab 不合法");
    }

    /**
     * 是否「全部」Tab（西药+中药+诊疗一次返回）。
     *
     * @param medicineCategoryTab west / tcm / all
     * @return true 表示 all
     */
    public boolean isAllMedicineCategoryTab(String medicineCategoryTab) {
        return InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_ALL.equals(
            resolveMedicineCategoryTabOrDefault(medicineCategoryTab));
    }

    /**
     * 是否中药 Tab。
     *
     * @param medicineCategoryTab west / tcm / all
     * @return true 表示 tcm Tab
     */
    public boolean isTcmTab(String medicineCategoryTab) {
        return InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(
            resolveMedicineCategoryTabOrDefault(medicineCategoryTab));
    }

    /**
     * 中药 Tab 下是否展示诊疗医嘱（中药 Tab 仅药嘱方级行；all/west 展示诊疗）。
     *
     * @param medicineCategoryTab west / tcm / all
     * @return true 表示可展示 SERVICE_REQUEST
     */
    public boolean includeServiceRequestOnTab(String medicineCategoryTab) {
        return !isTcmTab(medicineCategoryTab);
    }

    /**
     * 按中西药 Tab 过滤药品明细行。
     *
     * @param rows                就诊下药品明细
     * @param medicineCategoryTab west / tcm
     * @return 过滤后的列表
     */
    public List<MedicationRequestListQueryModel> filterMedRowsByMedicineCategory(
        List<MedicationRequestListQueryModel> rows,
        String medicineCategoryTab) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        String tab = resolveMedicineCategoryTabOrDefault(medicineCategoryTab);
        List<MedicationRequestListQueryModel> result = new ArrayList<>();
        for (MedicationRequestListQueryModel row : rows) {
            if (row == null) {
                continue;
            }
            if (matchesMedicineCategoryTab(row, tab)) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 按中西药 Tab 过滤 OrderInfo 骨架行（需结合药品明细判断 tcmFlag）。
     *
     * @param skeleton            骨架行
     * @param medDetailById       药品申请 id → 明细
     * @param medicineCategoryTab west / tcm
     * @return 过滤后的骨架
     */
    public List<InpatientOrderListSkeleton> filterSkeletonByMedicineCategory(
        List<InpatientOrderListSkeleton> skeleton,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        String medicineCategoryTab) {
        if (skeleton == null || skeleton.isEmpty()) {
            return new ArrayList<>();
        }
        String tab = resolveMedicineCategoryTabOrDefault(medicineCategoryTab);
        boolean includeService = includeServiceRequestOnTab(tab);
        List<InpatientOrderListSkeleton> result = new ArrayList<>();
        for (InpatientOrderListSkeleton row : skeleton) {
            if (row == null) {
                continue;
            }
            if (ReadModelOrderTypeConstants.SERVICE_REQUEST.equals(row.getOrderType())) {
                // 中药 Tab 不展示诊疗行
                if (includeService) {
                    result.add(row);
                }
                continue;
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(row.getOrderType())) {
                continue;
            }
            Long medReqId = row.getMedReqId();
            MedicationRequestListQueryModel med = medDetailById.get(medReqId);
            if (matchesMedicineCategoryTab(med, tab)) {
                result.add(row);
            }
        }
        return result;
    }

    /**
     * 构建就诊维度药品 id 索引。
     *
     * @param medByEncounter 就诊 → 药品明细列表
     * @return 全局 medReqId → 明细
     */
    public Map<Long, MedicationRequestListQueryModel> indexMedDetailById(
        Map<Long, List<MedicationRequestListQueryModel>> medByEncounter) {
        Map<Long, MedicationRequestListQueryModel> map = new LinkedHashMap<>();
        if (medByEncounter == null) {
            return map;
        }
        for (List<MedicationRequestListQueryModel> rows : medByEncounter.values()) {
            if (rows == null) {
                continue;
            }
            for (MedicationRequestListQueryModel row : rows) {
                if (row != null && row.getId() != null) {
                    map.put(row.getId(), row);
                }
            }
        }
        return map;
    }

    /**
     * 按同就诊同处方号扩全方药品申请 id（整方校对/执行/退回）。
     *
     * @param encounterId   就诊主键
     * @param seedRequestId 种子行 id
     * @param encounterMeds 本就诊药品明细全集
     * @return 扩行后的 id 列表（含种子）
     */
    public List<Long> expandMedRequestIdsByPrescriptionNo(
        Long encounterId,
        Long seedRequestId,
        List<MedicationRequestListQueryModel> encounterMeds) {
        Set<Long> expanded = new LinkedHashSet<>();
        if (seedRequestId != null) {
            expanded.add(seedRequestId);
        }
        if (encounterId == null || seedRequestId == null || encounterMeds == null) {
            return new ArrayList<>(expanded);
        }
        MedicationRequestListQueryModel seed = null;
        for (MedicationRequestListQueryModel row : encounterMeds) {
            if (row != null && seedRequestId.equals(row.getId())) {
                seed = row;
                break;
            }
        }
        if (seed == null || !Boolean.TRUE.equals(seed.getTcmFlag())) {
            return new ArrayList<>(expanded);
        }
        String targetPrescriptionNo = trimToNull(seed.getPrescriptionNo());
        if (targetPrescriptionNo == null) {
            return new ArrayList<>(expanded);
        }
        // 同方：tcm_flag=true 且 prescription_no 一致的全部味药行
        for (MedicationRequestListQueryModel row : encounterMeds) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            if (targetPrescriptionNo.equals(trimToNull(row.getPrescriptionNo()))) {
                expanded.add(row.getId());
            }
        }
        return new ArrayList<>(expanded);
    }

    /**
     * 按同就诊同 {@code group_id} 扩全组成组药嘱 id（西药成组一起校对/执行/退回/不执行）。
     * <p>中药行不走本组扩行（仍按 {@link #expandMedRequestIdsByPrescriptionNo}）；未成组仅回种子行。</p>
     *
     * @param encounterId   就诊主键
     * @param seedRequestId 种子行 id（组内任一行）
     * @param encounterMeds 本就诊药品明细全集
     * @return 扩行后的 id 列表（含种子；同组一致）
     */
    public List<Long> expandMedRequestIdsByGroupId(
        Long encounterId,
        Long seedRequestId,
        List<MedicationRequestListQueryModel> encounterMeds) {
        Set<Long> expanded = new LinkedHashSet<>();
        if (seedRequestId != null) {
            expanded.add(seedRequestId);
        }
        if (encounterId == null || seedRequestId == null || encounterMeds == null) {
            return new ArrayList<>(expanded);
        }
        MedicationRequestListQueryModel seed = null;
        for (MedicationRequestListQueryModel row : encounterMeds) {
            if (row != null && seedRequestId.equals(row.getId())) {
                seed = row;
                break;
            }
        }
        // 中药成组扩行走处方号，不在此按 group_id 扩
        if (seed == null || Boolean.TRUE.equals(seed.getTcmFlag())) {
            return new ArrayList<>(expanded);
        }
        Long targetGroupId = seed.getGroupId();
        if (targetGroupId == null) {
            return new ArrayList<>(expanded);
        }
        // 同组：非中药且 group_id 一致的全部药嘱行（与医生站输液成组同源）
        for (MedicationRequestListQueryModel row : encounterMeds) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            if (targetGroupId.equals(row.getGroupId())) {
                expanded.add(row.getId());
            }
        }
        return new ArrayList<>(expanded);
    }

    /**
     * 护士站药嘱写路径统一扩行：优先中药整方（prescription_no），否则西药成组（group_id），否则仅种子行。
     *
     * @param encounterId   就诊主键
     * @param seedRequestId 种子行 id
     * @param encounterMeds 本就诊药品明细全集
     * @return 扩行后的 id 列表（含种子）
     */
    public List<Long> expandMedRequestIdsForNurseWrite(
        Long encounterId,
        Long seedRequestId,
        List<MedicationRequestListQueryModel> encounterMeds) {
        List<Long> byPrescription =
            this.expandMedRequestIdsByPrescriptionNo(encounterId, seedRequestId, encounterMeds);
        // 中药整方已扩出多方味药时，不再叠加 group_id 扩行
        if (byPrescription.size() > 1) {
            return byPrescription;
        }
        return this.expandMedRequestIdsByGroupId(encounterId, seedRequestId, encounterMeds);
    }

    /**
     * 中药 Tab 下列表按 prescription_no 折叠为一行（一方一行展示）。
     *
     * @param rows              已组装的药/诊行（含 requestId、adviceDisplayName 等）
     * @param medDetailById     药品明细索引
     * @param medicineCategoryTab west / tcm
     * @param rowAccessor       行访问器（校对/执行 DTO 异构）
     * @param <T>               行 DTO 类型
     * @return 折叠后的行列表
     */
    public <T> List<T> collapseTcmPrescriptionDisplayRows(
        List<T> rows,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        String medicineCategoryTab,
        TcmPrescriptionRowAccessor<T> rowAccessor) {
        if (rows == null || rows.isEmpty()) {
            return new ArrayList<>();
        }
        if (!isTcmTab(medicineCategoryTab) && !isAllMedicineCategoryTab(medicineCategoryTab)) {
            return new ArrayList<>(rows);
        }
        Map<String, List<T>> bucketByPrescription = new LinkedHashMap<>();
        List<T> nonTcmOrSingleton = new ArrayList<>();
        for (T row : rows) {
            if (row == null) {
                continue;
            }
            if (!ReadModelOrderTypeConstants.MED_REQUEST.equals(rowAccessor.getOrderType(row))) {
                nonTcmOrSingleton.add(row);
                continue;
            }
            Long requestId = rowAccessor.getRequestId(row);
            MedicationRequestListQueryModel med = medDetailById.get(requestId);
            if (med == null || !Boolean.TRUE.equals(med.getTcmFlag())) {
                nonTcmOrSingleton.add(row);
                continue;
            }
            String prescriptionNo = trimToNull(med.getPrescriptionNo());
            if (prescriptionNo == null) {
                nonTcmOrSingleton.add(row);
                continue;
            }
            String bucketKey = String.valueOf(rowAccessor.getEncounterId(row)) + ":" + prescriptionNo;
            List<T> bucket = bucketByPrescription.get(bucketKey);
            if (bucket == null) {
                bucket = new ArrayList<>();
                bucketByPrescription.put(bucketKey, bucket);
            }
            bucket.add(row);
        }
        List<T> result = new ArrayList<>(nonTcmOrSingleton);
        for (List<T> bucket : bucketByPrescription.values()) {
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            // 代表行取 sortNumber 最小或 id 最小（展示字段载体）；开立时间另取方内最晚
            T representative = bucket.stream()
                .min(Comparator
                    .comparing(rowAccessor::getSortNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(rowAccessor::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(bucket.get(0));
            MedicationRequestListQueryModel repMed = medDetailById.get(rowAccessor.getRequestId(representative));
            List<Long> lineIds = new ArrayList<>();
            for (T line : bucket) {
                Long id = rowAccessor.getRequestId(line);
                if (id != null) {
                    lineIds.add(id);
                }
            }
            // 方级开立时间取最晚一味（空回落签发），使护士按展示时刻顺延即可过 BR-IP-EXE-024
            LocalDateTime prescriptionOpenedTime =
                this.resolveLatestTcmPrescriptionOpenedTime(bucket, medDetailById, rowAccessor);
            List<String> herbNames = this.collectTcmHerbDisplayNames(bucket, medDetailById, rowAccessor);
            List<String> herbDosages = this.collectTcmHerbDosageDisplays(bucket, medDetailById, rowAccessor);
            String displayName = buildTcmPrescriptionDisplayName(herbNames);
            String dosageDisplay = buildTcmPrescriptionDosageDisplay(herbDosages);
            rowAccessor.applyTcmPrescriptionCollapse(
                representative, repMed, lineIds, displayName, dosageDisplay, prescriptionOpenedTime);
            result.add(representative);
        }
        return result;
    }

    /**
     * 方内各味开立轴最晚时刻：优先 {@code req_opened_time}，空回落 {@code req_authored_time}。
     *
     * <p>护士站折叠行展示用最晚时刻，避免按第一味较早时间执行时撞上后开药味的
     * 「执行时间不能早于开立时间」门禁（写路径仍按各味自身开立时间校验）。</p>
     *
     * @param bucket          同方展示行桶
     * @param medDetailById   药品明细索引
     * @param rowAccessor     行访问器
     * @param <T>             行 DTO 类型
     * @return 方级展示开立时间；方内皆空时 null
     */
    private <T> LocalDateTime resolveLatestTcmPrescriptionOpenedTime(
        List<T> bucket,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        TcmPrescriptionRowAccessor<T> rowAccessor) {
        LocalDateTime latest = null;
        if (bucket == null || bucket.isEmpty()) {
            return null;
        }
        // 遍历方内各味，取开立轴最大值
        for (T line : bucket) {
            if (line == null) {
                continue;
            }
            MedicationRequestListQueryModel med = medDetailById.get(rowAccessor.getRequestId(line));
            LocalDateTime lineOpened = this.resolveMedOpenedTimeWithAuthoredFallback(med);
            if (lineOpened == null) {
                continue;
            }
            if (latest == null || lineOpened.isAfter(latest)) {
                latest = lineOpened;
            }
        }
        return latest;
    }

    /**
     * 单味开立轴时刻：优先开立列，空回落签发列。
     *
     * @param med 药品明细；可 null
     * @return 开立轴；皆空时 null
     */
    private LocalDateTime resolveMedOpenedTimeWithAuthoredFallback(MedicationRequestListQueryModel med) {
        if (med == null) {
            return null;
        }
        if (med.getReqOpenedTime() != null) {
            return med.getReqOpenedTime();
        }
        return med.getReqAuthoredTime();
    }

    /**
     * 组装中药方展示名：方内各味药品名合并为一行（空格分隔，按 sortNumber 排序）。
     *
     * @param herbDisplayNames 方内各味展示名（通常为药品名称）
     * @return 展示文案；无有效名称时回退「中药处方」
     */
    public String buildTcmPrescriptionDisplayName(List<String> herbDisplayNames) {
        if (herbDisplayNames == null || herbDisplayNames.isEmpty()) {
            return "中药处方";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String herbName : herbDisplayNames) {
            String trimmed = trimToNull(herbName);
            if (trimmed == null) {
                continue;
            }
            if (!first) {
                builder.append(' ');
            }
            builder.append(trimmed);
            first = false;
        }
        if (builder.length() == 0) {
            return "中药处方";
        }
        return builder.toString();
    }

    /**
     * 组装中药方单次量展示：方内各味 dose 合并为一行（空格分隔，按 sortNumber 排序）。
     *
     * @param perLineDosageDisplays 方内各味单次量展示
     * @return 展示文案；无有效单次量时返回 null
     */
    public String buildTcmPrescriptionDosageDisplay(List<String> perLineDosageDisplays) {
        if (perLineDosageDisplays == null || perLineDosageDisplays.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String dosageText : perLineDosageDisplays) {
            String trimmed = trimToNull(dosageText);
            if (trimmed == null) {
                continue;
            }
            if (!first) {
                builder.append(' ');
            }
            builder.append(trimmed);
            first = false;
        }
        if (builder.length() == 0) {
            return null;
        }
        return builder.toString();
    }

    /**
     * 按方内 sortNumber 收集各味药品展示名。
     *
     * @param bucket        同方折叠桶
     * @param medDetailById 药品明细索引
     * @param rowAccessor   行访问器
     * @return 排序后的名称列表
     */
    private <T> List<String> collectTcmHerbDisplayNames(
        List<T> bucket,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        TcmPrescriptionRowAccessor<T> rowAccessor) {
        List<T> sorted = new ArrayList<>(bucket);
        sorted.sort(Comparator
            .comparing(
                (T line) -> this.resolveMedSortNumber(line, medDetailById, rowAccessor),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(rowAccessor::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<String> names = new ArrayList<>();
        for (T line : sorted) {
            String name = trimToNull(rowAccessor.getAdviceDisplayName(line));
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    /**
     * 按方内 sortNumber 收集各味单次量展示。
     *
     * @param bucket        同方折叠桶
     * @param medDetailById 药品明细索引
     * @param rowAccessor   行访问器
     * @return 排序后的单次量列表
     */
    private <T> List<String> collectTcmHerbDosageDisplays(
        List<T> bucket,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        TcmPrescriptionRowAccessor<T> rowAccessor) {
        List<T> sorted = new ArrayList<>(bucket);
        sorted.sort(Comparator
            .comparing(
                (T line) -> this.resolveMedSortNumber(line, medDetailById, rowAccessor),
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(rowAccessor::getRequestId, Comparator.nullsLast(Comparator.naturalOrder())));
        List<String> dosages = new ArrayList<>();
        for (T line : sorted) {
            String dosage = trimToNull(rowAccessor.getDosageDisplay(line));
            if (dosage != null) {
                dosages.add(dosage);
            }
        }
        return dosages;
    }

    /**
     * 中药行排序号：优先关联 med_request.sortNumber，回落行内 sortNumber。
     *
     * @param line          处方行
     * @param medDetailById med_request 详情映射
     * @param rowAccessor   行字段访问器
     * @return 排序号
     */
    private <T> Integer resolveMedSortNumber(
        T line,
        Map<Long, MedicationRequestListQueryModel> medDetailById,
        TcmPrescriptionRowAccessor<T> rowAccessor) {
        Long requestId = rowAccessor.getRequestId(line);
        if (requestId != null) {
            MedicationRequestListQueryModel med = medDetailById.get(requestId);
            if (med != null && med.getSortNumber() != null) {
                return med.getSortNumber();
            }
        }
        return rowAccessor.getSortNumber(line);
    }

    /**
     * 药嘱 Tab 过滤：tcm 仅中药；west 仅非中药；all 全部命中。
     *
     * @param row 药嘱行
     * @param tab Tab 码
     * @return 是否命中
     */
    private boolean matchesMedicineCategoryTab(MedicationRequestListQueryModel row, String tab) {
        if (row == null) {
            return false;
        }
        if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_ALL.equals(tab)) {
            return true;
        }
        boolean tcm = Boolean.TRUE.equals(row.getTcmFlag());
        if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
            return tcm;
        }
        return !tcm;
    }

    /**
     * 中药方折叠时对异构行 DTO 的访问器。
     *
     * @param <T> 校对/执行列表行类型
     */
    public interface TcmPrescriptionRowAccessor<T> {

        Long getRequestId(T row);

        Long getEncounterId(T row);

        String getOrderType(T row);

        Integer getSortNumber(T row);

        /**
         * 单行医嘱名称摘要（折叠前已由装配层写入药品名）。
         *
         * @param row 列表行
         * @return 药品展示名；可 null
         */
        String getAdviceDisplayName(T row);

        /**
         * 单行单次量展示（折叠前已由装配层写入 dose+单位）。
         *
         * @param row 列表行
         * @return 单次量展示；可 null
         */
        String getDosageDisplay(T row);

        /**
         * 将方级字段写回代表行。
         *
         * @param representative          代表行
         * @param repMed                  代表行药品明细
         * @param lineRequestIds          方内全部 req id
         * @param adviceDisplayName       方级展示名
         * @param dosageDisplay           方级单次量展示（各味合并）
         * @param prescriptionOpenedTime  方级展示开立时间（方内最晚；可 null）
         */
        void applyTcmPrescriptionCollapse(
            T representative,
            MedicationRequestListQueryModel repMed,
            List<Long> lineRequestIds,
            String adviceDisplayName,
            String dosageDisplay,
            LocalDateTime prescriptionOpenedTime);
    }
}
