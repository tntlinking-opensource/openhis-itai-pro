/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.share;

import com.zxplat.plugin.campus.domain.archivechangelog.entity.ArchiveChangeLog;
import com.zxplat.plugin.campus.domain.archivechangelog.service.ArchiveChangeLogDomainService;
import com.zxplat.plugin.campus.domain.shared.CampusDomainConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * 档案变更记录编排辅助。
 */
@Component
@RequiredArgsConstructor
public class CampusArchiveChangeLogSupport {

    private final ArchiveChangeLogDomainService archiveChangeLogDomainService;

    public List<ArchiveChangeLog> appendCreateBatch(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        String campusCode,
        Long operatorId) {
        ArchiveChangeLog entry = baseEntry(tenantId, subjectTypeEnum, subjectId, campusCode);
        entry.setChangeTypeEnum(CampusArchiveApplicationConstants.CHANGE_TYPE_CREATE);
        entry.setFieldName(CampusArchiveApplicationConstants.FIELD_WILDCARD);
        entry.setNewValue("created");
        entry.setChangeSourceEnum(CampusArchiveApplicationConstants.CHANGE_SOURCE_MANUAL);
        return archiveChangeLogDomainService.appendBatch(List.of(entry), operatorId);
    }

    public List<ArchiveChangeLog> appendFieldUpdates(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        String campusCode,
        String changeSourceEnum,
        List<FieldChange> changes,
        Long operatorId) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        List<ArchiveChangeLog> entries = new ArrayList<>();
        for (FieldChange change : changes) {
            ArchiveChangeLog entry = baseEntry(tenantId, subjectTypeEnum, subjectId, campusCode);
            entry.setChangeTypeEnum(CampusArchiveApplicationConstants.CHANGE_TYPE_UPDATE);
            entry.setFieldName(change.fieldName());
            entry.setOldValue(change.oldValue());
            entry.setNewValue(change.newValue());
            entry.setChangeSourceEnum(changeSourceEnum);
            entries.add(entry);
        }
        return archiveChangeLogDomainService.appendBatch(entries, operatorId);
    }

    public List<ArchiveChangeLog> appendSyncBatch(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        String campusCode,
        List<FieldChange> changes,
        Long operatorId) {
        if (changes == null || changes.isEmpty()) {
            return List.of();
        }
        List<ArchiveChangeLog> entries = new ArrayList<>();
        for (FieldChange change : changes) {
            ArchiveChangeLog entry = baseEntry(tenantId, subjectTypeEnum, subjectId, campusCode);
            entry.setChangeTypeEnum(CampusArchiveApplicationConstants.CHANGE_TYPE_SYNC);
            entry.setFieldName(change.fieldName());
            entry.setOldValue(change.oldValue());
            entry.setNewValue(change.newValue());
            entry.setChangeSourceEnum(CampusArchiveApplicationConstants.CHANGE_SOURCE_SYNC);
            entries.add(entry);
        }
        return archiveChangeLogDomainService.appendBatch(entries, operatorId);
    }

    public static <T> FieldChange diff(String fieldName, T oldValue, T newValue, Function<T, String> formatter) {
        if (Objects.equals(oldValue, newValue)) {
            return null;
        }
        return new FieldChange(
            fieldName,
            oldValue == null ? null : formatter.apply(oldValue),
            newValue == null ? null : formatter.apply(newValue));
    }

    public static FieldChange diffString(String fieldName, String oldValue, String newValue) {
        return diff(fieldName, oldValue, newValue, Function.identity());
    }

    public static FieldChange diffLong(String fieldName, Long oldValue, Long newValue) {
        return diff(fieldName, oldValue, newValue, String::valueOf);
    }

    public static List<FieldChange> collect(FieldChange... changes) {
        List<FieldChange> result = new ArrayList<>();
        if (changes == null) {
            return result;
        }
        for (FieldChange change : changes) {
            if (change != null) {
                result.add(change);
            }
        }
        return result;
    }

    public static String resolveStudentTransferType(
        String fromCampus,
        String toCampus,
        Long fromCollegeId,
        Long toCollegeId) {
        boolean campusChanged = !Objects.equals(fromCampus, toCampus);
        boolean collegeChanged = !Objects.equals(fromCollegeId, toCollegeId);
        if (campusChanged && collegeChanged) {
            return CampusArchiveApplicationConstants.TRANSFER_TYPE_CAMPUS_COLLEGE;
        }
        if (campusChanged) {
            return CampusArchiveApplicationConstants.TRANSFER_TYPE_CAMPUS;
        }
        if (collegeChanged) {
            return CampusArchiveApplicationConstants.TRANSFER_TYPE_COLLEGE;
        }
        return CampusArchiveApplicationConstants.TRANSFER_TYPE_CAMPUS_COLLEGE;
    }

    public static String resolveStaffTransferType(Long fromCampusSiteId, Long toCampusSiteId, Long fromDept, Long toDept) {
        boolean campusChanged = !Objects.equals(fromCampusSiteId, toCampusSiteId);
        boolean deptChanged = !Objects.equals(fromDept, toDept);
        if (campusChanged && deptChanged) {
            return CampusArchiveApplicationConstants.TRANSFER_TYPE_CAMPUS_DEPARTMENT;
        }
        if (campusChanged) {
            return CampusArchiveApplicationConstants.TRANSFER_TYPE_CAMPUS;
        }
        if (deptChanged) {
            return CampusArchiveApplicationConstants.TRANSFER_TYPE_DEPARTMENT;
        }
        return CampusArchiveApplicationConstants.TRANSFER_TYPE_DEPARTMENT;
    }

    private ArchiveChangeLog baseEntry(
        Long tenantId,
        String subjectTypeEnum,
        Long subjectId,
        String campusCode) {
        ArchiveChangeLog entry = new ArchiveChangeLog();
        entry.setTenantId(tenantId);
        entry.setSubjectTypeEnum(subjectTypeEnum);
        entry.setSubjectId(subjectId);
        entry.setCampusCode(campusCode);
        return entry;
    }

    public record FieldChange(String fieldName, String oldValue, String newValue) {
    }
}
