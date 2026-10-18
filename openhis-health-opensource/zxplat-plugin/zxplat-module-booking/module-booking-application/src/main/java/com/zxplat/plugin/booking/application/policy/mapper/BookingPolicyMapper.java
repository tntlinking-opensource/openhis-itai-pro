/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy.mapper;

import com.zxplat.plugin.booking.application.policy.support.ReleasePolicyJsonSupport;
import com.zxplat.plugin.booking.application.policy.request.SaveRegistrationPolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.SaveReleasePolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.SaveScheduleChangePolicyRequest;
import com.zxplat.plugin.booking.application.policy.response.GetRegistrationModeResponse;
import com.zxplat.plugin.booking.application.policy.response.RegistrationPolicyDto;
import com.zxplat.plugin.booking.application.policy.response.ReleasePolicyDto;
import com.zxplat.plugin.booking.application.policy.response.ScheduleChangePolicyDto;
import com.zxplat.plugin.booking.domain.policy.entity.RegistrationPolicy;
import com.zxplat.plugin.booking.domain.policy.entity.ReleasePolicy;
import com.zxplat.plugin.booking.domain.policy.entity.ScheduleChangePolicy;
import com.zxplat.plugin.booking.domain.share.RegistrationWalkInSlotModeConstants;
import org.springframework.stereotype.Component;

import static com.zxplat.framework.core.application.util.StringUtils.trimToNull;

/**
 * 预约策略应用层映射器。
 * <p>职责：放号/挂号/停诊变更策略的 Request/Entity/DTO 转换；不含业务门禁与事务。</p>
 */
@Component
public class BookingPolicyMapper {

    /**
     * 放号策略实体 → 列表/详情 DTO。
     *
     * @param policy 放号策略实体
     * @return 放号策略 DTO
     */
    public ReleasePolicyDto toReleasePolicyDto(ReleasePolicy policy) {
        ReleasePolicyDto dto = new ReleasePolicyDto();
        dto.setId(policy.getId());
        dto.setOrganizationId(policy.getOrganizationId());
        dto.setPolicyCode(policy.getPolicyCode());
        dto.setPolicyName(policy.getPolicyName());
        dto.setAdvanceDays(policy.getAdvanceDays());
        dto.setSlotMinutes(policy.getSlotMinutes());
        dto.setCapacityPerSlot(policy.getCapacityPerSlot());
        dto.setChannelQuotas(ReleasePolicyJsonSupport.parseChannelQuotas(policy.getChannelQuotaJson()));
        return dto;
    }

    /**
     * 保存放号策略请求 → 领域实体。
     *
     * @param request 保存放号策略请求
     * @return 放号策略领域实体
     */
    public ReleasePolicy toReleasePolicyEntity(SaveReleasePolicyRequest request) {
        ReleasePolicy policy = new ReleasePolicy();
        policy.setId(request.getId());
        policy.setOrganizationId(request.getOrganizationId());
        // 编码可选：空白交由领域层自动生成
        policy.setPolicyCode(trimToNull(request.getPolicyCode()));
        policy.setPolicyName(request.getPolicyName());
        policy.setAdvanceDays(request.getAdvanceDays());
        policy.setSlotMinutes(request.getSlotMinutes());
        policy.setCapacityPerSlot(request.getCapacityPerSlot());
        policy.setChannelQuotaJson(request.getChannelQuotaJson());
        return policy;
    }

    /**
     * 挂号策略实体 → DTO。
     *
     * @param policy 挂号策略实体
     * @return 挂号策略 DTO
     */
    public RegistrationPolicyDto toRegistrationPolicyDto(RegistrationPolicy policy) {
        RegistrationPolicyDto dto = new RegistrationPolicyDto();
        dto.setId(policy.getId());
        dto.setOrganizationId(policy.getOrganizationId());
        dto.setWalkInSlotMode(policy.getWalkInSlotMode());
        dto.setAutoCheckInOnRegister(policy.getAutoCheckInOnRegister());
        dto.setOnCancelPolicy(policy.getOnCancelPolicy());
        dto.setAllowEditPractitionerFromAppointment(policy.getAllowEditPractitionerFromAppointment());
        return dto;
    }

