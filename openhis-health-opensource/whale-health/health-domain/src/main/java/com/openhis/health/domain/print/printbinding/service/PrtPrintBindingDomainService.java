/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printbinding.service;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import com.openhis.health.domain.print.printbinding.command.SaveTenantBindingsCommand;
import com.openhis.health.domain.print.printbinding.entity.PrtPrintBinding;
import com.openhis.health.domain.print.printbinding.repository.PrtPrintBindingRepository;
import com.openhis.health.domain.print.printtemplate.entity.PrtTemplate;
import com.openhis.health.domain.print.printtemplate.service.PrtTemplateQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 业务场景模板绑定写侧领域服务（DM-PRT-003 · PrtPrintBinding）。
 *
 * <p>与 {@link PrtPrintBindingQueryService} 同包并列；跨聚合读 published 模板经 {@link PrtTemplateQueryService}（B.6.4）。</p>
 * <p>写路径 UoW 由调用方 {@code *AppService} 开启，本类禁止声明 {@code @Transactional}。</p>
 */
@Service
@RequiredArgsConstructor
public class PrtPrintBindingDomainService {

    private final PrtPrintBindingRepository prtPrintBindingRepository;
    private final PrtTemplateQueryService prtTemplateQueryService;

    /**
     * 保存租户级 bindingCode→busNo 映射（TC-PRT-BND-SAVE-001）。
     *
     * @param cmd 全量绑定命令
     * @throws BusinessException 租户/绑定项非法或目标模板不存在
     */
    public void saveTenantBindings(SaveTenantBindingsCommand cmd) {
        // Gate：仅租户可维护 override，平台 seed 不可改。
        if (cmd.getTenantId() == null) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
        }
        if (GeneralSharedConstants.PLATFORM_TENANT_ID == cmd.getTenantId()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "不可修改平台出厂绑定");
        }
        Map<String, String> bindings = cmd.getBindingCodeToBusNo();
        if (bindings == null || bindings.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "绑定配置不能为空");
        }

        List<PrtPrintBinding> toSave = new ArrayList<>();
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String bindingCode = trimToNull(entry.getKey());
            String busNo = trimToNull(entry.getValue());
            if (bindingCode == null) {
                throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "bindingCode 不能为空");
            }
            if (busNo == null) {
                throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "请为所有场景选择有效模板");
            }
            // Pre：目标 busNo 须存在 published 模板（BR-PRT-BND-002 · B.6.4）。
            PrtTemplate published = prtTemplateQueryService.findPublished(busNo, cmd.getTenantId());
            if (published == null) {
                throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "请为所有场景选择有效模板");
            }

            PrtPrintBinding existing = prtPrintBindingRepository
                .findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(
                    cmd.getTenantId(), bindingCode, null);
            if (existing != null) {
                existing.setBusNo(busNo);
                existing.setEnabled(Boolean.TRUE);
                toSave.add(existing);
            } else {
                PrtPrintBinding row = new PrtPrintBinding();
                row.setTenantId(cmd.getTenantId());
                row.setOrganizationId(null);
                row.setBindingCode(bindingCode);
                row.setBusNo(busNo);
                row.setEnabled(Boolean.TRUE);
                toSave.add(row);
            }
        }
        // Cmd：批量 upsert 绑定行。
        if (!toSave.isEmpty()) {
            prtPrintBindingRepository.saveAll(toSave);
        }
    }
}
