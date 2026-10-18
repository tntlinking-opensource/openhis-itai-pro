/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.policy;

import com.zxplat.plugin.booking.application.policy.mapper.BookingPolicyMapper;
import com.zxplat.plugin.booking.application.policy.request.DeleteReleasePolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.GetRegistrationModeRequest;
import com.zxplat.plugin.booking.application.policy.request.GetRegistrationPolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.PageReleasePoliciesRequest;
import com.zxplat.plugin.booking.application.policy.request.GetScheduleChangePolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.SaveScheduleChangePolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.SaveRegistrationPolicyRequest;
import com.zxplat.plugin.booking.application.policy.request.SaveReleasePolicyRequest;
import com.zxplat.plugin.booking.application.policy.response.GetRegistrationModeResponse;
import com.zxplat.plugin.booking.application.policy.response.PageReleasePoliciesResponse;
import com.zxplat.plugin.booking.application.policy.response.RegistrationPolicyDto;
import com.zxplat.plugin.booking.application.policy.response.ReleasePolicyDto;
import com.zxplat.plugin.booking.application.policy.response.ScheduleChangePolicyDto;
import com.zxplat.plugin.booking.application.share.BookingOperatorResolver;
import com.zxplat.plugin.booking.application.share.BookingPagedResultBuilder;
import com.zxplat.plugin.booking.application.policy.response.SaveReleasePolicyResponse;
import com.zxplat.plugin.booking.domain.policy.entity.ReleasePolicy;
import com.zxplat.plugin.booking.domain.policy.entity.RegistrationPolicy;
import com.zxplat.plugin.booking.domain.policy.entity.ScheduleChangePolicy;
import com.zxplat.plugin.booking.domain.policy.service.PolicyDomainService;
import com.zxplat.plugin.booking.domain.policy.service.PolicyQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 放号/挂号/停诊变更策略应用服务（P 线 2.16～2.20）。
 *
 * <p>职责边界：放号策略、挂号模式、科室停诊变更策略的查询与维护；不含号源生成与预约写路径。</p>
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/bookingPolicy/{methodName}}。</p>
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class BookingPolicyAppService {

    /** 策略读侧：分页、按机构加载。 */
    private final PolicyQueryService policyQueryService;
    /** 策略写侧：保存与软删。 */
    private final PolicyDomainService policyDomainService;
    /** Request/Entity/DTO 映射。 */
    private final BookingPolicyMapper bookingPolicyMapper;
    /** 写路径操作人。 */
    private final BookingOperatorResolver bookingOperatorResolver;

    /**
     * 放号策略分页列表（2.16）。
     *
     * @param request 机构与分页参数
     * @return 放号策略分页结果
     */
    public PageReleasePoliciesResponse pageReleasePolicies(@Valid PageReleasePoliciesRequest request) {
        // Pre：按机构分页加载放号策略实体。
        Page<ReleasePolicy> page = policyQueryService.pageReleasePolicies(
            request.getOrganizationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<ReleasePolicyDto> rows = new ArrayList<>();
        for (ReleasePolicy policy : page.getContent()) {
            rows.add(bookingPolicyMapper.toReleasePolicyDto(policy));
        }
        // Asm：映射 DTO 并包装分页结果。
        return BookingPagedResultBuilder.build(new PageReleasePoliciesResponse(), rows, page);
    }

    /**
     * 保存放号策略（2.16）。
     *
     * @param request 策略字段
     * @return 保存后的主键
     */
    public SaveReleasePolicyResponse saveReleasePolicy(@Valid SaveReleasePolicyRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化放号策略。
        ReleasePolicy saved = policyDomainService.saveReleasePolicy(
            bookingPolicyMapper.toReleasePolicyEntity(request), operatorId);
        // Asm：返回主键。
        SaveReleasePolicyResponse response = new SaveReleasePolicyResponse();
        response.setId(saved.getId());
        return response;
    }

    /**
     * 删除放号策略（2.16）。
     *
     * @param request 策略主键
     */
    public void deleteReleasePolicy(@Valid DeleteReleasePolicyRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：软删放号策略。
        policyDomainService.deleteReleasePolicy(request.getId(), operatorId);
    }

    /**
     * 加载机构挂号策略（2.17）；无配置时返回 null。
     *
     * @param request 机构主键
     * @return 挂号策略 DTO，或 null
     */
    public RegistrationPolicyDto getRegistrationPolicy(@Valid GetRegistrationPolicyRequest request) {
        // Pre：按机构加载挂号策略；无配置时下游返回 null。
        // Asm：实体映射为挂号策略 DTO。
        return policyQueryService.findRegistrationPolicyByOrganization(request.getOrganizationId())
            .map(bookingPolicyMapper::toRegistrationPolicyDto)
            .orElse(null);
    }

    /**
     * 保存机构挂号策略（2.17）。
     *
     * @param request 挂号策略字段
     * @return 保存后的 DTO
     */
    public RegistrationPolicyDto saveRegistrationPolicy(@Valid SaveRegistrationPolicyRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化挂号策略。
        return bookingPolicyMapper.toRegistrationPolicyDto(
            policyDomainService.saveRegistrationPolicy(
                bookingPolicyMapper.toRegistrationPolicyEntity(request), operatorId));
    }

    /**
     * 获取挂号模式快照（2.18），供前台判断是否启用预约/现场模式。
     *
     * @param request 机构主键
     * @return 挂号模式响应（无配置时返回默认 walkInSlotMode）
     */
    public GetRegistrationModeResponse getRegistrationMode(@Valid GetRegistrationModeRequest request) {
        // Pre：按机构读取挂号策略；organizationId 为空时仅返回模块默认快照。
        RegistrationPolicy policy = null;
        if (request.getOrganizationId() != null) {
            policy = policyQueryService.findRegistrationPolicyByOrganization(request.getOrganizationId()).orElse(null);
        }
        // Asm：映射为前台挂号模式响应（无配置时默认 WALK_IN_WITH_SLOT）。
        return bookingPolicyMapper.toGetRegistrationModeResponse(policy);
    }

    /**
     * 加载科室停诊变更策略（2.20）；无配置时返回默认快照且 persisted=false。
     *
     * @param request 机构主键
     * @return 停诊变更策略 DTO
     */
    public ScheduleChangePolicyDto getScheduleChangePolicy(@Valid GetScheduleChangePolicyRequest request) {
        // Pre：按机构加载停诊变更策略；缺失时用默认快照。
        // Asm：映射停诊变更策略 DTO（含 persisted 标记）。
        return policyQueryService.findScheduleChangePolicyByOrganization(request.getOrganizationId())
            .map(bookingPolicyMapper::toScheduleChangePolicyDto)
            .orElseGet(() -> bookingPolicyMapper.toScheduleChangePolicyDto(
                ScheduleChangePolicy.defaultForOrganization(request.getOrganizationId()), false));
    }

    /**
     * 保存科室停诊变更策略（2.20）；每科室至多一条有效配置。
     *
     * @param request 停诊变更策略字段
     * @return 保存后的 DTO
     */
    public ScheduleChangePolicyDto saveScheduleChangePolicy(@Valid SaveScheduleChangePolicyRequest request) {
        // Gate：写路径须登录。
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        // Cmd：持久化停诊变更策略。
        ScheduleChangePolicy saved = policyDomainService.saveScheduleChangePolicy(
            bookingPolicyMapper.toScheduleChangePolicyEntity(request), operatorId);
        // Asm：映射 DTO。
        return bookingPolicyMapper.toScheduleChangePolicyDto(saved);
    }
}
