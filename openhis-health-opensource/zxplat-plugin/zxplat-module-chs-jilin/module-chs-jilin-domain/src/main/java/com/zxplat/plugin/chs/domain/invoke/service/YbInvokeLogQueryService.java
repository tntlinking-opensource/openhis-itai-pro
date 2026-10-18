/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.invoke.service;

import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;

import com.zxplat.plugin.chs.domain.invoke.entity.YbInvokeLog;
import com.zxplat.plugin.chs.domain.invoke.repository.YbInvokeLogRepository;
import com.zxplat.plugin.chs.domain.share.constants.ChsInfNo;
import com.zxplat.plugin.chs.domain.share.constants.ChsPlatformCodes;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** {@code yb_invoke_log} 读服务 */
@Service
@RequiredArgsConstructor
public class YbInvokeLogQueryService {

    private final YbInvokeLogRepository invokeLogRepository;

    /**
     * 按主键查询未删除 invoke 日志。
     *
     * @param id invoke 日志主键
     * @return 未删除的行；不存在时 empty
     */
    public Optional<YbInvokeLog> findOptionalById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return invokeLogRepository.findById(id)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 按支付单查询 invoke 日志列表。
     *
     * @param paymentDraftId 支付草稿/支付单 ID
     * @param tenantId       租户 ID
     * @return 按创建时间倒序的 invoke 列表
     */
    public List<YbInvokeLog> listByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        if (paymentDraftId == null || tenantId == null) {
            return Collections.emptyList();
        }
        return invokeLogRepository.findAllByPaymentDraftIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
            paymentDraftId, tenantId);
    }

    /**
     * 从同支付单 invoke 日志中取首个非空 contractBusNo（2601 冲正路由兜底）。
     *
     * @param paymentDraftId 支付单 ID
     * @param tenantId 租户 ID
     * @return 首个 contractBusNo；均无则 empty */
    public Optional<String> findFirstContractBusNoByPaymentDraftId(Long paymentDraftId, Long tenantId) {
        for (YbInvokeLog row : listByPaymentDraftId(paymentDraftId, tenantId)) {
            if (row != null && isNotEmpty(row.getContractBusNo())) {
                return Optional.of(row.getContractBusNo().trim());
            }
        }
        return Optional.empty();
    }

    /**
     * 从同结算回执 invoke 日志中取首个非空 contractBusNo。
     *
     * @param settlementRecordId 结算回执 ID
     * @param tenantId 租户 ID
     * @return 首个 contractBusNo；均无则 empty */
    public Optional<String> findFirstContractBusNoBySettlementRecordId(Long settlementRecordId, Long tenantId) {
        if (settlementRecordId == null || tenantId == null) {
            return Optional.empty();
        }
        List<YbInvokeLog> rows = invokeLogRepository
            .findAllBySettlementRecordIdAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                settlementRecordId, tenantId);
        for (YbInvokeLog row : rows) {
            if (row != null && isNotEmpty(row.getContractBusNo())) {
                return Optional.of(row.getContractBusNo().trim());
            }
        }
        return Optional.empty();
    }

    /**
     * 按就诊与交易号列出调用日志（倒序）。
     *
     * <p>仅本聚合 {@code yb_invoke_log}；供预结算前回收「平台 2204 成功、本地无 ACTIVE」悬挂批次。</p>
     *
     * @param encounterId 就诊 ID
     * @param infno       交易号
     * @param tenantId    租户 ID
     * @return 调用日志；参数不全时 empty list
     */
    public List<YbInvokeLog> listByEncounterIdAndInfno(Long encounterId, String infno, Long tenantId) {
        if (encounterId == null || tenantId == null || !isNotEmpty(infno)) {
            return Collections.emptyList();
        }
        return invokeLogRepository
            .findAllByEncounterIdAndInfnoAndTenantIdAndIsDeletedFalseOrderByCreatedAtDesc(
                encounterId, infno.trim(), tenantId);
    }

    /**
     * 按住院号在 2401 成功报文中查找调用日志。
     *
     * <p>入院登记日志的 {@code encounter_id} 可能为空，报文 {@code mdtrtinfo.ipt_no} 仍是住院号。
     * 只取最近 5 条，调用方再按住院号精确核对。</p>
     *
     * @param iptNo    住院号
     * @param tenantId 租户主键
     * @return 最近的成功 2401；参数不全时空列表
     */
    public List<YbInvokeLog> listSuccessfulAdmissionsContainingIptNo(String iptNo, Long tenantId) {
        if (tenantId == null || !isNotEmpty(iptNo)) {
            return Collections.emptyList();
        }
        String trimmed = iptNo.trim();
        if (trimmed.indexOf('%') >= 0 || trimmed.indexOf('_') >= 0) {
            return Collections.emptyList();
        }
        Specification<YbInvokeLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            predicates.add(cb.isFalse(root.get("isDeleted")));
            predicates.add(cb.equal(root.get("infno"), ChsInfNo.INPATIENT_ADMISSION));
            predicates.add(cb.equal(root.get("infcode"), ChsPlatformCodes.INFCODE_SUCCESS));
            predicates.add(cb.like(root.get("requestJson"), "%" + trimmed + "%"));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return invokeLogRepository.findAll(
            spec,
            PageRequest.of(0, 5, Sort.by(Sort.Order.desc("createdAt")))).getContent();
    }
}
