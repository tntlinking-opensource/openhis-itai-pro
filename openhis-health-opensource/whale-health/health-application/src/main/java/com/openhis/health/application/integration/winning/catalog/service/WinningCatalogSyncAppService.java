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

package com.openhis.health.application.integration.winning.catalog.service;

import com.openhis.health.application.common.validator.CurrentOperatorResolver;
import com.openhis.health.application.integration.winning.catalog.WinningCatalogSyncConstants;
import com.openhis.health.application.integration.winning.catalog.dto.SyncWinningCatalogRequest;
import com.openhis.health.application.integration.winning.catalog.dto.WinningActivityCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogFetchResult;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogPingResponse;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogPullRequest;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogSyncFailureRow;
import com.openhis.health.application.integration.winning.catalog.dto.WinningCatalogSyncResponse;
import com.openhis.health.application.integration.winning.catalog.dto.WinningDeviceCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.dto.WinningMedicationCatalogItemDto;
import com.openhis.health.application.integration.winning.catalog.gateway.WinningCatalogGateway;
import com.openhis.health.application.integration.winning.catalog.mapper.WinningCatalogSyncMapper;
import com.openhis.health.application.integration.winning.catalog.orchestration.WinningCatalogInstanceIdResolver;
import com.openhis.health.application.integration.winning.catalog.orchestration.WinningCatalogMedicationUpsertOrchestrator;
import com.openhis.health.application.integration.winning.catalog.orchestration.WinningCatalogPricingApplier;
import com.openhis.health.domain.administration.devicedefinition.command.ImportDeviceDefinitionCatalogCommand;
import com.openhis.health.domain.administration.devicedefinition.command.ImportDeviceDefinitionCatalogResult;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionDomainService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.workflow.activitydefinition.command.ImportActivityCatalogCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.ImportActivityCatalogResult;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 卫宁产品目录同步：探测网关连通性，按页拉取药品/耗材/诊疗目录并 upsert 至本系统定义表，刷新定价行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WinningCatalogSyncAppService {

    private final WinningCatalogGateway winningCatalogGateway;
    private final WinningCatalogSyncMapper mapper;
    private final WinningCatalogMedicationUpsertOrchestrator medicationUpsertOrchestrator;
    private final DeviceDefinitionDomainService deviceDefinitionDomainService;
    private final ActivityDefinitionDomainService activityDefinitionDomainService;
    private final WinningCatalogPricingApplier pricingApplier;
    private final WinningCatalogInstanceIdResolver instanceIdResolver;
    private final CurrentOperatorResolver currentOperatorResolver;

    /**
     * 探测卫宁网关是否可用（现场实现 {@link WinningCatalogGateway#ping()}）。
     */
    public WinningCatalogPingResponse pingWinningCatalogConnection() {
        WinningCatalogPingResponse resp = new WinningCatalogPingResponse();
        resp.setConfigured(winningCatalogGateway.isLiveGateway());
        resp.setGatewayImplementation(winningCatalogGateway.getClass().getSimpleName());
        var ping = winningCatalogGateway.ping();
        resp.setReachable(ping.reachable());
        resp.setMessage(ping.message());
        return resp;
    }

    /**
     * 同步卫宁药品目录（一页）。
     */
    @Transactional
    public WinningCatalogSyncResponse syncWinningMedicationCatalog(SyncWinningCatalogRequest request) {
        return syncMedicationInternal(request);
    }

    /**
     * 同步卫宁耗材目录（一页）。
     */
    @Transactional
    public WinningCatalogSyncResponse syncWinningDeviceCatalog(SyncWinningCatalogRequest request) {
        return syncDeviceInternal(request);
    }

    /**
     * 同步卫宁诊疗目录（一页）。
     */
    @Transactional
    public WinningCatalogSyncResponse syncWinningActivityCatalog(SyncWinningCatalogRequest request) {
        return syncActivityInternal(request);
    }

    /**
     * 按机构顺序同步药品、耗材、诊疗三表（各拉取一页）。
     */
    @Transactional
    public WinningCatalogSyncResponse syncAllWinningCatalogs(SyncWinningCatalogRequest request) {
        // 按产品目录种类依次拉取一页并 upsert，失败行分别汇总到合并响应。
        WinningCatalogSyncResponse med = syncMedicationInternal(request);
        WinningCatalogSyncResponse dev = syncDeviceInternal(request);
        WinningCatalogSyncResponse act = syncActivityInternal(request);

        WinningCatalogSyncResponse merged = new WinningCatalogSyncResponse();
        merged.setSuccess(Boolean.TRUE.equals(med.getSuccess())
            && Boolean.TRUE.equals(dev.getSuccess())
            && Boolean.TRUE.equals(act.getSuccess()));
        merged.setProductKind("ALL");
        merged.setStubMode(Boolean.TRUE.equals(med.getStubMode())
            || Boolean.TRUE.equals(dev.getStubMode())
            || Boolean.TRUE.equals(act.getStubMode()));
        merged.setMessage("药品/耗材/诊疗同步已完成");
        merged.setFetchedCount(safe(med.getFetchedCount()) + safe(dev.getFetchedCount()) + safe(act.getFetchedCount()));
        merged.setCreatedCount(safe(med.getCreatedCount()) + safe(dev.getCreatedCount()) + safe(act.getCreatedCount()));
        merged.setUpdatedCount(safe(med.getUpdatedCount()) + safe(dev.getUpdatedCount()) + safe(act.getUpdatedCount()));
        merged.setFailedCount(safe(med.getFailedCount()) + safe(dev.getFailedCount()) + safe(act.getFailedCount()));
        merged.setSkippedCount(safe(med.getSkippedCount()) + safe(dev.getSkippedCount()) + safe(act.getSkippedCount()));
        merged.setPricingAppliedCount(safe(med.getPricingAppliedCount()) + safe(dev.getPricingAppliedCount()) + safe(act.getPricingAppliedCount()));
        List<WinningCatalogSyncFailureRow> failures = new ArrayList<>();
        failures.addAll(med.getFailures());
        failures.addAll(dev.getFailures());
        failures.addAll(act.getFailures());
        merged.setFailures(failures);
        return merged;
    }

    /**
     * 药品目录一页同步：网关拉取 → 定义 upsert → 批号定价刷新。
     *
     * @param request 同步请求（机构、覆盖策略、分页游标）
     * @return 本页统计与失败明细
     */
    private WinningCatalogSyncResponse syncMedicationInternal(SyncWinningCatalogRequest request) {
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.MEDICATION_DEFINITION_INVALID_ARGUMENT, "请先登录后再同步目录");
        WinningCatalogPullRequest pull = mapper.toPullRequest(request);
        WinningCatalogFetchResult<WinningMedicationCatalogItemDto> fetch =
            winningCatalogGateway.fetchMedicationCatalog(pull);
        if (Boolean.TRUE.equals(fetch.getStubMode())) {
            // 现场未接真实网关：返回占位成功，不落库。
            return stubResponse(WinningCatalogSyncConstants.PRODUCT_KIND_MEDICATION, fetch.getGatewayMessage());
        }
        // 网关未带机构时回落请求机构，避免定价/归属歧义。
        normalizeOrgId(fetch.getItems(), request.getOrganizationId());
        boolean overwrite = request.getOverwrite() == null || Boolean.TRUE.equals(request.getOverwrite());
        var upsert = medicationUpsertOrchestrator.upsert(fetch.getItems(), operatorId, overwrite);
        int pricingApplied = pricingApplier.applyMedicationPrices(fetch.getItems(), upsert.getInstanceIdByBusNo());

        WinningCatalogSyncResponse resp = baseResponse(WinningCatalogSyncConstants.PRODUCT_KIND_MEDICATION, fetch);
        resp.setCreatedCount(upsert.getCreatedCount());
        resp.setUpdatedCount(upsert.getUpdatedCount());
        resp.setSkippedCount(upsert.getSkippedCount());
        resp.setFailedCount(upsert.getFailures().size());
        resp.setFailures(upsert.getFailures());
        resp.setPricingAppliedCount(pricingApplied);
        resp.setSuccess(upsert.getFailures().isEmpty());
        return resp;
    }

    /**
     * 耗材目录一页同步：领域批量导入 → 按 busNo 解析实例 → 定价行刷新。
     *
     * @param request 同步请求
     * @return 本页统计与失败明细
     */
    private WinningCatalogSyncResponse syncDeviceInternal(SyncWinningCatalogRequest request) {
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.DEVICE_DEFINITION_INVALID_ARGUMENT, "请先登录后再同步目录");
        WinningCatalogPullRequest pull = mapper.toPullRequest(request);
        WinningCatalogFetchResult<WinningDeviceCatalogItemDto> fetch = winningCatalogGateway.fetchDeviceCatalog(pull);
        if (Boolean.TRUE.equals(fetch.getStubMode())) {
            return stubResponse(WinningCatalogSyncConstants.PRODUCT_KIND_DEVICE, fetch.getGatewayMessage());
        }
        normalizeOrgId(fetch.getItems(), request.getOrganizationId());
        boolean overwrite = request.getOverwrite() == null || Boolean.TRUE.equals(request.getOverwrite());
        ImportDeviceDefinitionCatalogCommand importCmd =
            mapper.toDeviceImportCommand(fetch.getItems(), operatorId, overwrite);
        ImportDeviceDefinitionCatalogResult domainResult =
            deviceDefinitionDomainService.importDeviceDefinitionCatalog(importCmd);

        List<String> busNos = instanceIdResolver.collectBusNos(
            fetch.getItems(), WinningDeviceCatalogItemDto::getBusNo, WinningDeviceCatalogItemDto::getExternalItemCode);
        // 定价须绑定已落库的耗材定义主键。
        int pricingApplied = pricingApplier.applyDevicePrices(fetch.getItems(), instanceIdResolver.resolveDeviceIds(busNos));

        WinningCatalogSyncResponse resp = baseResponse(WinningCatalogSyncConstants.PRODUCT_KIND_DEVICE, fetch);
        resp.setCreatedCount(domainResult.getCreatedCount());
        resp.setUpdatedCount(domainResult.getUpdatedCount());
        int deviceFailedCount = 0;
        if (domainResult.getFailureCount() != null) {
            deviceFailedCount = domainResult.getFailureCount().intValue();
        }
        resp.setFailedCount(deviceFailedCount);
        resp.setFailures(mapDeviceFailures(domainResult));
        resp.setPricingAppliedCount(pricingApplied);
        resp.setSuccess(resp.getFailedCount() == 0);
        return resp;
    }

    /**
     * 诊疗目录一页同步：活动定义批量导入 → 定价刷新。
     *
     * @param request 同步请求
     * @return 本页统计与失败明细
     */
    private WinningCatalogSyncResponse syncActivityInternal(SyncWinningCatalogRequest request) {
        Long operatorId = currentOperatorResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "请先登录后再同步目录");
        WinningCatalogPullRequest pull = mapper.toPullRequest(request);
        WinningCatalogFetchResult<WinningActivityCatalogItemDto> fetch = winningCatalogGateway.fetchActivityCatalog(pull);
        if (Boolean.TRUE.equals(fetch.getStubMode())) {
            return stubResponse(WinningCatalogSyncConstants.PRODUCT_KIND_ACTIVITY, fetch.getGatewayMessage());
        }
        normalizeOrgId(fetch.getItems(), request.getOrganizationId());
        ImportActivityCatalogCommand importCmd = mapper.toActivityImportCommand(fetch.getItems(), operatorId);
        ImportActivityCatalogResult domainResult = activityDefinitionDomainService.importActivityCatalog(importCmd);

        List<String> busNos = instanceIdResolver.collectBusNos(
            fetch.getItems(), WinningActivityCatalogItemDto::getBusNo, WinningActivityCatalogItemDto::getExternalItemCode);
        int pricingApplied = pricingApplier.applyActivityPrices(fetch.getItems(), instanceIdResolver.resolveActivityIds(busNos));

        WinningCatalogSyncResponse resp = baseResponse(WinningCatalogSyncConstants.PRODUCT_KIND_ACTIVITY, fetch);
        resp.setCreatedCount(domainResult.getCreatedCount());
        resp.setUpdatedCount(domainResult.getUpdatedCount());
        int activityFailedCount = 0;
        if (domainResult.getFailureCount() != null) {
            activityFailedCount = domainResult.getFailureCount().intValue();
        }
        resp.setFailedCount(activityFailedCount);
        resp.setFailures(mapActivityFailures(domainResult));
        resp.setPricingAppliedCount(pricingApplied);
        resp.setSuccess(resp.getFailedCount() == 0);
        return resp;
    }

    /**
     * 为网关行补齐机构主键（仅当行上机构为空且请求带了机构时写入）。
     *
     * @param items          网关目录行
     * @param organizationId 请求机构
     * @param <T>            药品/耗材/诊疗 DTO 之一
     */
    private static <T> void normalizeOrgId(List<T> items, Long organizationId) {
        if (items == null || organizationId == null) {
            return;
        }
        for (T item : items) {
            if (item instanceof WinningMedicationCatalogItemDto med && med.getOrganizationId() == null) {
                med.setOrganizationId(organizationId);
            } else if (item instanceof WinningDeviceCatalogItemDto dev && dev.getOrganizationId() == null) {
                dev.setOrganizationId(organizationId);
            } else if (item instanceof WinningActivityCatalogItemDto act && act.getOrganizationId() == null) {
                act.setOrganizationId(organizationId);
            }
        }
    }

    /**
     * 构造 Stub 模式响应：标记未真实落库，计数归零。
     *
     * @param productKind 产品种类码
     * @param message     网关提示
     * @return 占位响应
     */
    private static WinningCatalogSyncResponse stubResponse(String productKind, String message) {
        WinningCatalogSyncResponse resp = new WinningCatalogSyncResponse();
        resp.setSuccess(Boolean.TRUE);
        resp.setProductKind(productKind);
        resp.setStubMode(Boolean.TRUE);
        resp.setMessage(message);
        resp.setFetchedCount(0);
        resp.setCreatedCount(0);
        resp.setUpdatedCount(0);
        resp.setFailedCount(0);
        resp.setSkippedCount(0);
        resp.setPricingAppliedCount(0);
        return resp;
    }

    /**
     * 构造非 Stub 响应壳：写入产品种类、网关消息与拉取条数。
     *
     * @param productKind 产品种类码
     * @param fetch       网关拉取结果
     * @param <T>         目录行类型
     * @return 待填充统计字段的响应
     */
    private static <T> WinningCatalogSyncResponse baseResponse(String productKind, WinningCatalogFetchResult<T> fetch) {
        WinningCatalogSyncResponse resp = new WinningCatalogSyncResponse();
        resp.setProductKind(productKind);
        resp.setStubMode(Boolean.FALSE);
        resp.setMessage(fetch.getGatewayMessage());
        int fetchedCount = 0;
        if (fetch.getItems() != null) {
            fetchedCount = fetch.getItems().size();
        }
        resp.setFetchedCount(fetchedCount);
        return resp;
    }

    /**
     * 将耗材领域导入失败行映射为对外失败明细。
     *
     * @param domainResult 领域导入结果
     * @return 失败行列表
     */
    private static List<WinningCatalogSyncFailureRow> mapDeviceFailures(ImportDeviceDefinitionCatalogResult domainResult) {
        List<WinningCatalogSyncFailureRow> rows = new ArrayList<>();
        if (domainResult.getFailures() == null) {
            return rows;
        }
        for (ImportDeviceDefinitionCatalogResult.ImportFailure f : domainResult.getFailures()) {
            WinningCatalogSyncFailureRow row = new WinningCatalogSyncFailureRow();
            row.setLineNo(f.getLineNo());
            row.setReason(f.getMessage());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 将诊疗领域导入失败行映射为对外失败明细。
     *
     * @param domainResult 领域导入结果
     * @return 失败行列表
     */
    private static List<WinningCatalogSyncFailureRow> mapActivityFailures(ImportActivityCatalogResult domainResult) {
        List<WinningCatalogSyncFailureRow> rows = new ArrayList<>();
        if (domainResult.getFailures() == null) {
            return rows;
        }
        for (ImportActivityCatalogResult.ImportFailure f : domainResult.getFailures()) {
            WinningCatalogSyncFailureRow row = new WinningCatalogSyncFailureRow();
            row.setLineNo(f.getLineNo());
            row.setReason(f.getMessage());
            rows.add(row);
        }
        return rows;
    }

    /**
     * 合并统计时对 null 计数按 0 处理。
     *
     * @param value 可空整数
     * @return 非 null 值或 0
     */
    private static int safe(Integer value) {
        if (value == null) {
            return 0;
        }
        return value;
    }
}
