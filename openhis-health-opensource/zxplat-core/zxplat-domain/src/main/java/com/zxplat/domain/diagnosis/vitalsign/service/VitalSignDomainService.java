/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.diagnosis.vitalsign.service;

import com.zxplat.domain.diagnosis.vitalsign.command.DeleteVitalSignByIdCommand;
import com.zxplat.domain.diagnosis.vitalsign.command.DeleteVitalSignByIdsCommand;
import com.zxplat.domain.diagnosis.vitalsign.command.SaveVitalSignCommand;
import com.zxplat.domain.diagnosis.vitalsign.command.SaveVitalSignDetailItemCommand;
import com.zxplat.domain.diagnosis.vitalsign.entity.VitalSign;
import com.zxplat.domain.diagnosis.vitalsign.entity.VitalSignDetail;
import com.zxplat.domain.diagnosis.vitalsign.repository.VitalSignDetailRepository;
import com.zxplat.domain.diagnosis.vitalsign.repository.VitalSignRepository;
import com.zxplat.domain.diagnosis.vitalsign.support.VitalSignDetailSupport;
import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.constants.BusinessSequenceConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.events.diagnosis.vitalsign.VitalSignDeletedEvent;
import com.zxplat.domain.share.events.diagnosis.vitalsign.VitalSignSavedEvent;
import com.zxplat.domain.terminology.entity.Concept;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.domain.event.IDomainEventPublisher;
import com.zxplat.framework.core.infrastructure.sequence.BusinessSequenceService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 生命体征（{@code dia_vital_sign} + 明细子表）聚合写侧领域服务（DM-DIA-002 v2.1 §6.1）。
 *
 * <p>职责：主表 upsert、明细覆盖写、软删及 bus_no 唯一校验。</p>
 * <p>不变量：患者/就诊绑定（BR-VS-001）；字典项须启用（BR-VS-003）；持久化先于 {@link VitalSignSavedEvent}。</p>
 * <p>跨聚合读术语字典经 {@link CodeSystemQueryService}（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class VitalSignDomainService {

    private final VitalSignRepository headerRepository;
    private final VitalSignDetailRepository detailRepository;
    private final CodeSystemQueryService codeSystemQueryService;
    private final BusinessSequenceService businessSequenceService;
    private final IDomainEventPublisher domainEventPublisher;

    /**
     * 保存生命体征单据（新增或更新主表及明细，TC-DIA-VS-001）。
     *
     * @param cmd 主表头 + 明细项
     * @return 主表 id
     */
    public Long saveVitalSign(SaveVitalSignCommand cmd) {
        // Gate：命令头必填项校验
        if (cmd == null || cmd.getEncounterId() == null || cmd.getPatientId() == null || cmd.getRecordedAt() == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "就诊、患者与记录时点不能为空");
        }
        if (StringUtils.isEmpty(cmd.getTypeEnum())) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "单据类型不能为空");
        }
        VitalSign.assertPatientEncounterBound(cmd.getPatientId(), cmd.getEncounterId());
        VitalSign.assertTypeEnumValid(cmd.getTypeEnum());

        List<SaveVitalSignDetailItemCommand> items = cmd.getItems();
        if (items == null) {
            items = List.of();
        }
        VitalSign.assertHasBusinessContent(cmd.getTypeEnum(), cmd.getRemark(), items.size());
        assertActiveDetailCodes(items);
        assertNoDuplicateDetailKey(items);

        Map<String, String> defaultUnitsByCode = loadDefaultUnitsByCode(
            TerminologyCodeSystemNameConstants.VITAL_SIGNS_CODE);

        // 加载/组装：主表 upsert
        VitalSign header = loadOrCreateHeader(cmd);
        header.setPatientId(cmd.getPatientId());
        header.setEncounterId(cmd.getEncounterId());
        header.setRecordedAt(cmd.getRecordedAt());
        header.setTypeEnum(cmd.getTypeEnum());
        header.setLocationId(cmd.getLocationId());
        header.setBabyNo(cmd.getBabyNo());
        header.setRecorderId(cmd.getRecorderId());
        header.setRemark(cmd.getRemark());
        header.assertSamePatientEncounter(cmd.getPatientId(), cmd.getEncounterId());
        header.assertBusNoNotBlank();
        assertBusNoUnique(header);

        VitalSign savedHeader = headerRepository.save(header);

        // 明细覆盖写：未提交自然键软删 + upsert
        List<VitalSignDetail> existingDetails =
            detailRepository.findAllByVitalSignIdAndIsDeletedFalseOrderByTermCodeAscItemCodeAsc(
                savedHeader.getId());
        Set<String> submittedKeys = new HashSet<>();
        List<VitalSignDetail> detailsToSave = new ArrayList<>();

        for (SaveVitalSignDetailItemCommand item : items) {
            submittedKeys.add(VitalSignDetailSupport.buildNaturalKey(item));
        }
        for (VitalSignDetail existing : existingDetails) {
            // 业务分支：未出现在本次提交集的自然键软删（主表级覆盖写，BR-VS-005）
            if (!submittedKeys.contains(VitalSignDetailSupport.buildNaturalKey(existing))) {
                existing.markDeleted(cmd.getOperatorId());
                detailsToSave.add(existing);
            }
        }
        for (SaveVitalSignDetailItemCommand item : items) {
            String termCode = VitalSignDetailSupport.normalizeTermCode(item.getTermCode());
            VitalSignDetail detail = null;
            for (VitalSignDetail existing : existingDetails) {
                if (Objects.equals(existing.getTermCode(), termCode)
                    && Objects.equals(existing.getItemCode(), item.getItemCode())) {
                    detail = existing;
                    break;
                }
            }
            // 业务分支：同自然键已有行则 upsert，否则新增明细行
            if (detail == null) {
                detail = new VitalSignDetail();
                detail.setVitalSignId(savedHeader.getId());
            }
            detail.setTermCode(termCode);
            detail.setItemCode(item.getItemCode());
            detail.setItemValue(item.getItemValue());
            detail.setUnits(resolveUnits(item, defaultUnitsByCode));
            detail.setRemark(trimToNull(item.getRemark()));
            detail.assertTermCodeNotBlank();
            detail.assertItemCodeNotBlank();
            detail.assertItemValueNotBlank();
            detailsToSave.add(detail);
        }

        List<VitalSignDetail> savedDetails = detailRepository.saveAll(detailsToSave);
        List<Long> savedDetailIds = new ArrayList<>();
        for (VitalSignDetail detail : savedDetails) {
            if (!Boolean.TRUE.equals(detail.getIsDeleted()) && detail.getId() != null) {
                savedDetailIds.add(detail.getId());
            }
        }

        VitalSignSavedEvent event = new VitalSignSavedEvent(
            savedHeader.getId(), savedHeader.getEncounterId(), savedHeader.getPatientId(),
            savedHeader.getRecordedAt(), savedHeader.getTypeEnum(), savedDetailIds);
        event.setTenantId(savedHeader.getTenantId());
        // 发布事件：须在主表与明细持久化成功后（beforeCommit）
        domainEventPublisher.publish(event);
        return savedHeader.getId();
    }

    /**
     * 按主表 id 软删单据及全部明细（TC-DIA-VS-002）。
     *
     * @param cmd 主表 id + 操作人
     */
    public void deleteVitalSignById(DeleteVitalSignByIdCommand cmd) {
        // Gate：删除主键必填
        if (cmd == null || cmd.getId() == null) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "删除主键不能为空");
        }
        VitalSign header = headerRepository.findByIdAndIsDeletedFalse(cmd.getId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.VITAL_SIGN_NOT_FOUND, "生命体征单据不存在"));
        // 持久化：先软删明细再软删主表（BR-VS-004 级联）
        List<Long> deletedDetailIds = softDeleteDetails(header.getId(), cmd.getOperatorId());
        header.markDeleted(cmd.getOperatorId());
        headerRepository.save(header);
        publishDeletedEvent(header, List.of(header.getId()), deletedDetailIds);
    }

    /**
     * 批量按主表 id 软删（TC-DIA-VS-003）。
     *
     * @param cmd 主表 id 列表 + 操作人
     */
    public void deleteVitalSignByIds(DeleteVitalSignByIdsCommand cmd) {
        // Gate：批量删除 id 列表非空
        if (cmd == null || cmd.getIds() == null || cmd.getIds().isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "删除主键不能为空");
        }
        List<VitalSign> headers = headerRepository.findAllByIdInAndIsDeletedFalse(cmd.getIds());
        // 业务分支：任一 id 无效或已删则整批拒绝，避免部分删除
        if (headers.size() != cmd.getIds().size()) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_NOT_FOUND, "存在无效或已删的生命体征主键");
        }
        List<Long> deletedIds = new ArrayList<>();
        List<Long> deletedDetailIds = new ArrayList<>();
        for (VitalSign header : headers) {
            deletedDetailIds.addAll(softDeleteDetails(header.getId(), cmd.getOperatorId()));
            header.markDeleted(cmd.getOperatorId());
            deletedIds.add(header.getId());
        }
        headerRepository.saveAll(headers);
        VitalSign first = headers.get(0);
        publishDeletedEvent(first, deletedIds, deletedDetailIds);
    }

    /**
     * 加载已有主表或创建新行。
     *
     * @param cmd 保存命令
     * @return 主表实体
     */
    private VitalSign loadOrCreateHeader(SaveVitalSignCommand cmd) {
        // 业务分支：带 id 为更新路径，须加载有效主表
        if (cmd.getId() != null) {
            return headerRepository.findByIdAndIsDeletedFalse(cmd.getId())
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.VITAL_SIGN_NOT_FOUND, "生命体征单据不存在"));
        }
        VitalSign header = new VitalSign();
        header.setBusNo(businessSequenceService.getSeqByDay(
            AggregateTypeConstants.VITAL_SIGN, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH));
        return header;
    }

    /**
     * 校验 bus_no 租户内唯一（BR-VS-002）。
     *
     * @param header 主表
     */
    private void assertBusNoUnique(VitalSign header) {
        Long tenantId = header.getTenantId();
        if (tenantId == null) {
            return;
        }
        if (header.getId() == null) {
            if (headerRepository.existsByTenantIdAndBusNoAndIsDeletedFalse(tenantId, header.getBusNo())) {
                throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "业务单号已存在");
            }
            return;
        }
        if (headerRepository.existsByTenantIdAndBusNoAndIsDeletedFalseAndIdNot(tenantId, header.getBusNo(), header.getId())) {
            throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "业务单号已存在");
        }
    }

    /**
     * 软删主表下全部有效明细。
     *
     * @param vitalSignId 主表 id
     * @param operatorId  操作人
     * @return 已删明细 id 列表
     */
    private List<Long> softDeleteDetails(Long vitalSignId, Long operatorId) {
        List<VitalSignDetail> details =
            detailRepository.findAllByVitalSignIdAndIsDeletedFalseOrderByTermCodeAscItemCodeAsc(vitalSignId);
        List<Long> deletedDetailIds = new ArrayList<>();
        for (VitalSignDetail detail : details) {
            detail.markDeleted(operatorId);
            deletedDetailIds.add(detail.getId());
        }
        if (!details.isEmpty()) {
            detailRepository.saveAll(details);
        }
        return deletedDetailIds;
    }

    /**
     * 发布删除事件。
     *
     * @param header            上下文主表
     * @param deletedHeaderIds  已删主表 id
     * @param deletedDetailIds  已删明细 id
     */
    private void publishDeletedEvent(VitalSign header, List<Long> deletedHeaderIds, List<Long> deletedDetailIds) {
        VitalSignDeletedEvent event = new VitalSignDeletedEvent(
            header.getId(), header.getEncounterId(), header.getRecordedAt(), deletedHeaderIds, deletedDetailIds);
        event.setTenantId(header.getTenantId());
        domainEventPublisher.publish(event);
    }

    /**
     * 校验明细字典项启用（BR-VS-003）。
     *
     * @param items 明细命令
     */
    private void assertActiveDetailCodes(List<SaveVitalSignDetailItemCommand> items) {
        Map<String, Set<String>> activeCodesBySystem = new HashMap<>();
        for (SaveVitalSignDetailItemCommand item : items) {
            if (item == null || StringUtils.isEmpty(item.getItemCode())) {
                throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT, "字典项编码不能为空");
            }
            String termCode = VitalSignDetailSupport.normalizeTermCode(item.getTermCode());
            Set<String> activeCodes = activeCodesBySystem.computeIfAbsent(
                termCode, this::loadActiveCodesForTerm);
            if (!activeCodes.contains(item.getItemCode())) {
                throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_CODE,
                    "字典项编码未启用或不存在: " + termCode + "/" + item.getItemCode());
            }
        }
    }

    /**
     * 加载指定术语体系下启用的字典项 code。
     *
     * @param termCode 术语体系 code
     * @return 启用 code 集合
     */
    private Set<String> loadActiveCodesForTerm(String termCode) {
        // 加载候选：经 CodeSystemQueryService 取启用字典项（B.6.4）
        List<OptionDto> options = codeSystemQueryService.getOptionListByCodeSystemName(termCode);
        Set<String> activeCodes = new HashSet<>();
        for (OptionDto option : options) {
            activeCodes.add(option.getValue());
        }
        return activeCodes;
    }

    /**
     * 断言明细自然键不重复。
     *
     * @param items 明细命令
     */
    private static void assertNoDuplicateDetailKey(List<SaveVitalSignDetailItemCommand> items) {
        Set<String> keys = new HashSet<>();
        for (SaveVitalSignDetailItemCommand item : items) {
            String key = VitalSignDetailSupport.buildNaturalKey(item);
            if (!keys.add(key)) {
                throw new BusinessException(ErrorCodeConstants.VITAL_SIGN_INVALID_ARGUMENT,
                    "同一单据下字典项不得重复: " + item.getItemCode());
            }
        }
    }

    /**
     * 加载术语默认单位。
     *
     * @param termCode 术语体系 code
     * @return code → 默认单位
     */
    private Map<String, String> loadDefaultUnitsByCode(String termCode) {
        // 加载候选：经 CodeSystemQueryService 取概念默认单位（B.6.4）
        List<Concept> concepts = codeSystemQueryService.findConceptsByCodeSystemName(termCode);
        Map<String, String> result = new HashMap<>();
        for (Concept concept : concepts) {
            if (concept.getCode() != null && StringUtils.isNotEmpty(concept.getDefinition())) {
                result.put(concept.getCode(), concept.getDefinition());
            }
        }
        return result;
    }

    /**
     * 解析明细单位。
     *
     * @param item               明细命令
     * @param defaultUnitsByCode 默认单位
     * @return 单位
     */
    private static String resolveUnits(
        SaveVitalSignDetailItemCommand item,
        Map<String, String> defaultUnitsByCode) {
        if (StringUtils.isNotEmpty(item.getUnits())) {
            return item.getUnits();
        }
        return defaultUnitsByCode.get(item.getItemCode());
    }
}
