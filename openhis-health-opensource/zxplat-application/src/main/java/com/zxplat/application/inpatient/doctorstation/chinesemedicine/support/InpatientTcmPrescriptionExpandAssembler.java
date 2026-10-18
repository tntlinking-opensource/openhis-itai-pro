/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.doctorstation.chinesemedicine.support;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.zxplat.domain.medication.medicationrequest.queries.model.TcmHerbalPrescriptionGroupQueryModel;
import com.zxplat.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.zxplat.domain.share.constants.DoctorStationTcmConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.InpatientDoctorStationTcmConstants;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * IF-IP-TCM-38/40/41/42：按 prescription_no / prescriptionGroupKey 整方扩行支持。
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmPrescriptionExpandAssembler {

    private final MedicationRequestQueryService medicationRequestQueryService;

    /**
     * 将整方范围扩成同方全部中医行 id。
     *
     * @param encounterId          就诊主键
     * @param scope                  PRESCRIPTION/LINE
     * @param seedIds                种子行 id
     * @param prescriptionGroupKey   整方草稿 key
     * @param prescriptionNo       整方已签发号
     * @return 扩行后的 id 全集
     */
    public List<Long> expandToAllTcmLines(
        Long encounterId,
        String scope,
        List<Long> seedIds,
        String prescriptionGroupKey,
        String prescriptionNo
    ) {
        // 单行范围不扩方，直接返回种子 id
        if (!DoctorStationTcmConstants.SCOPE_PRESCRIPTION.equals(scope)) {
            return new ArrayList<>(seedIds);
        }
        List<TcmHerbalPrescriptionGroupQueryModel> groups = medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        Set<Long> expanded = new LinkedHashSet<>();
        if (seedIds != null) {
            expanded.addAll(seedIds);
        }
        String targetPrescriptionNo = trimToNull(prescriptionNo);
        String targetGroupKey = trimToNull(prescriptionGroupKey);
        // 遍历就诊下全部中药方，按处方号或草稿 groupKey 命中整方
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            boolean matched = false;
            // 已签发方按 prescription_no 匹配
            if (targetPrescriptionNo != null && targetPrescriptionNo.equals(trimToNull(group.getPrescriptionNo()))) {
                matched = true;
            }
            // 草稿方按 prescription_group_key 匹配
            if (!matched && targetGroupKey != null && targetGroupKey.equals(trimToNull(group.getPrescriptionGroupKey()))) {
                matched = true;
            }
            // 未命中当前目标方，跳过
            if (!matched) {
                continue;
            }
            if (group.getLines() != null) {
                for (MedicationRequestListQueryModel line : group.getLines()) {
                    if (line.getId() != null) {
                        expanded.add(line.getId());
                    }
                }
            }
        }
        return new ArrayList<>(expanded);
    }

    /**
     * 按处方号解析全部长期中医行 id。
     *
     * @param encounterId     就诊主键
     * @param prescriptionNos 处方号列表
     * @return 行 id 列表
     */
    public List<Long> resolveLongTermTcmRequestIdsByPrescriptionNos(Long encounterId, List<String> prescriptionNos) {
        return resolveTcmRequestIdsByPrescriptionNos(encounterId, prescriptionNos, TherapyType.LONG_TERM.getCode(), false);
    }

    /**
     * 按处方号解析全部已停嘱长期中医行 id。
     *
     * @param encounterId     就诊主键
     * @param prescriptionNos 处方号列表
     * @return 行 id 列表
     */
    public List<Long> resolveStoppedLongTermTcmRequestIdsByPrescriptionNos(Long encounterId, List<String> prescriptionNos) {
        return resolveTcmRequestIdsByPrescriptionNos(encounterId, prescriptionNos, TherapyType.LONG_TERM.getCode(), true);
    }

    /**
     * 按方解析全部中药行 id（改开立时间用，不限长临/状态）。
     *
     * <p>已签发方按 {@code prescription_no} 命中；草稿方按 {@code prescriptionGroupKey} 命中；
     * 两侧可同时传入，结果去重保序。</p>
     *
     * @param encounterId            就诊主键
     * @param prescriptionNos        已签发处方号；可空
     * @param prescriptionGroupKeys  草稿方标识；可空
     * @return 方内全部药品申请主键；无命中时为空列表
     */
    public List<Long> resolveAllTcmRequestIdsForOpenedTimeChange(
        Long encounterId,
        List<String> prescriptionNos,
        List<String> prescriptionGroupKeys) {
        Set<Long> result = new LinkedHashSet<>();
        Set<String> targetNos = new LinkedHashSet<>();
        if (prescriptionNos != null) {
            for (String prescriptionNo : prescriptionNos) {
                String trimmed = trimToNull(prescriptionNo);
                if (trimmed != null) {
                    targetNos.add(trimmed);
                }
            }
        }
        Set<String> targetKeys = new LinkedHashSet<>();
        if (prescriptionGroupKeys != null) {
            for (String groupKey : prescriptionGroupKeys) {
                String trimmed = trimToNull(groupKey);
                if (trimmed != null) {
                    targetKeys.add(trimmed);
                }
            }
        }
        // 两侧皆空则无需查库
        if (targetNos.isEmpty() && targetKeys.isEmpty()) {
            return new ArrayList<>();
        }
        List<TcmHerbalPrescriptionGroupQueryModel> groups = medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode());
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (group == null) {
                continue;
            }
            boolean matched = false;
            String groupNo = trimToNull(group.getPrescriptionNo());
            // 已签发方：处方号命中
            if (groupNo != null && targetNos.contains(groupNo)) {
                matched = true;
            }
            String groupKey = trimToNull(group.getPrescriptionGroupKey());
            // 草稿方：前端方标识命中
            if (!matched && groupKey != null && targetKeys.contains(groupKey)) {
                matched = true;
            }
            if (!matched || group.getLines() == null) {
                continue;
            }
            for (MedicationRequestListQueryModel line : group.getLines()) {
                if (line != null && line.getId() != null) {
                    result.add(line.getId());
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * 按处方号与长临/停嘱条件解析中药行 id 列表。
     *
     * @param encounterId     就诊主键
     * @param prescriptionNos 处方号列表
     * @param therapyEnum     长临筛选（如 LONG_TERM）
     * @param requireStopped  true 时仅接受 STOPPED 行（取消停嘱）；false 时拒绝草稿/已停嘱行（停嘱）
     * @return 符合条件的药品申请主键列表
     * @throws BusinessException 行状态不符合停嘱/取消停嘱前置条件时
     */
    private List<Long> resolveTcmRequestIdsByPrescriptionNos(
        Long encounterId,
        List<String> prescriptionNos,
        String therapyEnum,
        boolean requireStopped
    ) {
        List<Long> result = new ArrayList<>();
        if (prescriptionNos == null || prescriptionNos.isEmpty()) {
            return result;
        }
        Set<String> targetNos = new LinkedHashSet<>();
        for (String prescriptionNo : prescriptionNos) {
            if (StringUtils.isNotEmpty(prescriptionNo)) {
                targetNos.add(prescriptionNo.trim());
            }
        }
        List<TcmHerbalPrescriptionGroupQueryModel> groups = medicationRequestQueryService.listTcmHerbalPrescriptionsByEncounter(
            encounterId, GenerateSource.DOCTOR_PRESCRIPTION.getCode(), therapyEnum);
        for (TcmHerbalPrescriptionGroupQueryModel group : groups) {
            if (group.getPrescriptionNo() == null || !targetNos.contains(group.getPrescriptionNo().trim())) {
                continue;
            }
            if (group.getLines() == null) {
                continue;
            }
            for (MedicationRequestListQueryModel line : group.getLines()) {
                if (line.getId() == null) {
                    continue;
                }
                if (requireStopped) {
                    if (!RequestStatus.STOPPED.getCode().equals(line.getStatusEnum())) {
                        throw new BusinessException(
                            ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS,
                            "仅已停嘱的长期中药方可取消停嘱");
                    }
                } else {
                    if (RequestStatus.DRAFT.getCode().equals(line.getStatusEnum())) {
                        throw new BusinessException(
                            ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS,
                            InpatientDoctorStationTcmConstants.MSG_STOP_NOT_ALLOWED);
                    }
                    if (RequestStatus.STOPPED.getCode().equals(line.getStatusEnum())) {
                        throw new BusinessException(
                            ErrorCodeConstants.MEDICATION_REQUEST_INVALID_STATUS,
                            InpatientDoctorStationTcmConstants.MSG_STOP_NOT_ALLOWED);
                    }
                }
                result.add(line.getId());
            }
        }
        return result;
    }
}
