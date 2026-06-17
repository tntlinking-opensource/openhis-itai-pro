package com.openhis.whale.booking.application.policy;

import com.openhis.whale.booking.application.policy.mapper.BookingPolicyMapper;
import com.openhis.whale.booking.application.policy.request.DeleteReleasePolicyRequest;
import com.openhis.whale.booking.application.policy.request.GetRegistrationModeRequest;
import com.openhis.whale.booking.application.policy.request.GetRegistrationPolicyRequest;
import com.openhis.whale.booking.application.policy.request.PageReleasePoliciesRequest;
import com.openhis.whale.booking.application.policy.request.GetScheduleChangePolicyRequest;
import com.openhis.whale.booking.application.policy.request.SaveScheduleChangePolicyRequest;
import com.openhis.whale.booking.application.policy.request.SaveRegistrationPolicyRequest;
import com.openhis.whale.booking.application.policy.request.SaveReleasePolicyRequest;
import com.openhis.whale.booking.application.policy.response.GetRegistrationModeResponse;
import com.openhis.whale.booking.application.policy.response.PageReleasePoliciesResponse;
import com.openhis.whale.booking.application.policy.response.RegistrationPolicyDto;
import com.openhis.whale.booking.application.policy.response.ReleasePolicyDto;
import com.openhis.whale.booking.application.policy.response.ScheduleChangePolicyDto;
import com.openhis.whale.booking.application.support.BookingOperatorResolver;
import com.openhis.whale.booking.application.support.BookingPagedResultBuilder;
import com.openhis.whale.booking.application.policy.response.SaveReleasePolicyResponse;
import com.openhis.whale.booking.domain.policy.entity.ReleasePolicy;
import com.openhis.whale.booking.domain.policy.entity.ScheduleChangePolicy;
import com.openhis.whale.booking.domain.policy.service.PolicyDomainService;
import com.openhis.whale.booking.domain.policy.service.PolicyQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 放号/挂号策略应用服务。
 */
@Service
@Validated
@Slf4j
@RequiredArgsConstructor
public class BookingPolicyAppService {

    private final PolicyQueryService policyQueryService;
    private final PolicyDomainService policyDomainService;
    private final BookingPolicyMapper bookingPolicyMapper;
    private final BookingOperatorResolver bookingOperatorResolver;

    public PageReleasePoliciesResponse pageReleasePolicies(@Valid PageReleasePoliciesRequest request) {
        Page<ReleasePolicy> page = policyQueryService.pageReleasePolicies(
            request.getOrganizationId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<ReleasePolicyDto> rows = new ArrayList<>();
        for (ReleasePolicy policy : page.getContent()) {
            rows.add(bookingPolicyMapper.toReleasePolicyDto(policy));
        }
        return BookingPagedResultBuilder.build(new PageReleasePoliciesResponse(), rows, page);
    }

    @Transactional
    public SaveReleasePolicyResponse saveReleasePolicy(@Valid SaveReleasePolicyRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ReleasePolicy saved = policyDomainService.saveReleasePolicy(
            bookingPolicyMapper.toReleasePolicyEntity(request), operatorId);
        SaveReleasePolicyResponse response = new SaveReleasePolicyResponse();
        response.setId(saved.getId());
        return response;
    }

    @Transactional
    public void deleteReleasePolicy(@Valid DeleteReleasePolicyRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        policyDomainService.deleteReleasePolicy(request.getId(), operatorId);
    }

    public RegistrationPolicyDto getRegistrationPolicy(@Valid GetRegistrationPolicyRequest request) {
        return policyQueryService.findRegistrationPolicyByOrganization(request.getOrganizationId())
            .map(bookingPolicyMapper::toRegistrationPolicyDto)
            .orElse(null);
    }

    @Transactional
    public RegistrationPolicyDto saveRegistrationPolicy(@Valid SaveRegistrationPolicyRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        return bookingPolicyMapper.toRegistrationPolicyDto(
            policyDomainService.saveRegistrationPolicy(
                bookingPolicyMapper.toRegistrationPolicyEntity(request), operatorId));
    }

    public GetRegistrationModeResponse getRegistrationMode(@Valid GetRegistrationModeRequest request) {
        return bookingPolicyMapper.toGetRegistrationModeResponse(
            policyQueryService.findRegistrationPolicyByOrganization(request.getOrganizationId()).orElse(null));
    }

    /**
     * 加载科室停诊变更策略（2.20）；无配置时返回默认快照且 persisted=false。
     */
    public ScheduleChangePolicyDto getScheduleChangePolicy(@Valid GetScheduleChangePolicyRequest request) {
        return policyQueryService.findScheduleChangePolicyByOrganization(request.getOrganizationId())
            .map(bookingPolicyMapper::toScheduleChangePolicyDto)
            .orElseGet(() -> bookingPolicyMapper.toScheduleChangePolicyDto(
                ScheduleChangePolicy.defaultForOrganization(request.getOrganizationId()), false));
    }

    /**
     * 保存科室停诊变更策略（2.20）；每科室至多一条有效配置。
     */
    @Transactional
    public ScheduleChangePolicyDto saveScheduleChangePolicy(@Valid SaveScheduleChangePolicyRequest request) {
        Long operatorId = bookingOperatorResolver.requireOperatorId();
        ScheduleChangePolicy saved = policyDomainService.saveScheduleChangePolicy(
            bookingPolicyMapper.toScheduleChangePolicyEntity(request), operatorId);
        return bookingPolicyMapper.toScheduleChangePolicyDto(saved);
    }
}
