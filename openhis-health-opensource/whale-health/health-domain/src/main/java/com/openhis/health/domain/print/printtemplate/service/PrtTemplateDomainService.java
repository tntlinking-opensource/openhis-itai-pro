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

package com.openhis.health.domain.print.printtemplate.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.domain.print.printtemplate.command.ForkDraftFromPublishedCommand;
import com.openhis.health.domain.print.printtemplate.command.PublishTemplateCommand;
import com.openhis.health.domain.print.printtemplate.command.SaveDraftCommand;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.repository.PrtTemplateRepository;
import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.health.domain.share.constants.BusinessSequenceConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.enums.PrintTemplateStatus;
import com.openhis.health.domain.share.utils.PrintJsonValidationUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.sequence.BusinessSequenceService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 画布打印模板写侧领域服务（DM-PRT-001 · PrtTemplate）。
 *
 * <p>与 {@link PrtTemplateQueryService} 同包并列；读路径请注入 QueryService（B.6.4）。</p>
 * <p>写路径 UoW 由调用方 {@code *AppService} 经 {@link com.openhis.whale.core.infrastructure.transaction.AppServiceAspect} 开启，本类禁止声明 {@code @Transactional}。</p>
 */
@Service
@RequiredArgsConstructor
public class PrtTemplateDomainService {

    private final PrtTemplateRepository prtTemplateRepository;
    private final PrtTemplateQueryService prtTemplateQueryService;
    private final ObjectMapper objectMapper;
    private final BusinessSequenceService businessSequenceService;

