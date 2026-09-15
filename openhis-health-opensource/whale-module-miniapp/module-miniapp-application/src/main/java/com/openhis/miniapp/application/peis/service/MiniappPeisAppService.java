/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.peis.service;

import com.openhis.health.domain.share.constants.PeisContractTypeCodes;
import com.openhis.health.domain.share.extension.booking.HcBookableDayView;
import com.openhis.health.domain.share.extension.booking.HcBookableSlotView;
import com.openhis.health.domain.share.extension.booking.HcCreateBookingCommand;
import com.openhis.health.domain.share.extension.booking.HcCreateBookingResult;
import com.openhis.health.domain.share.extension.booking.HealthCheckupSlotOccupyPort;
import com.openhis.health.domain.share.extension.peis.PeisContractPatientPort;
import com.openhis.health.domain.share.extension.peis.PeisContractTypeView;
import com.openhis.health.domain.share.extension.peis.PeisGroupRosterDeskView;
import com.openhis.health.domain.share.extension.peis.PeisGroupRosterPatientPort;
import com.openhis.health.domain.share.extension.peis.PeisGroupRosterSelectResult;
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
import com.openhis.miniapp.application.support.MiniappPatientAccessSupport;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 小程序体检 BFF：团检选定 + 号源占号（18 号需求）。
 *
 * <p>团检经 {@link PeisGroupRosterPatientPort}；号源经 {@link HealthCheckupSlotOccupyPort}；
 * 合同类型经 {@link PeisContractPatientPort}。</p>
 */
@Service
@RequiredArgsConstructor
public class MiniappPeisAppService {

    private static final String ORIGIN_CHANNEL_MINIAPP = "MINIAPP";

    private final ObjectProvider<PeisGroupRosterPatientPort> peisGroupRosterPatientPortProvider;
    private final ObjectProvider<PeisContractPatientPort> peisContractPatientPortProvider;
    private final ObjectProvider<HealthCheckupSlotOccupyPort> healthCheckupSlotOccupyPortProvider;
    private final MiniappPatientAccessSupport miniappPatientAccessSupport;

    /**
     * 查询当前就诊人团检名册（含可选套餐 / 选定态）。
     *
     * @param request 可选患者覆盖
     * @return 名册匹配列表
     */
    public MiniappLookupRosterResponse lookupRosterForDesk(MiniappLookupRosterRequest request) {
        // Gate：解析会话患者
        Long patientOverride = null;
        if (request != null) {
            patientOverride = request.getPatientId();
        }
        Long patientId = miniappPatientAccessSupport.requirePatientId(patientOverride);
        // Pre：委托 peis 名册 Port
        List<PeisGroupRosterDeskView> matches = requireRosterPort().lookupByPatientId(patientId);
        MiniappLookupRosterResponse response = new MiniappLookupRosterResponse();
        response.setMatches(matches);
        // Asm：返回名册上下文
        return response;
    }

    /**
     * 选定套餐并建单（生成流水号）。
     *
     * @param request 名册与套餐
     * @return 建单结果
     */
    public MiniappSelectRosterPackageResponse selectRosterPackage(MiniappSelectRosterPackageRequest request) {
        // Gate：名册与套餐必填
        if (request == null || request.getRosterId() == null || request.getPackageActivityId() == null) {
            throw new ValidationException("选定建单失败：名册与套餐不能为空");
        }
        Long patientId = miniappPatientAccessSupport.requirePatientId(request.getPatientId());
        Long operatorId = miniappPatientAccessSupport.requireOperatorId();
        // Cmd：选定建单
        PeisGroupRosterSelectResult result = requireRosterPort().selectPackage(
            patientId,
            request.getRosterId(),
            request.getPackageActivityId(),
            request.getOrgId(),
            operatorId);
        // Asm：映射响应
        return toResponse(result);
    }

    /**
     * 到检前换套餐（流水号不变）。
     *
     * @param request 名册与新套餐
     * @return 换套餐结果
     */
    public MiniappSelectRosterPackageResponse changeRosterSelectedPackage(
            MiniappChangeRosterPackageRequest request) {
        // Gate：名册与套餐必填
        if (request == null || request.getRosterId() == null || request.getPackageActivityId() == null) {
            throw new ValidationException("换套餐失败：名册与套餐不能为空");
        }
        Long patientId = miniappPatientAccessSupport.requirePatientId(request.getPatientId());
        Long operatorId = miniappPatientAccessSupport.requireOperatorId();
        // Cmd：换套餐
        PeisGroupRosterSelectResult result = requireRosterPort().changePackage(
            patientId,
            request.getRosterId(),
            request.getPackageActivityId(),
            operatorId);
        // Asm：映射响应
        return toResponse(result);
    }

    /**
     * 列出已启用合同类型字典（号源分池只读）。
     *
     * @return 类型列表
     */
    public List<PeisContractTypeView> listContractTypes() {
        // Pre：委托 peis 合同 Port
        return requireContractPort().listEnabledContractTypes();
    }

