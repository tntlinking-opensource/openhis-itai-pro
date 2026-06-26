/*
 * Copyright © 2026 OpenHIS Contributors
 */
package com.openhis.health.module.querysupport.nursestation;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.module.querysupport.order.dto.InpatientOrderListSkeleton;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.ReadModelOrderTypeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 住院护士站中药 Tab：中西药筛选、按 {@code prescription_no} 整方扩行与列表折叠（AS-IP-DOR-006 · BR-IP-TCM 下游）。
 */
@Component
public class InpatientNurseStationTcmSupport {

    /**
     * 解析中西药 Tab，非法值抛业务异常。
     *
     * @param medicineCategoryTab west / tcm；空则默认 west
     * @return 规范化 tab 码
     */
    public String resolveMedicineCategoryTabOrDefault(String medicineCategoryTab) {
        String tab = trimToEmpty(medicineCategoryTab);
        if (StringUtils.isBlank(tab)) {
            return InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST;
        }
        if (InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_WEST.equals(tab)
            || InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(tab)) {
            return tab;
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "中西药 Tab 不合法");
    }

    /**
     * 是否中药 Tab。
     *
     * @param medicineCategoryTab west / tcm
     * @return true 表示 tcm Tab
     */
    public boolean isTcmTab(String medicineCategoryTab) {
        return InpatientNurseStationConstants.MEDICINE_CATEGORY_TAB_TCM.equals(
            resolveMedicineCategoryTabOrDefault(medicineCategoryTab));
    }

    /**
     * 中药 Tab 下是否展示诊疗医嘱（中药 Tab 仅药嘱方级行）。
     *
     * @param medicineCategoryTab west / tcm
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
        if (!isTcmTab(medicineCategoryTab)) {
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
            // 代表行取 sortNumber 最小或 id 最小
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
            List<String> herbNames = this.collectTcmHerbDisplayNames(bucket, medDetailById, rowAccessor);
            List<String> herbDosages = this.collectTcmHerbDosageDisplays(bucket, medDetailById, rowAccessor);
            String displayName = buildTcmPrescriptionDisplayName(herbNames);
            String dosageDisplay = buildTcmPrescriptionDosageDisplay(herbDosages);
            rowAccessor.applyTcmPrescriptionCollapse(
                representative, repMed, lineIds, displayName, dosageDisplay);
            result.add(representative);
        }
        return result;
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

    private boolean matchesMedicineCategoryTab(MedicationRequestListQueryModel row, String tab) {
        if (row == null) {
            return false;
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
         * @param representative 代表行
         * @param repMed         代表行药品明细
         * @param lineRequestIds 方内全部 req id
         * @param adviceDisplayName 方级展示名
         * @param dosageDisplay 方级单次量展示（各味合并）
         */
        void applyTcmPrescriptionCollapse(
            T representative,
            MedicationRequestListQueryModel repMed,
            List<Long> lineRequestIds,
            String adviceDisplayName,
            String dosageDisplay);
    }
}
