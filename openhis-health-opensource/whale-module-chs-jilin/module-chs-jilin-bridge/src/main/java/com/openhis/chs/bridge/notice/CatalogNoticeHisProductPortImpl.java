/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.bridge.notice;

import com.openhis.chs.domain.notice.model.HisProductLvChange;
import com.openhis.chs.domain.notice.port.CatalogNoticeHisProductPort;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.repository.DeviceDefinitionRepository;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.repository.MedicationDefinitionRepository;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.repository.ActivityDefinitionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 院内 MD/AD/DD {@code chrgitm_lv} 回写实现。
 * <p>按 yb_no / busNo 匹配；真改表字段。</p>
 */
@Component
@RequiredArgsConstructor
public class CatalogNoticeHisProductPortImpl implements CatalogNoticeHisProductPort {

    private final MedicationDefinitionRepository medicationDefinitionRepository;
    private final ActivityDefinitionRepository activityDefinitionRepository;
    private final DeviceDefinitionRepository deviceDefinitionRepository;

    /**
     * 按 yb_no / busNo 匹配 MD/AD/DD，回写 {@code chrgitm_lv} 并返回变更明细。
     *
     * @param tenantId    租户（预留；当前按全局产品定义匹配）
     * @param projectCode 项目码（可空）
     * @param hilistCode  国家目录编码（可空）
     * @param chrgitmLv   目标甲乙类
     * @return 实际发生变更的行；等级无法解析时返回空列表
     */
    @Override
    public List<HisProductLvChange> updateProductChrgitmLv(
            Long tenantId,
            String projectCode,
            String hilistCode,
            String chrgitmLv) {
        Integer targetLv = parseLv(chrgitmLv);
        if (targetLv == null) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        addCode(codes, hilistCode);
        addCode(codes, projectCode);
        if (codes.isEmpty()) {
            return List.of();
        }

        List<HisProductLvChange> changes = new ArrayList<>();
        updateMedications(codes, targetLv, changes);
        updateActivities(codes, targetLv, changes);
        updateDevices(codes, targetLv, changes);
        return changes;
    }

    private void updateMedications(Set<String> codes, Integer targetLv, List<HisProductLvChange> changes) {
        Specification<MedicationDefinition> spec = (root, cq, cb) -> {
            List<Predicate> orList = new ArrayList<>();
            for (String code : codes) {
                orList.add(cb.equal(root.get("ybNo"), code));
                orList.add(cb.equal(root.get("busNo"), code));
            }
            return cb.and(
                cb.isFalse(root.get("isDeleted")),
                cb.or(orList.toArray(new Predicate[0])));
        };
        List<MedicationDefinition> rows = medicationDefinitionRepository.findAll(spec);
        List<MedicationDefinition> dirty = new ArrayList<>();
        for (MedicationDefinition row : rows) {
            Integer before = row.getChrgitmLv();
            if (targetLv.equals(before)) {
                continue;
            }
            row.setChrgitmLv(targetLv);
            dirty.add(row);
            changes.add(toChange(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                row.getId(),
                before,
                targetLv));
        }
        if (!dirty.isEmpty()) {
            medicationDefinitionRepository.saveAll(dirty);
        }
    }

    private void updateActivities(Set<String> codes, Integer targetLv, List<HisProductLvChange> changes) {
        Specification<ActivityDefinition> spec = (root, cq, cb) -> {
            List<Predicate> orList = new ArrayList<>();
            for (String code : codes) {
                orList.add(cb.equal(root.get("ybNo"), code));
                orList.add(cb.equal(root.get("busNo"), code));
            }
            return cb.and(
                cb.isFalse(root.get("isDeleted")),
                cb.or(orList.toArray(new Predicate[0])));
        };
        List<ActivityDefinition> rows = activityDefinitionRepository.findAll(spec);
        List<ActivityDefinition> dirty = new ArrayList<>();
        for (ActivityDefinition row : rows) {
            Integer before = row.getChrgitmLv();
            if (targetLv.equals(before)) {
                continue;
            }
            row.setChrgitmLv(targetLv);
            dirty.add(row);
            changes.add(toChange(
                TableNameConstants.WKF_ACTIVITY_DEFINITION,
                row.getId(),
                before,
                targetLv));
        }
        if (!dirty.isEmpty()) {
            activityDefinitionRepository.saveAll(dirty);
        }
    }

