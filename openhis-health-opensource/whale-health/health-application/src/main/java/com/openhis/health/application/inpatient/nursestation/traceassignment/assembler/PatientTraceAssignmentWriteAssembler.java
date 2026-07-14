/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.traceassignment.assembler;

import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupDto;
import com.openhis.health.application.inpatient.nursestation.traceassignment.dto.PatientTraceAssignmentGroupSubmitDto;
import com.openhis.health.domain.medication.medicationdispense.command.AssignTraceNoToCompletedDispenseCommand;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 住院护士站追溯码分配写路径装配（**AS-IP-NUR-015 · IF-IP-TRC-03/04**）。
 */
@Component
public class PatientTraceAssignmentWriteAssembler {

    /**
     * 追溯码有效长度（与 {@link InpatientNurseStationConstants#TRACE_CODE_LENGTH} 同族）。
     */
    public static final int TRACE_CODE_LENGTH = InpatientNurseStationConstants.TRACE_CODE_LENGTH;

    /**
     * 规范化追溯码列表：去空白、校验 20 位数字（**BR-IP-TRC-014**）。
     *
     * @param traceCodes 原始码列表
     * @return 规范化后的独立码列表
     * @throws BusinessException 格式非法时 {@link InpatientNurseStationConstants#EX_IP_TRC_202}
     */
    public List<String> normalizeTraceCodes(List<String> traceCodes) {
        List<String> normalized = new ArrayList<>();
        if (traceCodes == null) {
            return normalized;
        }
        Set<String> dedupe = new LinkedHashSet<>();
        for (String raw : traceCodes) {
            String code = trimToNull(raw);
            if (code == null) {
                continue;
            }
            if (code.length() != TRACE_CODE_LENGTH || StringUtils.isEmpty(code) || !code.chars().allMatch(Character::isDigit)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_202,
                    "追溯码须为 20 位有效编码");
            }
            dedupe.add(code);
        }
        normalized.addAll(dedupe);
        return normalized;
    }

    /**
     * 将逗号分隔追溯码串规范化为独立码列表。
     *
     * @param traceNo 逗号分隔串
     * @return 独立码列表；空白输入返回空表
     */
    public List<String> normalizeTraceNoString(String traceNo) {
        if (StringUtils.isEmpty(traceNo)) {
            return List.of();
        }
        List<String> rawParts = new ArrayList<>();
        for (String part : traceNo.split(GeneralSharedConstants.COMMA)) {
            rawParts.add(part);
        }
        return this.normalizeTraceCodes(rawParts);
    }

    /**
     * 断言汇总组追溯码数量不超过发药量上限（**BR-IP-TRC-008**）。
     *
     * @param groupDtos 含 maxTraceCodeCount 的组 DTO
     * @param traceNoByGroupKey groupKey → 逗号分隔追溯码
     * @throws BusinessException 超限时 {@link InpatientNurseStationConstants#EX_IP_TRC_208}
     */
    public void assertTraceCountWithinLimit(
        List<PatientTraceAssignmentGroupDto> groupDtos,
        Map<String, String> traceNoByGroupKey) {
        if (groupDtos == null || traceNoByGroupKey == null) {
            return;
        }
        for (PatientTraceAssignmentGroupDto group : groupDtos) {
            if (group == null || StringUtils.isEmpty(group.getGroupKey())) {
                continue;
            }
            String traceNo = traceNoByGroupKey.get(group.getGroupKey());
            if (StringUtils.isEmpty(traceNo)) {
                continue;
            }
            List<String> codes = this.normalizeTraceNoString(traceNo);
            Integer maxCount = group.getMaxTraceCodeCount();
            if (maxCount == null) {
                maxCount = 1;
            }
            if (codes.size() > maxCount) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_208,
                    "追溯码数量不能超过发药数量");
            }
        }
    }

    /**
     * 断言底层发放行均为汇总路径已核发态（**BR-IP-TRC-002**）。
     *
     * @param dispenses 强加载发放行
     * @throws BusinessException 状态非法时 {@link InpatientNurseStationConstants#EX_IP_TRC_207}
     */
    public void assertAllCompletedWithSummary(List<MedicationDispense> dispenses) {
        if (dispenses == null) {
            return;
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                continue;
            }
            if (!DispenseStatus.COMPLETED.equals(dispense.getStatusEnum())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_207,
                    "药品尚未核发完成，无法分配");
            }
            if (StringUtils.isEmpty(dispense.getSummaryNo())) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_207,
                    "药品尚未核发完成，无法分配");
            }
        }
    }

    /**
     * 断言各组批号非空（**EX-IP-TRC-203**）。
     *
     * @param submitGroups 待保存组
     * @throws BusinessException 批号缺失时
     */
    public void assertLotNumberPresent(List<PatientTraceAssignmentGroupSubmitDto> submitGroups) {
        if (submitGroups == null) {
            return;
        }
        for (PatientTraceAssignmentGroupSubmitDto group : submitGroups) {
            if (group == null || StringUtils.isEmpty(group.getGroupKey())) {
                continue;
            }
            String lotNumber = this.extractLotNumberFromGroupKey(group.getGroupKey());
            if (StringUtils.isEmpty(lotNumber)) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_203,
                    "该药品批号缺失，无法分配追溯码");
            }
        }
    }

    /**
     * 码池为空且用户提交了非空码时拒绝（**BR-IP-TRC-013**）。
     *
     * @param traceCodesBySummary summaryNo → 码列表
     * @param poolBySummary       summaryNo → 码池
     * @throws BusinessException 空池提交码时 {@link InpatientNurseStationConstants#EX_IP_TRC_204}
     */
    public void assertPoolPresentWhenCodesSubmitted(
        Map<String, List<String>> traceCodesBySummary,
        Map<String, Set<String>> poolBySummary) {
        if (traceCodesBySummary == null) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : traceCodesBySummary.entrySet()) {
            List<String> codes = entry.getValue();
            if (codes == null || codes.isEmpty()) {
                continue;
            }
            Set<String> pool = null;
            if (poolBySummary != null) {
                pool = poolBySummary.get(entry.getKey());
            }
            if (pool == null || pool.isEmpty()) {
                throw new BusinessException(
                    InpatientNurseStationConstants.EX_IP_TRC_204,
                    "药房未录入汇总追溯码，暂无法保存");
            }
        }
    }

    /**
     * 单汇总组提交载荷 → 领域分配命令（**BR-IP-TRC-010** 组内相同 trace_no）。
     *
     * @param submitGroup 组提交载荷
     * @param encounterId 就诊锚点
     * @param operatorId  操作人
     * @return 领域命令；trace 为空时 items 为空
     */
    public AssignTraceNoToCompletedDispenseCommand toAssignTraceNoCommand(
        PatientTraceAssignmentGroupSubmitDto submitGroup,
        Long encounterId,
        Long operatorId) {
        AssignTraceNoToCompletedDispenseCommand command = new AssignTraceNoToCompletedDispenseCommand();
        command.setOperatorId(operatorId);
        command.setEncounterId(encounterId);
        List<AssignTraceNoToCompletedDispenseCommand.Item> items = new ArrayList<>();
        if (submitGroup == null || StringUtils.isEmpty(submitGroup.getTraceNo())) {
            command.setItems(items);
            return command;
        }
        List<String> codes = this.normalizeTraceNoString(submitGroup.getTraceNo());
        if (codes.isEmpty()) {
            command.setItems(items);
            return command;
        }
        String joinedTraceNo = String.join(GeneralSharedConstants.COMMA, codes);
        if (submitGroup.getDispenseIds() != null) {
            for (Long dispenseId : submitGroup.getDispenseIds()) {
                if (dispenseId == null) {
                    continue;
                }
                AssignTraceNoToCompletedDispenseCommand.Item item = new AssignTraceNoToCompletedDispenseCommand.Item();
                item.setId(dispenseId);
                item.setTraceNo(joinedTraceNo);
                items.add(item);
            }
        }
        command.setItems(items);
        return command;
    }

    /**
     * 将组提交列表按 summaryNo 聚合追溯码，供码池校验。
     *
     * @param submitGroups  待保存组
     * @param groupKeyIndex groupKey → 汇总组 DTO
     * @return summaryNo → 码列表
     */
    public Map<String, List<String>> buildTraceCodesBySummary(
        List<PatientTraceAssignmentGroupSubmitDto> submitGroups,
        Map<String, PatientTraceAssignmentGroupDto> groupKeyIndex) {
        Map<String, List<String>> traceCodesBySummary = new HashMap<>();
        if (submitGroups == null) {
            return traceCodesBySummary;
        }
        for (PatientTraceAssignmentGroupSubmitDto submitGroup : submitGroups) {
            if (submitGroup == null || StringUtils.isEmpty(submitGroup.getTraceNo())) {
                continue;
            }
            PatientTraceAssignmentGroupDto groupDto = null;
            if (groupKeyIndex != null) {
                groupDto = groupKeyIndex.get(submitGroup.getGroupKey());
            }
            String summaryNo = null;
            if (groupDto != null) {
                summaryNo = groupDto.getSummaryNo();
            }
            if (StringUtils.isEmpty(summaryNo)) {
                summaryNo = this.extractSummaryNoFromGroupKey(submitGroup.getGroupKey());
            }
            if (StringUtils.isEmpty(summaryNo)) {
                continue;
            }
            List<String> codes = this.normalizeTraceNoString(submitGroup.getTraceNo());
            if (codes.isEmpty()) {
                continue;
            }
            List<String> bucket = traceCodesBySummary.computeIfAbsent(summaryNo, key -> new ArrayList<>());
            bucket.addAll(codes);
        }
        return traceCodesBySummary;
    }

    /**
     * 从组键 {@code summaryNo:medicationId:lotNumber} 解析批号段。
     *
     * @param groupKey 汇总组键
     * @return 批号段；键格式非法时 null
     */
    private String extractLotNumberFromGroupKey(String groupKey) {
        if (StringUtils.isEmpty(groupKey)) {
            return null;
        }
        String[] parts = groupKey.split(":", 3);
        if (parts.length < 3) {
            return null;
        }
        return parts[2];
    }

    /**
     * 从组键解析汇总单号段。
     *
     * @param groupKey 汇总组键
     * @return 汇总单号；键格式非法时 null
     */
    private String extractSummaryNoFromGroupKey(String groupKey) {
        if (StringUtils.isEmpty(groupKey)) {
            return null;
        }
        String[] parts = groupKey.split(":", 3);
        if (parts.length < 1) {
            return null;
        }
        return parts[0];
    }
}
