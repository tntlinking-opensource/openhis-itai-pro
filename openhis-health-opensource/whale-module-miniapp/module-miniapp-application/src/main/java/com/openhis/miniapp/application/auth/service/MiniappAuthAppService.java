/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.miniapp.application.auth.service;

import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityPort;
import com.openhis.health.domain.share.extension.campus.PeisCampusIdentityView;
import com.openhis.health.domain.share.extension.miniapp.MiniappPractitionerLookupPort;
import com.openhis.health.domain.share.extension.miniapp.MiniappPractitionerLookupView;
import com.openhis.miniapp.application.auth.request.MiniappAutoLoginRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindCampusStaffRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindCampusStudentRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindPatientRequest;
import com.openhis.miniapp.application.auth.request.MiniappBindPractitionerRequest;
import com.openhis.miniapp.application.auth.request.MiniappDevLoginRequest;
import com.openhis.miniapp.application.auth.request.MiniappSwitchPatientRequest;
import com.openhis.miniapp.application.auth.request.MiniappSwitchPractitionerRequest;
import com.openhis.miniapp.application.auth.response.MiniappLoginResponse;
import com.openhis.miniapp.application.auth.response.MiniappSessionResponse;
import com.openhis.miniapp.application.auth.wechat.WechatCode2SessionClient;
import com.openhis.miniapp.application.auth.wechat.WechatCode2SessionClient.Code2SessionResult;
import com.openhis.miniapp.application.config.MiniappProperties;
import com.openhis.miniapp.domain.auth.service.MapAuthDomainService;
import com.openhis.miniapp.domain.auth.service.MapAuthDomainService.LoginResult;
import com.openhis.miniapp.domain.context.MiniappContext;
import com.openhis.miniapp.domain.patientbinding.entity.MapPatientBinding;
import com.openhis.miniapp.domain.practitionerbinding.entity.MapPractitionerBinding;
import com.openhis.miniapp.domain.session.entity.MapSession;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 小程序身份 AppService（DM-MAP-AUTH · miniappAuth）。
 */
@Service
@RequiredArgsConstructor
public class MiniappAuthAppService {

    private static final String BIND_CHANNEL_CAMPUS_STUDENT = "campus_student";
    private static final String BIND_CHANNEL_CAMPUS_STAFF = "campus_staff";
    private static final String BIND_CHANNEL_PRACTITIONER_BUSNO = "practitioner_busno";
    private static final String CAMPUS_REF_STUDENT = "student";
    private static final String CAMPUS_REF_STAFF = "staff";

    private final MapAuthDomainService mapAuthDomainService;
    private final MiniappProperties miniappProperties;
    private final WechatCode2SessionClient wechatCode2SessionClient;
    private final ObjectProvider<PeisCampusIdentityPort> peisCampusIdentityPortProvider;
    private final ObjectProvider<MiniappPractitionerLookupPort> miniappPractitionerLookupPortProvider;

    /**
     * 微信 code 登录；配置了 appSecret 走正式 code2session，否则 mock 回落。
     */
    @Transactional(rollbackFor = Exception.class)
    public MiniappLoginResponse autoLogin(MiniappAutoLoginRequest request) {
        if (request == null || request.getCode() == null || request.getCode().isBlank()) {
            throw new ValidationException("登录失败：code 不能为空");
        }
        String appId = resolveAppId(request.getAppId());
        String code = request.getCode().trim();
        String openid;
        String unionId = null;
        if (hasWechatSecret()) {
            if (miniappProperties.isMockLoginEnabled() && code.startsWith("mock:")) {
                openid = code.substring("mock:".length()).trim();
                if (openid.isEmpty()) {
                    throw new ValidationException("mock 登录失败：openid 为空");
                }
            } else {
                Code2SessionResult wx = wechatCode2SessionClient.exchange(appId, code);
                openid = wx.getOpenid();
                unionId = wx.getUnionId();
            }
        } else {
            openid = resolveMockOpenid(code);
        }
        LoginResult login = mapAuthDomainService.loginOrCreateSession(
            appId, openid, unionId, miniappProperties.getSessionTtlHours());
        return toLoginResponse(login);
    }