    private void updateDevices(Set<String> codes, Integer targetLv, List<HisProductLvChange> changes) {
        Specification<DeviceDefinition> spec = (root, cq, cb) -> {
            List<Predicate> orList = new ArrayList<>();
            for (String code : codes) {
                orList.add(cb.equal(root.get("ybNo"), code));
                orList.add(cb.equal(root.get("busNo"), code));
            }
            return cb.and(
                cb.isFalse(root.get("isDeleted")),
                cb.or(orList.toArray(new Predicate[0])));
        };
        List<DeviceDefinition> rows = deviceDefinitionRepository.findAll(spec);
        List<DeviceDefinition> dirty = new ArrayList<>();
        for (DeviceDefinition row : rows) {
            Integer before = row.getChrgitmLv();
            if (targetLv.equals(before)) {
                continue;
            }
            row.setChrgitmLv(targetLv);
            dirty.add(row);
            changes.add(toChange(
                TableNameConstants.ADM_DEVICE_DEFINITION,
                row.getId(),
                before,
                targetLv));
        }
        if (!dirty.isEmpty()) {
            deviceDefinitionRepository.saveAll(dirty);
        }
    }

    /**
     * 按 yb_no / busNo 查找院内产品当前 {@code chrgitm_lv}（MD → AD → DD 优先序）。
     *
     * @param tenantId    租户（预留）
     * @param hilistCode  国家目录编码（可空）
     * @param projectCode 项目码（可空）
     * @return 首个非空等级的字符串形式；均无则 null
     */
    @Override
    public String findProductChrgitmLv(Long tenantId, String hilistCode, String projectCode) {
        Set<String> codes = new LinkedHashSet<>();
        addCode(codes, hilistCode);
        addCode(codes, projectCode);
        if (codes.isEmpty()) {
            return null;
        }
        Integer lv = findFirstMedicationLv(codes);
        if (lv == null) {
            lv = findFirstActivityLv(codes);
        }
        if (lv == null) {
            lv = findFirstDeviceLv(codes);
        }
        if (lv == null) {
            return null;
        }
        return String.valueOf(lv);
    }

    private Integer findFirstMedicationLv(Set<String> codes) {
        Specification<MedicationDefinition> spec = (root, cq, cb) -> {
            List<Predicate> orList = new ArrayList<>();
            for (String code : codes) {
                orList.add(cb.equal(root.get("ybNo"), code));
                orList.add(cb.equal(root.get("busNo"), code));
            }
            return cb.and(cb.isFalse(root.get("isDeleted")), cb.or(orList.toArray(new Predicate[0])));
        };
        for (MedicationDefinition row : medicationDefinitionRepository.findAll(spec)) {
            if (row.getChrgitmLv() != null) {
                return row.getChrgitmLv();
            }
        }
        return null;
    }

    private Integer findFirstActivityLv(Set<String> codes) {
        Specification<ActivityDefinition> spec = (root, cq, cb) -> {
            List<Predicate> orList = new ArrayList<>();
            for (String code : codes) {
                orList.add(cb.equal(root.get("ybNo"), code));
                orList.add(cb.equal(root.get("busNo"), code));
            }
            return cb.and(cb.isFalse(root.get("isDeleted")), cb.or(orList.toArray(new Predicate[0])));
        };
        for (ActivityDefinition row : activityDefinitionRepository.findAll(spec)) {
            if (row.getChrgitmLv() != null) {
                return row.getChrgitmLv();
            }
        }
        return null;
    }

    private Integer findFirstDeviceLv(Set<String> codes) {
        Specification<DeviceDefinition> spec = (root, cq, cb) -> {
            List<Predicate> orList = new ArrayList<>();
            for (String code : codes) {
                orList.add(cb.equal(root.get("ybNo"), code));
                orList.add(cb.equal(root.get("busNo"), code));
            }
            return cb.and(cb.isFalse(root.get("isDeleted")), cb.or(orList.toArray(new Predicate[0])));
        };
        for (DeviceDefinition row : deviceDefinitionRepository.findAll(spec)) {
            if (row.getChrgitmLv() != null) {
                return row.getChrgitmLv();
            }
        }
        return null;
    }

    private static HisProductLvChange toChange(
            String table, Long id, Integer before, Integer after) {
        HisProductLvChange change = new HisProductLvChange();
        change.setTargetTable(table);
        change.setTargetId(id);
        if (before == null) {
            change.setBeforeLv(null);
        } else {
            change.setBeforeLv(String.valueOf(before));
        }
        change.setAfterLv(String.valueOf(after));
        return change;
    }

    /**
     * 解析医保等级并映射到院内 {@code chrgitm_lv}（与 {@code yb_type} 一致：1自费/2甲/3乙/4丙）。
     * <p>通知/国标常见为 1/01=甲、2/02=乙、3/03=丙，需 +1 映射；已是院内 1～4 则原样落库。</p>
     */
    private static Integer parseLv(String chrgitmLv) {
        if (chrgitmLv == null) {
            return null;
        }
        String trimmed = chrgitmLv.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            int raw = Integer.parseInt(trimmed);
            // 国标三位码 01/02/03 经 parseInt 后为 1/2/3
            if (raw >= 1 && raw <= 3) {
                return raw + 1;
            }
            if (raw >= 1 && raw <= 4) {
                return raw;
            }
            return null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void addCode(Set<String> codes, String code) {
        if (code == null) {
            return;
        }
        String trimmed = code.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        codes.add(trimmed);
    }
}
