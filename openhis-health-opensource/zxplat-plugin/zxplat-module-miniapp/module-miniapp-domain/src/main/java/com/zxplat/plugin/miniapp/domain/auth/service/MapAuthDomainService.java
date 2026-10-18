/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.miniapp.domain.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.zxplat.plugin.miniapp.domain.patientbinding.entity.MapPatientBinding;
import com.zxplat.plugin.miniapp.domain.patientbinding.repository.MapPatientBindingRepository;
import com.zxplat.plugin.miniapp.domain.practitionerbinding.entity.MapPractitionerBinding;
import com.zxplat.plugin.miniapp.domain.practitionerbinding.repository.MapPractitionerBindingRepository;
import com.zxplat.plugin.miniapp.domain.session.entity.MapSession;
import com.zxplat.plugin.miniapp.domain.session.repository.MapSessionRepository;
import com.zxplat.plugin.miniapp.domain.wechataccount.entity.MapWechatAccount;
import com.zxplat.plugin.miniapp.domain.wechataccount.repository.MapWechatAccountRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.exception.ValidationException;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 微信登录 / 会话 / 绑定写侧（DM-MAP-AUTH）。
 */
@Service
@RequiredArgsConstructor
public class MapAuthDomainService {

    public static final String STATUS_ACTIVE = "active";
    public static final String CLIENT_MINIAPP = "miniapp";
    public static final String BIND_CHANNEL_MANUAL = "manual";

    private final MapWechatAccountRepository mapWechatAccountRepository;
    private final MapSessionRepository mapSessionRepository;
    private final MapPatientBindingRepository mapPatientBindingRepository;
    private final MapPractitionerBindingRepository mapPractitionerBindingRepository;