    /**
     * 开发态明文 openid 登录（仅 mockLoginEnabled=true）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MiniappLoginResponse devLogin(MiniappDevLoginRequest request) {
        if (!miniappProperties.isMockLoginEnabled()) {
            throw new BusinessException("MOCK_LOGIN_DISABLED", "开发登录未启用");
        }
        if (request == null || request.getOpenid() == null || request.getOpenid().isBlank()) {
            throw new ValidationException("开发登录失败：openid 不能为空");
        }
        String appId = resolveAppId(request.getAppId());
        LoginResult login = mapAuthDomainService.loginOrCreateSession(
            appId,
            request.getOpenid().trim(),
            request.getUnionId(),
            miniappProperties.getSessionTtlHours());
        return toLoginResponse(login);
    }

    public MiniappSessionResponse currentSession() {
        MiniappContext ctx = requireContext();
        MiniappSessionResponse response = new MiniappSessionResponse();
        response.setAccountId(ctx.getAccountId());
        response.setSessionId(ctx.getSessionId());
        response.setPatientId(ctx.getPatientId());
        response.setPractitionerId(ctx.getPractitionerId());
        response.setOpenid(ctx.getOpenid());
        response.setAppId(ctx.getAppId());
        response.setBindRequired(ctx.getPatientId() == null);
        return response;
    }

    @Transactional(rollbackFor = Exception.class)
    public void logout() {
        MiniappContext ctx = requireContext();
        Long sessionId = parseSessionId(ctx.getSessionId());
        mapAuthDomainService.revokeSession(sessionId);
    }

    @Transactional(rollbackFor = Exception.class)
    public MiniappSessionResponse bindPatient(MiniappBindPatientRequest request) {
        MiniappContext ctx = requireContext();
        if (request == null || request.getPatientId() == null) {
            throw new ValidationException("绑定失败：patientId 不能为空");
        }
        boolean asDefault = !Boolean.FALSE.equals(request.getAsDefault());
        MapPatientBinding binding = mapAuthDomainService.bindPatient(
            ctx.getAccountId(),
            request.getPatientId(),
            MapAuthDomainService.BIND_CHANNEL_MANUAL,
            null,
            null,
            null,
            asDefault);
        MapSession session = mapAuthDomainService.switchPatient(
            parseSessionId(ctx.getSessionId()), ctx.getAccountId(), binding.getPatientId());
        return toSessionResponse(ctx, session);
    }

    /**
     * 校园学生绑定：学号 + 姓名 → Campus 校验 → 绑 Patient。
     */
    @Transactional(rollbackFor = Exception.class)
    public MiniappSessionResponse bindCampusStudent(MiniappBindCampusStudentRequest request) {
        MiniappContext ctx = requireContext();
        if (request == null || isBlank(request.getStudentNo()) || isBlank(request.getName())) {
            throw new ValidationException("学生绑定失败：学号与姓名不能为空");
        }
        PeisCampusIdentityView identity = requireCampusPort()
            .resolveByStudentNo(TenantUtils.getCurrentTenantId(), request.getStudentNo().trim())
            .orElseThrow(() -> new BusinessException("CAMPUS_STUDENT_NOT_FOUND", "未找到该学号档案"));
        assertCampusNameMatches(identity.getName(), request.getName());
        if (identity.getPatientId() == null) {
            throw new BusinessException("CAMPUS_PATIENT_MISSING", "校园档案未绑定患者，请先到院建档");
        }
        boolean asDefault = !Boolean.FALSE.equals(request.getAsDefault());
        MapPatientBinding binding = mapAuthDomainService.bindPatient(
            ctx.getAccountId(),
            identity.getPatientId(),
            BIND_CHANNEL_CAMPUS_STUDENT,
            CAMPUS_REF_STUDENT,
            identity.getSubjectId(),
            identity.getStudentNo(),
            asDefault);
        MapSession session = mapAuthDomainService.switchPatient(
            parseSessionId(ctx.getSessionId()), ctx.getAccountId(), binding.getPatientId());
        return toSessionResponse(ctx, session);
    }

