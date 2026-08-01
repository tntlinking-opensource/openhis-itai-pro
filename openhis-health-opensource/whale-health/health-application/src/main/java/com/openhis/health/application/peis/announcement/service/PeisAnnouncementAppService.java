/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.announcement.service;

import com.openhis.health.application.peis.announcement.request.SaveHomeNoticeRequest;
import com.openhis.health.application.peis.announcement.response.HomeNoticeResponse;
import com.openhis.health.domain.peis.peisconfig.command.SavePeisConfigCommand;
import com.openhis.health.domain.peis.peisconfig.entity.PeisConfig;
import com.openhis.health.domain.peis.peisconfig.service.PeisConfigDomainService;
import com.openhis.health.domain.peis.peisconfig.service.PeisConfigQueryService;
import com.openhis.health.domain.share.constants.PeisCatalogConstants;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 小程序首页公告（骨架）：内容存 peis_config 键 {@link PeisCatalogConstants#CONFIG_KEY_MINIAPP_HOME_NOTICE}。
 */
@Service
@RequiredArgsConstructor
public class PeisAnnouncementAppService {

    private static final String DEFAULT_TITLE = "院内公告";

    private final PeisConfigQueryService peisConfigQueryService;
    private final PeisConfigDomainService peisConfigDomainService;

    /**
     * 小程序首页读取已发布公告（无配置时返回空正文）。
     */
    public HomeNoticeResponse getHomeNotice() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        Optional<PeisConfig> row = peisConfigQueryService.findOptionalByKey(
            tenantId, PeisCatalogConstants.CONFIG_KEY_MINIAPP_HOME_NOTICE);
        HomeNoticeResponse response = new HomeNoticeResponse();
        response.setTitle(DEFAULT_TITLE);
        if (row.isEmpty()) {
            response.setContentHtml("");
            return response;
        }
        PeisConfig config = row.get();
        response.setContentHtml(config.getConfigValue() == null ? "" : config.getConfigValue());
        response.setId(config.getId());
        LocalDateTime updated = config.getUpdatedAt();
        if (updated != null) {
            response.setUpdatedAt(updated.toString());
        }
        return response;
    }

    /**
     * 管理端保存首页公告 HTML。
     */
    public HomeNoticeResponse saveHomeNotice(SaveHomeNoticeRequest request) {
        if (request == null) {
            throw new ValidationException("保存公告失败：请求不能为空");
        }
        String html = request.getContentHtml();
        if (html == null) {
            throw new ValidationException("保存公告失败：正文不能为空");
        }
        SavePeisConfigCommand cmd = new SavePeisConfigCommand();
        cmd.setTenantId(TenantUtils.getCurrentTenantId());
        cmd.setConfigKey(PeisCatalogConstants.CONFIG_KEY_MINIAPP_HOME_NOTICE);
        cmd.setConfigValue(html);
        peisConfigDomainService.save(cmd);
        return getHomeNotice();
    }
}