    /**
     * 为租户生成下一个模板 busNo（IF-BAS-PRT-12；DOC-CONFLICT：未列入 DM §6.1，B.10.9 序号生成）。
     *
     * @param tenantId 租户
     * @return 新 busNo
     * @throws BusinessException 租户为空
     */
    public String nextBusNo(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
        }
        return businessSequenceService.getSeqByDay(
            AggregateTypeConstants.PRINT_TEMPLATE, BusinessSequenceConstants.BUS_NO_SEQ_LENGTH);
    }

    /**
     * 保存或更新租户草稿（TC-PRT-TPL-SAVE-001）。
     *
     * @param cmd 草稿命令
     * @return 持久化后的模板行
     * @throws BusinessException 租户/版式/状态非法
     */
    public PrtTemplate saveDraft(SaveDraftCommand cmd) {
        // Gate：租户与 busNo 须有效，平台租户不允许编辑草稿。
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
        }
        if (GeneralSharedConstants.PLATFORM_TENANT_ID == cmd.getTenantId()) {
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
        PrintJsonValidationUtil.validate(objectMapper, printJson);

        PrtTemplate template;
        if (cmd.getExistingId() != null) {
            // Pre：经 Template QS 加载 draft，与读路径同口径（B.6.4）。
            template = prtTemplateQueryService.findDraftById(cmd.getExistingId(), cmd.getTenantId());
            if (template == null) {
                throw new BusinessException(
                    ErrorCodeConstants.PRT_NOT_FOUND, "未找到模板：" + cmd.getExistingId());
            }
            template.assertDraftStatus();
        } else {
            template = prtTemplateRepository
                .findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
                    cmd.getTenantId(), busNo, PrintTemplateStatus.DRAFT.getCode());
            if (template == null) {
                template = new PrtTemplate();
                template.setTenantId(cmd.getTenantId());
                template.setOrganizationId(null);
                template.setBusNo(busNo);
                template.setStatus(PrintTemplateStatus.DRAFT.getCode());
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
        template.setSchemaVersion(PrintJsonValidationUtil.resolveSchemaVersion(objectMapper, printJson));
        template.setSamplePrintData(trimToNull(cmd.getSamplePrintData()));
        template.setStatus(PrintTemplateStatus.DRAFT.getCode());
        return prtTemplateRepository.save(template);
    }

    /**
     * 发布草稿：插新 published、旧 published 归档、源 draft 软删（TC-PRT-TPL-PUB-001）。
     *
     * @param cmd 发布命令
     * @return 新插入的 published 行
     * @throws BusinessException 草稿不存在或状态/版式非法
     */
    public PrtTemplate publish(PublishTemplateCommand cmd) {
        // Pre：加载 draft 并断言可发布。
        PrtTemplate draft = prtTemplateRepository.findById(cmd.getDraftId())
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到草稿模板"));
        if (cmd.getTenantId() != null) {
            draft.assertTenantMatches(cmd.getTenantId());
        }
        String busNo = trimToNull(cmd.getBusNo());
        if (busNo != null && !busNo.equals(draft.getBusNo())) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 与草稿不一致");
        }
        draft.assertDraftForPublish();
        // Pre：版式校验。
        PrintJsonValidationUtil.validate(objectMapper, draft.getPrintJson());

        Long tenantId = draft.getTenantId();
        Long organizationId = draft.getOrganizationId();
        String resolvedBusNo = draft.getBusNo();
        String scenarioCode = draft.getScenarioCode();
        this.reassignScenarioCode(tenantId, scenarioCode, resolvedBusNo);

        PrtTemplate latestPublished = prtTemplateRepository
            .findFirstByTenantIdAndBusNoAndStatusAndIsDeletedFalseOrderByVersionDesc(
                tenantId, resolvedBusNo, PrintTemplateStatus.PUBLISHED.getCode());
        int nextVersion = 1;
        if (latestPublished != null && latestPublished.getVersion() != null) {
            nextVersion = latestPublished.getVersion() + 1;
        }

        // Cmd：同 scope 旧 published → archived（须在插入新 published 前完成）。
        List<PrtTemplate> oldPublishedRows = prtTemplateRepository
            .findAllByTenantIdAndOrganizationIdAndBusNoAndStatusAndIsDeletedFalse(
                tenantId, organizationId, resolvedBusNo, PrintTemplateStatus.PUBLISHED.getCode());
        for (PrtTemplate row : oldPublishedRows) {
            row.markArchived();
        }
        if (!oldPublishedRows.isEmpty()) {
            prtTemplateRepository.saveAll(oldPublishedRows);
        }

        // Cmd：插入新 published 行。
        PrtTemplate published = new PrtTemplate();
        published.setTenantId(tenantId);
        published.setOrganizationId(organizationId);
        published.setBusNo(resolvedBusNo);
        published.setScenarioCode(scenarioCode);
        published.setName(draft.getName());
        published.setStatus(PrintTemplateStatus.PUBLISHED.getCode());
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
     * @throws BusinessException 租户/busNo 非法或源模板不存在
     */
    public PrtTemplate forkDraftFromPublished(ForkDraftFromPublishedCommand cmd) {
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
        }
        if (GeneralSharedConstants.PLATFORM_TENANT_ID == cmd.getTenantId()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "平台租户不允许复制草稿");
        }
        String busNo = trimToNull(cmd.getBusNo());
        if (busNo == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
        }

        PrtTemplate existingDraft = prtTemplateQueryService.findTenantDraft(busNo, cmd.getTenantId());
        if (existingDraft != null) {
            return existingDraft;
        }

        PrtTemplate published;
        if (cmd.getPublishedId() != null) {
            published = prtTemplateRepository.findById(cmd.getPublishedId())
                .orElseThrow(() -> new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到已发布模板"));
            published.assertTenantMatches(cmd.getTenantId());
            published.assertPublishedForFork();
        } else {
            // 加载候选：经 Template QS，与运行时解析同口径（B.6.4）。
            published = prtTemplateQueryService.findPublished(busNo, cmd.getTenantId());
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
        draft.setStatus(PrintTemplateStatus.DRAFT.getCode());
        draft.setVersion(published.getVersion());
        draft.setPrintJson(published.getPrintJson());
        draft.setSchemaVersion(published.getSchemaVersion());
        draft.setSamplePrintData(published.getSamplePrintData());
        return prtTemplateRepository.save(draft);
    }

    /**
     * 软删租户下指定 busNo 的全部模板行（IF-BAS-PRT-17 · TC-PRT-TPL-DEL-001；DOC-CONFLICT：未列入 DM §6.1）。
     *
     * @param busNo    业务键
     * @param tenantId 租户
     * @throws BusinessException busNo/租户非法或无可删行
     */
    public void deleteByBusNo(String busNo, Long tenantId) {
        String resolvedBusNo = trimToNull(busNo);
        if (resolvedBusNo == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "busNo 不能为空");
        }
        if (tenantId == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
        }
        if (GeneralSharedConstants.PLATFORM_TENANT_ID == tenantId) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "平台租户不允许删除模板");
        }
        List<PrtTemplate> rows = prtTemplateRepository.findAllByTenantIdAndBusNoAndIsDeletedFalse(
            tenantId, resolvedBusNo);
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRT_NOT_FOUND, "未找到可删除的模板：" + resolvedBusNo);
        }
        for (PrtTemplate row : rows) {
            row.markDeleted();
        }
        prtTemplateRepository.saveAll(rows);
    }

    /**
     * 发布时将场景码从同租户其它模板行上收回（仅 draft / published）。
     *
     * @param tenantId     租户
     * @param scenarioCode 场景码
     * @param targetBusNo  目标 busNo
     */
    private void reassignScenarioCode(Long tenantId, String scenarioCode, String targetBusNo) {
        String code = trimToNull(scenarioCode);
        if (code == null || tenantId == null || targetBusNo == null) {
            return;
        }
        List<PrtTemplate> rows = prtTemplateRepository.findAllByTenantIdAndScenarioCodeAndIsDeletedFalse(
            tenantId, code);
        List<PrtTemplate> toUpdate = new ArrayList<>();
        for (PrtTemplate row : rows) {
            if (targetBusNo.equals(row.getBusNo())) {
                continue;
            }
            // archived 行不参与场景码竞争，保留历史快照。
            if (row.isArchived()) {
                continue;
            }
            row.setScenarioCode(null);
            toUpdate.add(row);
        }
        if (!toUpdate.isEmpty()) {
            prtTemplateRepository.saveAll(toUpdate);
        }
    }
}