    /**
     * 校园教职工绑定：工号 + 姓名 → Campus 校验 → 绑 Patient。
     */
    @Transactional(rollbackFor = Exception.class)
    public MiniappSessionResponse bindCampusStaff(MiniappBindCampusStaffRequest request) {
        MiniappContext ctx = requireContext();
        if (request == null || isBlank(request.getEmployeeNo()) || isBlank(request.getName())) {
            throw new ValidationException("教职工绑定失败：工号与姓名不能为空");
        }
        PeisCampusIdentityView identity = requireCampusPort()
            .resolveByEmployeeNo(TenantUtils.getCurrentTenantId(), request.getEmployeeNo().trim())
            .orElseThrow(() -> new BusinessException("CAMPUS_STAFF_NOT_FOUND", "未找到该工号档案"));
        assertCampusNameMatches(identity.getName(), request.getName());
        if (identity.getPatientId() == null) {
            throw new BusinessException("CAMPUS_PATIENT_MISSING", "校园档案未绑定患者，请先到院建档");
        }
        boolean asDefault = !Boolean.FALSE.equals(request.getAsDefault());
        MapPatientBinding binding = mapAuthDomainService.bindPatient(
            ctx.getAccountId(),
            identity.getPatientId(),
            BIND_CHANNEL_CAMPUS_STAFF,
            CAMPUS_REF_STAFF,
            identity.getSubjectId(),
            identity.getEmployeeNo(),
            asDefault);
        MapSession session = mapAuthDomainService.switchPatient(
            parseSessionId(ctx.getSessionId()), ctx.getAccountId(), binding.getPatientId());
        return toSessionResponse(ctx, session);
    }

    @Transactional(rollbackFor = Exception.class)
    public MiniappSessionResponse switchPatient(MiniappSwitchPatientRequest request) {
        MiniappContext ctx = requireContext();
        if (request == null || request.getPatientId() == null) {
            throw new ValidationException("切换失败：patientId 不能为空");
        }
        MapSession session = mapAuthDomainService.switchPatient(
            parseSessionId(ctx.getSessionId()), ctx.getAccountId(), request.getPatientId());
        return toSessionResponse(ctx, session);
    }

    @Transactional(rollbackFor = Exception.class)
    public MiniappSessionResponse bindPractitioner(MiniappBindPractitionerRequest request) {
        MiniappContext ctx = requireContext();
        if (request == null) {
            throw new ValidationException("绑定失败：请求不能为空");
        }
        Long practitionerId = request.getPractitionerId();
        String employeeNo = trimToNull(request.getEmployeeNo());
        if (practitionerId == null) {
            if (employeeNo == null) {
                throw new ValidationException("绑定失败：请提供 practitionerId 或工号");
            }
            MiniappPractitionerLookupView lookup = requirePractitionerPort()
                .resolveByBusNo(TenantUtils.getCurrentTenantId(), employeeNo)
                .orElseThrow(() -> new BusinessException("PRACTITIONER_NOT_FOUND", "未找到该工号医护"));
            assertPractitionerNameMatches(lookup.getName(), request.getName());
            practitionerId = lookup.getPractitionerId();
            employeeNo = lookup.getBusNo();
        }
        boolean asDefault = !Boolean.FALSE.equals(request.getAsDefault());
        String bindChannel = employeeNo != null
            ? BIND_CHANNEL_PRACTITIONER_BUSNO
            : MapAuthDomainService.BIND_CHANNEL_MANUAL;
        MapPractitionerBinding binding = mapAuthDomainService.bindPractitioner(
            ctx.getAccountId(),
            practitionerId,
            bindChannel,
            employeeNo,
            asDefault);
        MapSession session = mapAuthDomainService.switchPractitioner(
            parseSessionId(ctx.getSessionId()), ctx.getAccountId(), binding.getPractitionerId());
        return toSessionResponse(ctx, session);
    }

    @Transactional(rollbackFor = Exception.class)
    public MiniappSessionResponse switchPractitioner(MiniappSwitchPractitionerRequest request) {
        MiniappContext ctx = requireContext();
        if (request == null || request.getPractitionerId() == null) {
            throw new ValidationException("切换失败：practitionerId 不能为空");
        }
        MapSession session = mapAuthDomainService.switchPractitioner(
            parseSessionId(ctx.getSessionId()), ctx.getAccountId(), request.getPractitionerId());
        return toSessionResponse(ctx, session);
    }