    @Transactional(rollbackFor = Exception.class)
    public LoginResult loginOrCreateSession(
            String appId, String openid, String unionId, int sessionTtlHours) {
        if (isBlank(appId) || isBlank(openid)) {
            throw new ValidationException("登录失败：appId 与 openid 不能为空");
        }
        Long tenantId = requireTenantId();
        MapWechatAccount account = mapWechatAccountRepository
            .findFirstByTenantIdAndAppIdAndOpenidAndIsDeletedFalse(tenantId, appId, openid)
            .orElseGet(() -> createAccount(tenantId, appId, openid, unionId));
        if (!STATUS_ACTIVE.equals(account.getStatusEnum())) {
            throw new BusinessException("ACCOUNT_DISABLED", "微信账号已禁用");
        }
        if (!isBlank(unionId) && isBlank(account.getUnionId())) {
            account.setUnionId(unionId.trim());
        }
        account.setLastLoginAt(LocalDateTime.now());
        mapWechatAccountRepository.save(account);

        String rawToken = generateRawToken();
        MapSession session = new MapSession();
        session.setTenantId(tenantId);
        session.setWechatAccountId(account.getId());
        session.setTokenHash(hashToken(rawToken));
        session.setClientTypeEnum(CLIENT_MINIAPP);
        session.setExpiresAt(LocalDateTime.now().plusHours(Math.max(1, sessionTtlHours)));
        List<MapPatientBinding> patientBindings =
            mapPatientBindingRepository.findByTenantIdAndWechatAccountIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, account.getId());
        Optional<MapPatientBinding> defaultPatient = patientBindings.stream()
            .filter(b -> Boolean.TRUE.equals(b.getIsDefault()))
            .findFirst();
        if (defaultPatient.isEmpty() && !patientBindings.isEmpty()) {
            defaultPatient = Optional.of(patientBindings.get(0));
        }
        defaultPatient.ifPresent(b -> session.setCurrentPatientId(b.getPatientId()));
        List<MapPractitionerBinding> practitionerBindings =
            mapPractitionerBindingRepository.findByTenantIdAndWechatAccountIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, account.getId());
        Optional<MapPractitionerBinding> defaultPractitioner = practitionerBindings.stream()
            .filter(b -> Boolean.TRUE.equals(b.getIsDefault()))
            .findFirst();
        if (defaultPractitioner.isEmpty() && !practitionerBindings.isEmpty()) {
            defaultPractitioner = Optional.of(practitionerBindings.get(0));
        }
        defaultPractitioner.ifPresent(b -> session.setCurrentPractitionerId(b.getPractitionerId()));
        MapSession saved = mapSessionRepository.save(session);

        LoginResult result = new LoginResult();
        result.account = account;
        result.session = saved;
        result.rawToken = rawToken;
        result.bindRequired = saved.getCurrentPatientId() == null;
        return result;
    }

    public Optional<ResolvedSession> resolveActiveSession(String rawToken) {
        if (isBlank(rawToken)) {
            return Optional.empty();
        }
        return mapSessionRepository
            .findFirstByTokenHashAndRevokedAtIsNullAndIsDeletedFalse(hashToken(rawToken.trim()))
            .filter(session -> session.getExpiresAt() != null
                && session.getExpiresAt().isAfter(LocalDateTime.now()))
            .flatMap(session -> mapWechatAccountRepository.findById(session.getWechatAccountId())
                .filter(acc -> Boolean.FALSE.equals(acc.getIsDeleted())
                    || acc.getIsDeleted() == null)
                .filter(acc -> STATUS_ACTIVE.equals(acc.getStatusEnum()))
                .map(acc -> new ResolvedSession(session, acc)));
    }

    @Transactional(rollbackFor = Exception.class)
    public MapSession revokeSession(Long sessionId) {
        MapSession session = requireSession(sessionId);
        session.setRevokedAt(LocalDateTime.now());
        return mapSessionRepository.save(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public MapSession switchPatient(Long sessionId, Long accountId, Long patientId) {
        if (patientId == null) {
            throw new ValidationException("切换就诊人失败：patientId 不能为空");
        }
        Long tenantId = requireTenantId();
        MapPatientBinding binding = mapPatientBindingRepository
            .findFirstByTenantIdAndWechatAccountIdAndPatientIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, accountId, patientId)
            .orElseThrow(() -> new BusinessException("BIND_REQUIRED", "请先绑定该就诊人"));
        MapSession session = requireSession(sessionId);
        if (!accountId.equals(session.getWechatAccountId())) {
            throw new BusinessException("SESSION_ACCOUNT_MISMATCH", "会话与账号不匹配");
        }
        session.setCurrentPatientId(binding.getPatientId());
        return mapSessionRepository.save(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public MapSession switchPractitioner(Long sessionId, Long accountId, Long practitionerId) {
        if (practitionerId == null) {
            throw new ValidationException("切换参与者失败：practitionerId 不能为空");
        }
        Long tenantId = requireTenantId();
        MapPractitionerBinding binding = mapPractitionerBindingRepository
            .findFirstByTenantIdAndWechatAccountIdAndPractitionerIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, accountId, practitionerId)
            .orElseThrow(() -> new BusinessException(
                "PRACTITIONER_BIND_REQUIRED", "请先绑定该医疗参与者"));
        MapSession session = requireSession(sessionId);
        if (!accountId.equals(session.getWechatAccountId())) {
            throw new BusinessException("SESSION_ACCOUNT_MISMATCH", "会话与账号不匹配");
        }
        session.setCurrentPractitionerId(binding.getPractitionerId());
        return mapSessionRepository.save(session);
    }

    @Transactional(rollbackFor = Exception.class)
    public MapPatientBinding bindPatient(
            Long accountId,
            Long patientId,
            String bindChannelEnum,
            String campusRefType,
            Long campusRefId,
            String campusBizNo,
            boolean asDefault) {
        if (patientId == null) {
            throw new ValidationException("绑定就诊人失败：patientId 不能为空");
        }
        Long tenantId = requireTenantId();
        Optional<MapPatientBinding> existing = mapPatientBindingRepository
            .findFirstByTenantIdAndWechatAccountIdAndPatientIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, accountId, patientId);
        if (existing.isPresent()) {
            MapPatientBinding hit = existing.get();
            if (asDefault) {
                clearDefaultPatient(tenantId, accountId);
                hit.setIsDefault(Boolean.TRUE);
                return mapPatientBindingRepository.save(hit);
            }
            return hit;
        }
        if (asDefault) {
            clearDefaultPatient(tenantId, accountId);
        }
        MapPatientBinding binding = new MapPatientBinding();
        binding.setTenantId(tenantId);
        binding.setWechatAccountId(accountId);
        binding.setPatientId(patientId);
        binding.setBindChannelEnum(isBlank(bindChannelEnum) ? BIND_CHANNEL_MANUAL : bindChannelEnum);
        binding.setCampusRefType(campusRefType);
        binding.setCampusRefId(campusRefId);
        binding.setCampusBizNo(campusBizNo);
        binding.setIsDefault(asDefault);
        binding.setBoundAt(LocalDateTime.now());
        return mapPatientBindingRepository.save(binding);
    }

    @Transactional(rollbackFor = Exception.class)
    public MapPractitionerBinding bindPractitioner(
            Long accountId, Long practitionerId, String bindChannelEnum, String employeeNo, boolean asDefault) {
        if (practitionerId == null) {
            throw new ValidationException("绑定参与者失败：practitionerId 不能为空");
        }
        Long tenantId = requireTenantId();
        Optional<MapPractitionerBinding> existing = mapPractitionerBindingRepository
            .findFirstByTenantIdAndWechatAccountIdAndPractitionerIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, accountId, practitionerId);
        if (existing.isPresent()) {
            MapPractitionerBinding hit = existing.get();
            if (asDefault) {
                clearDefaultPractitioner(tenantId, accountId);
                hit.setIsDefault(Boolean.TRUE);
                return mapPractitionerBindingRepository.save(hit);
            }
            return hit;
        }
        if (asDefault) {
            clearDefaultPractitioner(tenantId, accountId);
        }
        MapPractitionerBinding binding = new MapPractitionerBinding();
        binding.setTenantId(tenantId);
        binding.setWechatAccountId(accountId);
        binding.setPractitionerId(practitionerId);
        binding.setBindChannelEnum(isBlank(bindChannelEnum) ? BIND_CHANNEL_MANUAL : bindChannelEnum);
        binding.setEmployeeNo(employeeNo);
        binding.setIsDefault(asDefault);
        binding.setBoundAt(LocalDateTime.now());
        return mapPractitionerBindingRepository.save(binding);
    }

    private void clearDefaultPatient(Long tenantId, Long accountId) {
        List<MapPatientBinding> rows =
            mapPatientBindingRepository.findByTenantIdAndWechatAccountIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, accountId);
        for (MapPatientBinding row : rows) {
            if (Boolean.TRUE.equals(row.getIsDefault())) {
                row.setIsDefault(Boolean.FALSE);
                mapPatientBindingRepository.save(row);
            }
        }
    }

    private void clearDefaultPractitioner(Long tenantId, Long accountId) {
        List<MapPractitionerBinding> rows =
            mapPractitionerBindingRepository.findByTenantIdAndWechatAccountIdAndUnboundAtIsNullAndIsDeletedFalse(
                tenantId, accountId);
        for (MapPractitionerBinding row : rows) {
            if (Boolean.TRUE.equals(row.getIsDefault())) {
                row.setIsDefault(Boolean.FALSE);
                mapPractitionerBindingRepository.save(row);
            }
        }
    }

    private MapWechatAccount createAccount(Long tenantId, String appId, String openid, String unionId) {
        MapWechatAccount account = new MapWechatAccount();
        account.setTenantId(tenantId);
        account.setAppId(appId.trim());
        account.setOpenid(openid.trim());
        account.setUnionId(isBlank(unionId) ? null : unionId.trim());
        account.setStatusEnum(STATUS_ACTIVE);
        account.setLastLoginAt(LocalDateTime.now());
        return mapWechatAccountRepository.save(account);
    }

    private MapSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw new ValidationException("会话不能为空");
        }
        return mapSessionRepository.findById(sessionId)
            .filter(s -> !Boolean.TRUE.equals(s.getIsDeleted()))
            .orElseThrow(() -> new BusinessException("SESSION_NOT_FOUND", "会话不存在"));
    }

    private static Long requireTenantId() {
        Long tenantId = TenantUtils.getCurrentTenantId();
        if (tenantId == null) {
            throw new ValidationException("登录失败：租户上下文缺失");
        }
        return tenantId;
    }

    private static String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "")
            + UUID.randomUUID().toString().replace("-", "");
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Getter
    public static class LoginResult {
        private MapWechatAccount account;
        private MapSession session;
        private String rawToken;
        private boolean bindRequired;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ResolvedSession {
        private final MapSession session;
        private final MapWechatAccount account;
    }
}
