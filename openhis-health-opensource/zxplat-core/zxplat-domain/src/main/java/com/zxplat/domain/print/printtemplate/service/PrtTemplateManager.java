/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.print.printtemplate.service;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

import com.zxplat.domain.print.printtemplate.command.ForkDraftFromPublishedCommand;
import com.zxplat.domain.print.printtemplate.command.PublishTemplateCommand;
import com.zxplat.domain.print.printtemplate.command.SaveDraftCommand;
import com.zxplat.domain.print.printtemplate.constants.PrintTemplateStatus;
import com.zxplat.domain.print.printtemplate.constants.PrtTenantScope;
import com.zxplat.domain.print.printtemplate.entity.PrtTemplate;
import com.zxplat.domain.print.printtemplate.repository.PrtTemplateRepository;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.framework.core.application.exception.BusinessException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 画布打印模板写读领域服务（PrtTemplateManager）。
 */
@Service
@RequiredArgsConstructor
public class PrtTemplateManager {

  private final PrtTemplateRepository prtTemplateRepository;
  private final PrintJsonValidator printJsonValidator;

  /**
   * 保存或更新租户草稿（TC-PRT-TPL-SAVE-001）。
   *
   * @param cmd 草稿命令
   * @return 持久化后的模板行
   */
  public PrtTemplate saveDraft(SaveDraftCommand cmd) {
  // Gate：租户与 busNo。
    if (cmd.getTenantId() == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == cmd.getTenantId()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "平台租户不允许保存草稿");
    }
    String busNo = trimToNull(cmd.getBusNo());
    if (busNo == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
    }
    String printJson = trimToNull(cmd.getPrintJson());
    if (printJson == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "PrintJson 不能为空");
    }
  // Pre：版式校验（BR-PRT-003、008、009）。
    printJsonValidator.validate(printJson);

    PrtTemplate template;
    if (cmd.getExistingId() != null) {
      template =
          prtTemplateRepository
              .findById(cmd.getExistingId())
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + cmd.getExistingId()));
      if (!template.getTenantId().equals(cmd.getTenantId())) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板租户不匹配");
      }
      if (!PrintTemplateStatus.DRAFT.equals(template.getStatus())) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "仅 draft 可保存");
      }
    } else {
      template =
          prtTemplateRepository
              .findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
                  cmd.getTenantId(), busNo, PrintTemplateStatus.DRAFT);
      if (template == null) {
        template = new PrtTemplate();
        template.setTenantId(cmd.getTenantId());
        template.setOrganizationId(null);
        template.setBusNo(busNo);
        template.setStatus(PrintTemplateStatus.DRAFT);
        template.setVersion(1);
      }
    }

  // Cmd：写入载荷并置 draft（organization_id 本期恒 NULL，见 DM-PRT-001 §9.1）。
    template.setOrganizationId(null);
    template.setBusNo(busNo);
    if (cmd.getName() != null && !cmd.getName().trim().isEmpty()) {
      template.setName(cmd.getName().trim());
    } else if (template.getName() == null || template.getName().trim().isEmpty()) {
      template.setName(busNo);
    }
    template.setPrintJson(printJson);
    template.setSchemaVersion(printJsonValidator.resolveSchemaVersion(printJson));
    template.setSamplePrintData(trimToNull(cmd.getSamplePrintData()));
    template.setScenarioCode(normalizeScenarioCode(cmd.getScenarioCode()));
    template.setStatus(PrintTemplateStatus.DRAFT);
    return prtTemplateRepository.save(template);
  }

  /**
   * 发布草稿：插新 published、旧 published 归档、源 draft 软删（TC-PRT-TPL-PUB-001）。
   *
   * @param cmd 发布命令
   * @return 新插入的 published 行
   */
  @Transactional
  public PrtTemplate publish(PublishTemplateCommand cmd) {
  // Pre：加载 draft。
    PrtTemplate draft =
        prtTemplateRepository
            .findById(cmd.getDraftId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        ErrorCodeConstants.PRT_NOT_FOUND, "未找到草稿模板"));
    if (cmd.getTenantId() != null && !draft.getTenantId().equals(cmd.getTenantId())) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "模板租户不匹配");
    }
    String busNo = trimToNull(cmd.getBusNo());
    if (busNo != null && !busNo.equals(draft.getBusNo())) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 与草稿不一致");
    }
    draft.assertDraftForPublish();
  // Pre：版式校验。
    printJsonValidator.validate(draft.getPrintJson());

    Long tenantId = draft.getTenantId();
    Long organizationId = draft.getOrganizationId();
    String resolvedBusNo = draft.getBusNo();
    String scenarioCode = draft.getScenarioCode();
    reassignScenarioCode(tenantId, scenarioCode, resolvedBusNo);

    PrtTemplate latestPublished =
        prtTemplateRepository.findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
            tenantId, resolvedBusNo, PrintTemplateStatus.PUBLISHED);
    int nextVersion = 1;
    if (latestPublished != null && latestPublished.getVersion() != null) {
      nextVersion = latestPublished.getVersion() + 1;
    }

  // Cmd：同 scope 旧 published → archived（须在插入新 published 前完成，避免同 busNo 并存多条 published）。
    List<PrtTemplate> oldPublishedRows =
        prtTemplateRepository.findAllByTenantIdAndOrganizationIdAndBusNoAndStatusAndIsDeletedFalse(
            tenantId, organizationId, resolvedBusNo, PrintTemplateStatus.PUBLISHED);
    for (PrtTemplate row : oldPublishedRows) {
      row.setStatus(PrintTemplateStatus.ARCHIVED);
      prtTemplateRepository.save(row);
    }

  // Cmd：插入新 published 行。
    PrtTemplate published = new PrtTemplate();
    published.setTenantId(tenantId);
    published.setOrganizationId(organizationId);
    published.setBusNo(resolvedBusNo);
    published.setScenarioCode(scenarioCode);
    published.setName(draft.getName());
    published.setStatus(PrintTemplateStatus.PUBLISHED);
    published.setVersion(nextVersion);
    published.setPrintJson(draft.getPrintJson());
    published.setSchemaVersion(draft.getSchemaVersion());
    published.setSamplePrintData(draft.getSamplePrintData());
    PrtTemplate savedPublished = prtTemplateRepository.save(published);

  // Cmd：源 draft 软删。
    draft.markDeleted();
    prtTemplateRepository.save(draft);

    return savedPublished;
  }

  /**
   * 从 published 复制新 draft（TC-PRT-TPL-FORK-001）。
   *
   * @param cmd 复制命令
   * @return 新或已有 draft 行
   */
  public PrtTemplate forkDraftFromPublished(ForkDraftFromPublishedCommand cmd) {
    if (cmd.getTenantId() == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == cmd.getTenantId()) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "平台租户不允许复制草稿");
    }
    String busNo = trimToNull(cmd.getBusNo());
    if (busNo == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
    }

    PrtTemplate existingDraft = findTenantDraft(busNo, cmd.getTenantId());
    if (existingDraft != null) {
      return existingDraft;
    }

    PrtTemplate published;
    if (cmd.getPublishedId() != null) {
      published =
          prtTemplateRepository
              .findById(cmd.getPublishedId())
              .orElseThrow(
                  () ->
                      new BusinessException(
                          ErrorCodeConstants.PRT_NOT_FOUND, "未找到已发布模板"));
      if (!PrintTemplateStatus.PUBLISHED.equals(published.getStatus())) {
        throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "源模板不是 published");
      }
    } else {
      published = findPublished(busNo, cmd.getTenantId());
      if (published == null) {
        throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + busNo);
      }
    }

    PrtTemplate draft = new PrtTemplate();
    draft.setTenantId(cmd.getTenantId());
    draft.setOrganizationId(null);
    draft.setBusNo(published.getBusNo());
    draft.setScenarioCode(published.getScenarioCode());
    draft.setName(published.getName());
    draft.setStatus(PrintTemplateStatus.DRAFT);
    draft.setVersion(published.getVersion());
    draft.setPrintJson(published.getPrintJson());
    draft.setSchemaVersion(published.getSchemaVersion());
    draft.setSamplePrintData(published.getSamplePrintData());
    return prtTemplateRepository.save(draft);
  }

  /**
   * 取最新 published（不含 archived）；租户无则 fallback 平台（TC-PRT-TPL-RD-001）。
   *
   * @param busNo 业务键
   * @param tenantId 租户
   * @return published 行或 null
   */
  public PrtTemplate findPublished(String busNo, Long tenantId) {
    String resolvedBusNo = trimToNull(busNo);
    if (resolvedBusNo == null || tenantId == null) {
      return null;
    }
  // Pre：租户 published。
    PrtTemplate tenantRow =
        prtTemplateRepository.findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
            tenantId, resolvedBusNo, PrintTemplateStatus.PUBLISHED);
    if (tenantRow != null) {
      return tenantRow;
    }
  // Pre：平台 fallback。
    if (PrtTenantScope.PLATFORM_TENANT_ID != tenantId) {
      return prtTemplateRepository.findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
          PrtTenantScope.PLATFORM_TENANT_ID, resolvedBusNo, PrintTemplateStatus.PUBLISHED);
    }
    return null;
  }

  /**
   * 设计态加载：租户 draft 优先，否则 published（TC-PRT-TPL-RD-002）。
   *
   * @param busNo 业务键
   * @param tenantId 租户
   * @return 模板行或 null
   */
  public PrtTemplate resolveForDesign(String busNo, Long tenantId) {
    String resolvedBusNo = trimToNull(busNo);
    if (resolvedBusNo == null || tenantId == null) {
      return null;
    }
    PrtTemplate draft = findTenantDraft(resolvedBusNo, tenantId);
    if (draft != null) {
      return draft;
    }
    return findPublished(resolvedBusNo, tenantId);
  }

  /**
   * 查租户草稿（平台 tenant 不查 draft，TC-PRT-TPL-RD-003）。
   *
   * @param busNo 业务键
   * @param tenantId 租户
   * @return draft 行或 null
   */
  public PrtTemplate findTenantDraft(String busNo, Long tenantId) {
    String resolvedBusNo = trimToNull(busNo);
    if (resolvedBusNo == null || tenantId == null) {
      return null;
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == tenantId) {
      return null;
    }
    return prtTemplateRepository.findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
        tenantId, resolvedBusNo, PrintTemplateStatus.DRAFT);
  }

  /**
   * 按主键加载租户草稿。
   *
   * @param draftId 草稿 id
   * @param tenantId 租户
   * @return draft 行或 null
   */
  public PrtTemplate findDraftById(Long draftId, Long tenantId) {
    if (draftId == null || tenantId == null) {
      return null;
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == tenantId) {
      return null;
    }
    PrtTemplate row = prtTemplateRepository.findById(draftId).orElse(null);
    if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
      return null;
    }
    if (!tenantId.equals(row.getTenantId())) {
      return null;
    }
    if (!PrintTemplateStatus.DRAFT.equals(row.getStatus())) {
      return null;
    }
    return row;
  }

  /**
   * 确保存在可编辑草稿：有 draft 则返回，否则从 published fork（TC-PRT-TPL-RD-005）。
   *
   * @param busNo 业务键
   * @param tenantId 租户
   * @return 草稿行
   */
  public PrtTemplate ensureDesignDraft(String busNo, Long tenantId) {
    String resolvedBusNo = trimToNull(busNo);
    if (resolvedBusNo == null || tenantId == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == tenantId) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "平台租户不允许编辑草稿");
    }
    PrtTemplate draft = findTenantDraft(resolvedBusNo, tenantId);
    if (draft != null) {
      return draft;
    }
    ForkDraftFromPublishedCommand cmd = new ForkDraftFromPublishedCommand();
    cmd.setTenantId(tenantId);
    cmd.setBusNo(resolvedBusNo);
    return forkDraftFromPublished(cmd);
  }

  /**
   * 合并平台与租户模板列表（TC-PRT-TPL-RD-004）。
   *
   * @param tenantId 当前租户
   * @param includeArchived 是否包含 archived
   * @return 合并后的模板行
   */
  public List<PrtTemplate> listMergedForTenant(Long tenantId, boolean includeArchived) {
    if (tenantId == null) {
      return List.of();
    }
    List<PrtTemplate> platformRows =
        prtTemplateRepository.findByTenantIdAndIsDeletedFalseOrderByBusNoAscVersionDesc(
            PrtTenantScope.PLATFORM_TENANT_ID);
    List<PrtTemplate> tenantRows =
        prtTemplateRepository.findByTenantIdAndIsDeletedFalseOrderByBusNoAscVersionDesc(tenantId);

    Set<String> tenantBusNos = new HashSet<>();
    for (PrtTemplate row : tenantRows) {
      tenantBusNos.add(row.getBusNo());
    }

    List<PrtTemplate> merged = new ArrayList<>(tenantRows);
    for (PrtTemplate platformRow : platformRows) {
      if (!tenantBusNos.contains(platformRow.getBusNo())) {
        merged.add(platformRow);
      }
    }

    if (!includeArchived) {
      List<PrtTemplate> filtered = new ArrayList<>();
      for (PrtTemplate row : merged) {
        if (!PrintTemplateStatus.ARCHIVED.equals(row.getStatus())) {
          filtered.add(row);
        }
      }
      return filtered;
    }
    return merged;
  }

  /**
   * 软删租户下指定 busNo 的全部模板行（TC-PRT-TPL-DEL-001）。
   *
   * @param busNo 业务键
   * @param tenantId 租户
   */
  @Transactional
  public void deleteByBusNo(String busNo, Long tenantId) {
    String resolvedBusNo = trimToNull(busNo);
    if (resolvedBusNo == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
    }
    if (tenantId == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
    }
    if (PrtTenantScope.PLATFORM_TENANT_ID == tenantId) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "平台租户不允许删除模板");
    }
    List<PrtTemplate> rows =
        prtTemplateRepository.findAllByTenantIdAndBusNoAndIsDeletedFalse(tenantId, resolvedBusNo);
    if (rows.isEmpty()) {
      throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到可删除的模板：" + resolvedBusNo);
    }
    for (PrtTemplate row : rows) {
      row.markDeleted();
      prtTemplateRepository.save(row);
    }
  }

  /**
   * 发布时将场景码从同租户其它模板行上收回（仅 draft / published）。
   *
   * @param tenantId 租户
   * @param scenarioCode 场景码
   * @param targetBusNo 目标 busNo
   */
  private void reassignScenarioCode(Long tenantId, String scenarioCode, String targetBusNo) {
    String code = trimToNull(scenarioCode);
    if (code == null || tenantId == null || targetBusNo == null) {
      return;
    }
    List<PrtTemplate> rows =
        prtTemplateRepository.findAllByTenantIdAndScenarioCodeAndIsDeletedFalse(tenantId, code);
    for (PrtTemplate row : rows) {
      if (targetBusNo.equals(row.getBusNo())) {
        continue;
      }
      if (PrintTemplateStatus.ARCHIVED.equals(row.getStatus())) {
        continue;
      }
      row.setScenarioCode(null);
      prtTemplateRepository.save(row);
    }
  }

  /**
   * 规范化场景码：trim 后空串视为 null。
   *
   * @param scenarioCode 原始场景码
   * @return 规范化后的场景码
   */
  private String normalizeScenarioCode(String scenarioCode) {
    return trimToNull(scenarioCode);
  }
}