    /**
     * 保存挂号策略请求 → 领域实体。
     *
     * @param request 保存挂号策略请求
     * @return 挂号策略领域实体
     */
    public RegistrationPolicy toRegistrationPolicyEntity(SaveRegistrationPolicyRequest request) {
        RegistrationPolicy policy = new RegistrationPolicy();
        policy.setId(request.getId());
        policy.setOrganizationId(request.getOrganizationId());
        policy.setWalkInSlotMode(request.getWalkInSlotMode());
        policy.setAutoCheckInOnRegister(request.getAutoCheckInOnRegister());
        policy.setOnCancelPolicy(request.getOnCancelPolicy());
        policy.setAllowEditPractitionerFromAppointment(request.getAllowEditPractitionerFromAppointment());
        return policy;
    }

    /**
     * 挂号策略 → 前台挂号模式快照（无配置时由 AppService 传 null）。
     *
     * @param policy 挂号策略实体，无配置时为 null
     * @return 前台挂号模式快照响应
     */
    public GetRegistrationModeResponse toGetRegistrationModeResponse(RegistrationPolicy policy) {
        GetRegistrationModeResponse response = new GetRegistrationModeResponse();
        response.setBookingEnabled(Boolean.TRUE);
        if (policy != null && policy.getWalkInSlotMode() != null && !policy.getWalkInSlotMode().isBlank()) {
            response.setWalkInSlotMode(policy.getWalkInSlotMode().trim());
        } else {
            // 无院点挂号策略时默认现场占当日号源（与排班维护路径 A/B 对齐，无需维护预约策略页）。
            response.setWalkInSlotMode(RegistrationWalkInSlotModeConstants.WALK_IN_WITH_SLOT);
        }
        return response;
    }

    /**
     * 停诊变更策略实体 → DTO（persisted 由 id 是否非空推断）。
     *
     * @param policy 停诊变更策略实体
     * @return 停诊变更策略 DTO
     */
    public ScheduleChangePolicyDto toScheduleChangePolicyDto(ScheduleChangePolicy policy) {
        return this.toScheduleChangePolicyDto(policy, policy.getId() != null);
    }

    /**
     * 停诊变更策略实体 → DTO，显式指定是否已持久化。
     *
     * @param policy 停诊变更策略实体
     * @param persisted 是否已持久化
     * @return 停诊变更策略 DTO
     */
    public ScheduleChangePolicyDto toScheduleChangePolicyDto(ScheduleChangePolicy policy, boolean persisted) {
        ScheduleChangePolicyDto dto = new ScheduleChangePolicyDto();
        dto.setId(policy.getId());
        dto.setOrganizationId(policy.getOrganizationId());
        dto.setPersisted(persisted);
        dto.setOnPractitionerStopMode(policy.getOnPractitionerStopMode());
        dto.setTransferMatchRule(policy.getTransferMatchRule());
        dto.setTransferRequireSameService(policy.getTransferRequireSameService());
        dto.setTransferAllowCrossPractitioner(policy.getTransferAllowCrossPractitioner());
        dto.setTransferPatientConfirmMode(policy.getTransferPatientConfirmMode());
        dto.setBlockDeactivateIfUnhandled(policy.getBlockDeactivateIfUnhandled());
        dto.setNotifyOnTransfer(policy.getNotifyOnTransfer());
        dto.setNotifyOnCancelStop(policy.getNotifyOnCancelStop());
        dto.setFreezeSourceSlotOnPreview(policy.getFreezeSourceSlotOnPreview());
        dto.setFreezeTtlMinutes(policy.getFreezeTtlMinutes());
        return dto;
    }

    /**
     * 保存停诊变更策略请求 → 领域实体。
     *
     * @param request 保存停诊变更策略请求
     * @return 停诊变更策略领域实体
     */
    public ScheduleChangePolicy toScheduleChangePolicyEntity(SaveScheduleChangePolicyRequest request) {
        ScheduleChangePolicy policy = new ScheduleChangePolicy();
        policy.setId(request.getId());
        policy.setOrganizationId(request.getOrganizationId());
        policy.setOnPractitionerStopMode(request.getOnPractitionerStopMode());
        policy.setTransferMatchRule(request.getTransferMatchRule());
        policy.setTransferRequireSameService(request.getTransferRequireSameService());
        policy.setTransferAllowCrossPractitioner(request.getTransferAllowCrossPractitioner());
        policy.setTransferPatientConfirmMode(request.getTransferPatientConfirmMode());
        policy.setBlockDeactivateIfUnhandled(request.getBlockDeactivateIfUnhandled());
        policy.setNotifyOnTransfer(request.getNotifyOnTransfer());
        policy.setNotifyOnCancelStop(request.getNotifyOnCancelStop());
        policy.setFreezeSourceSlotOnPreview(request.getFreezeSourceSlotOnPreview());
        policy.setFreezeTtlMinutes(request.getFreezeTtlMinutes());
        return policy;
    }
}