    private String resolveMockOpenid(String code) {
        if (!miniappProperties.isMockLoginEnabled()) {
            throw new BusinessException(
                "WECHAT_CODE2SESSION_NOT_CONFIGURED",
                "请配置 openhis.miniapp.wechat-app-secret，或开启 mock-login-enabled");
        }
        if (code.startsWith("mock:")) {
            String openid = code.substring("mock:".length()).trim();
            if (openid.isEmpty()) {
                throw new ValidationException("mock 登录失败：openid 为空");
            }
            return openid;
        }
        return code;
    }

    private boolean hasWechatSecret() {
        return miniappProperties.getWechatAppSecret() != null
            && !miniappProperties.getWechatAppSecret().isBlank();
    }

    private PeisCampusIdentityPort requireCampusPort() {
        PeisCampusIdentityPort port = peisCampusIdentityPortProvider.getIfAvailable();
        if (port == null) {
            throw new BusinessException("CAMPUS_DISABLED", "Campus 模块未启用，无法校园身份绑定");
        }
        return port;
    }

    private MiniappPractitionerLookupPort requirePractitionerPort() {
        MiniappPractitionerLookupPort port = miniappPractitionerLookupPortProvider.getIfAvailable();
        if (port == null) {
            throw new BusinessException("PRACTITIONER_LOOKUP_DISABLED", "医护查档未启用");
        }
        return port;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void assertCampusNameMatches(String archiveName, String inputName) {
        if (archiveName == null || inputName == null) {
            throw new BusinessException("CAMPUS_NAME_MISMATCH", "姓名与校园档案不一致");
        }
        if (!archiveName.trim().equals(inputName.trim())) {
            throw new BusinessException("CAMPUS_NAME_MISMATCH", "姓名与校园档案不一致");
        }
    }

    private static void assertPractitionerNameMatches(String archiveName, String inputName) {
        if (inputName == null || inputName.isBlank()) {
            return;
        }
        if (archiveName == null || !archiveName.trim().equals(inputName.trim())) {
            throw new BusinessException("PRACTITIONER_NAME_MISMATCH", "姓名与医护档案不一致");
        }
    }

    private String resolveAppId(String requestAppId) {
        if (requestAppId != null && !requestAppId.isBlank()) {
            return requestAppId.trim();
        }
        if (miniappProperties.getDefaultAppId() != null && !miniappProperties.getDefaultAppId().isBlank()) {
            return miniappProperties.getDefaultAppId().trim();
        }
        throw new ValidationException("登录失败：请配置 openhis.miniapp.default-app-id 或传入 appId");
    }

    private static MiniappContext requireContext() {
        MiniappContext ctx = MiniappContext.get();
        if (ctx == null || ctx.getAccountId() == null || ctx.getSessionId() == null) {
            throw new BusinessException("UNAUTHORIZED", "请先登录");
        }
        return ctx;
    }

    private static Long parseSessionId(String sessionId) {
        try {
            return Long.valueOf(sessionId);
        } catch (Exception ex) {
            throw new ValidationException("会话标识无效");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static MiniappLoginResponse toLoginResponse(LoginResult login) {
        MiniappLoginResponse response = new MiniappLoginResponse();
        response.setToken(login.getRawToken());
        response.setAccountId(login.getAccount().getId());
        response.setSessionId(String.valueOf(login.getSession().getId()));
        response.setPatientId(login.getSession().getCurrentPatientId());
        response.setPractitionerId(login.getSession().getCurrentPractitionerId());
        response.setBindRequired(login.isBindRequired());
        response.setOpenid(login.getAccount().getOpenid());
        response.setAppId(login.getAccount().getAppId());
        return response;
    }

    private static MiniappSessionResponse toSessionResponse(MiniappContext ctx, MapSession session) {
        MiniappSessionResponse response = new MiniappSessionResponse();
        response.setAccountId(ctx.getAccountId());
        response.setSessionId(String.valueOf(session.getId()));
        response.setPatientId(session.getCurrentPatientId());
        response.setPractitionerId(session.getCurrentPractitionerId());
        response.setOpenid(ctx.getOpenid());
        response.setAppId(ctx.getAppId());
        response.setBindRequired(session.getCurrentPatientId() == null);
        return response;
    }
}