    /**
     * 查询会话患者有效团检合同（0=散客；多份手选）。
     *
     * @param request 可选患者覆盖
     * @return 有效合同
     */
    public List<PeisValidContractView> listValidContractsForPatient(MiniappListValidContractsRequest request) {
        // Gate：解析会话患者
        Long patientOverride = null;
        if (request != null) {
            patientOverride = request.getPatientId();
        }
        Long patientId = miniappPatientAccessSupport.requirePatientId(patientOverride);
        // Pre：委托 peis 有效合同查询
        return requireContractPort().listValidContractsForPatient(patientId);
    }

    /**
     * 查询可约日期（须会话患者已登录）。
     *
     * @param request 机构、类型与日期区间
     * @return 可约日
     */
    public List<HcBookableDayView> listBookableDays(MiniappListHcBookableDaysRequest request) {
        // Gate：校验会话患者与请求体
        if (request == null) {
            throw new ValidationException("查询可约日失败：请求不能为空");
        }
        miniappPatientAccessSupport.requirePatientId(request.getPatientId());
        // Pre：委托 booking 占号 Port
        return requireOccupyPort().listBookableDays(
            request.getOrganizationId(),
            request.getContractTypeCode(),
            request.getCampusCode(),
            request.getFromDate(),
            request.getToDate());
    }

    /**
     * 查询某日可约时段。
     *
     * @param request 机构、日期与类型
     * @return 可约时段
     */
    public List<HcBookableSlotView> listBookableSlots(MiniappListHcBookableSlotsRequest request) {
        // Gate：校验会话患者与请求体
        if (request == null) {
            throw new ValidationException("查询可约时段失败：请求不能为空");
        }
        miniappPatientAccessSupport.requirePatientId(request.getPatientId());
        // Pre：委托 booking 占号 Port
        return requireOccupyPort().listBookableSlots(
            request.getOrganizationId(),
            request.getPlanDate(),
            request.getContractTypeCode(),
            request.getCampusCode());
    }

    /**
     * 创建体检号源预约（占号）；强制来源渠道 MINIAPP。
     *
     * @param request 套餐、时段与合同
     * @return 预约结果
     */
    public HcCreateBookingResult createBooking(MiniappCreateHcBookingRequest request) {
        // Gate：请求与关键主键
        if (request == null
            || request.getOrganizationId() == null
            || request.getPackageId() == null
            || request.getDaySlotId() == null) {
            throw new ValidationException("创建体检预约失败：机构、套餐与时段不能为空");
        }
        Long patientId = miniappPatientAccessSupport.requirePatientId(request.getPatientId());
        Long operatorId = miniappPatientAccessSupport.requireOperatorId();
        // Pre：无合同则散客池；有合同须带类型编码
        String typeCode = request.getContractTypeCode();
        if (request.getContractId() == null) {
            typeCode = PeisContractTypeCodes.WALK_IN;
        } else if (!StringUtils.hasText(typeCode)) {
            throw new ValidationException("创建体检预约失败：有合同时须传合同类型");
        }
        HcCreateBookingCommand command = new HcCreateBookingCommand();
        command.setOrganizationId(request.getOrganizationId());
        command.setPatientId(patientId);
        command.setPackageId(request.getPackageId());
        command.setDaySlotId(request.getDaySlotId());
        command.setContractId(request.getContractId());
        command.setContractTypeCode(typeCode);
        command.setOriginChannel(ORIGIN_CHANNEL_MINIAPP);
        command.setOperatorId(operatorId);
        // Cmd：委托 booking 占号
        return requireOccupyPort().createBooking(command);
    }

    /**
     * 解析团检名册 Port；不可用时提示模块未启用。
     *
     * @return 非空 Port
     */
    private PeisGroupRosterPatientPort requireRosterPort() {
        PeisGroupRosterPatientPort port = peisGroupRosterPatientPortProvider.getIfAvailable();
        if (port == null) {
            throw new ValidationException("团检能力未就绪：PeisGroupRosterPatientPort 不可用");
        }
        return port;
    }

    /**
     * 解析合同只读 Port。
     *
     * @return 非空 Port
     */
    private PeisContractPatientPort requireContractPort() {
        PeisContractPatientPort port = peisContractPatientPortProvider.getIfAvailable();
        if (port == null) {
            throw new ValidationException("合同字典能力未就绪：PeisContractPatientPort 不可用");
        }
        return port;
    }

    /**
     * 解析号源占号 Port。
     *
     * @return 非空 Port
     */
    private HealthCheckupSlotOccupyPort requireOccupyPort() {
        HealthCheckupSlotOccupyPort port = healthCheckupSlotOccupyPortProvider.getIfAvailable();
        if (port == null) {
            throw new ValidationException("体检号源能力未就绪：HealthCheckupSlotOccupyPort 不可用");
        }
        return port;
    }

    /**
     * 团检选定结果映射为 miniapp 响应。
     *
     * @param result Port 结果
     * @return HTTP 响应体
     */
    private static MiniappSelectRosterPackageResponse toResponse(PeisGroupRosterSelectResult result) {
        MiniappSelectRosterPackageResponse response = new MiniappSelectRosterPackageResponse();
        if (result == null) {
            return response;
        }
        response.setRosterId(result.getRosterId());
        response.setEncounterId(result.getEncounterId());
        response.setBusNo(result.getBusNo());
        response.setSelectedPackageActivityId(result.getSelectedPackageActivityId());
        return response;
    }
}
