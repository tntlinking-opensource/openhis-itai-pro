/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.web;

import com.openhis.health.domain.share.extension.booking.HcBookableDayView;
import com.openhis.health.domain.share.extension.booking.HcBookableSlotView;
import com.openhis.health.domain.share.extension.booking.HcCreateBookingResult;
import com.openhis.health.domain.share.extension.peis.PeisContractTypeView;
import com.openhis.health.domain.share.extension.peis.PeisValidContractView;
import com.openhis.miniapp.application.peis.request.MiniappChangeRosterPackageRequest;
import com.openhis.miniapp.application.peis.request.MiniappCreateHcBookingRequest;
import com.openhis.miniapp.application.peis.request.MiniappListHcBookableDaysRequest;
import com.openhis.miniapp.application.peis.request.MiniappListHcBookableSlotsRequest;
import com.openhis.miniapp.application.peis.request.MiniappListValidContractsRequest;
import com.openhis.miniapp.application.peis.request.MiniappLookupRosterRequest;
import com.openhis.miniapp.application.peis.request.MiniappSelectRosterPackageRequest;
import com.openhis.miniapp.application.peis.response.MiniappLookupRosterResponse;
import com.openhis.miniapp.application.peis.response.MiniappSelectRosterPackageResponse;
import com.openhis.miniapp.application.peis.service.MiniappPeisAppService;
import com.openhis.whale.core.application.dto.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序体检 HTTP 门面（{@code /api/miniapp/miniappPeis/*}）。
 *
 * <p>与 {@link MiniappPeisAppService} 同名方法对齐；AppService 动态路由另暴露
 * {@code /api/app/miniappPeis/*}。</p>
 */
@RestController
@RequestMapping("/api/miniapp/miniappPeis")
@RequiredArgsConstructor
public class MiniappPeisApiController {

    private final MiniappPeisAppService miniappPeisAppService;

    /**
     * 查询团检名册上下文。
     *
     * @param request 可选患者
     * @return 名册匹配
     */
    @PostMapping("/lookupRosterForDesk")
    public Result<MiniappLookupRosterResponse> lookupRosterForDesk(
            @RequestBody(required = false) MiniappLookupRosterRequest request) {
        return Result.success(miniappPeisAppService.lookupRosterForDesk(request));
    }

    /**
     * 选定套餐建单。
     *
     * @param request 名册与套餐
     * @return 建单结果
     */
    @PostMapping("/selectRosterPackage")
    public Result<MiniappSelectRosterPackageResponse> selectRosterPackage(
            @RequestBody MiniappSelectRosterPackageRequest request) {
        return Result.success(miniappPeisAppService.selectRosterPackage(request));
    }

    /**
     * 到检前换套餐。
     *
     * @param request 名册与新套餐
     * @return 换套餐结果
     */
    @PostMapping("/changeRosterSelectedPackage")
    public Result<MiniappSelectRosterPackageResponse> changeRosterSelectedPackage(
            @RequestBody MiniappChangeRosterPackageRequest request) {
        return Result.success(miniappPeisAppService.changeRosterSelectedPackage(request));
    }

    /**
     * 合同类型字典。
     *
     * @return 类型列表
     */
    @PostMapping("/listContractTypes")
    public Result<List<PeisContractTypeView>> listContractTypes() {
        return Result.success(miniappPeisAppService.listContractTypes());
    }

    /**
     * 会话患者有效合同。
     *
     * @param request 可选患者
     * @return 有效合同
     */
    @PostMapping("/listValidContractsForPatient")
    public Result<List<PeisValidContractView>> listValidContractsForPatient(
            @RequestBody(required = false) MiniappListValidContractsRequest request) {
        return Result.success(miniappPeisAppService.listValidContractsForPatient(request));
    }

    /**
     * 可约日期。
     *
     * @param request 机构与类型区间
     * @return 可约日
     */
    @PostMapping("/listBookableDays")
    public Result<List<HcBookableDayView>> listBookableDays(
            @RequestBody MiniappListHcBookableDaysRequest request) {
        return Result.success(miniappPeisAppService.listBookableDays(request));
    }

    /**
     * 可约时段。
     *
     * @param request 机构与日期
     * @return 可约时段
     */
    @PostMapping("/listBookableSlots")
    public Result<List<HcBookableSlotView>> listBookableSlots(
            @RequestBody MiniappListHcBookableSlotsRequest request) {
        return Result.success(miniappPeisAppService.listBookableSlots(request));
    }

    /**
     * 体检号源下单占号。
     *
     * @param request 套餐与时段
     * @return 预约结果
     */
    @PostMapping("/createBooking")
    public Result<HcCreateBookingResult> createBooking(@RequestBody MiniappCreateHcBookingRequest request) {
        return Result.success(miniappPeisAppService.createBooking(request));
    }
}
