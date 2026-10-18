/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.episodeofcare.service;

import com.zxplat.domain.administration.episodeofcare.entity.EpisodeOfCare;
import com.zxplat.domain.administration.episodeofcare.query.EpisodeOfCareQueryCommand;
import com.zxplat.domain.administration.episodeofcare.repository.EpisodeOfCareRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * 就诊节（Episode of Care）读侧查询服务。
 *
 * <p>职责：按主键、业务单号、患者、机构等维度加载就诊节主档，供病程串联与就诊列表使用。</p>
 *
 * <p>边界：只读；无记录时返回 {@code null} 或空列表，由调用方决定提示策略；活动状态过滤使用持久化状态码 {@code "2"}（与 EpisodeOfCare 状态枚举约定一致）。</p>
 *
 * <p>聚合边界：仅本聚合 {@link EpisodeOfCareRepository}。</p>
 */
@Service
@RequiredArgsConstructor
public class EpisodeOfCareQueryService {
    private final EpisodeOfCareRepository repository;

    /**
     * 按主键加载就诊节主档。
     *
     * @param cmd 查询命令（{@code id} 必填）
     * @return 就诊节实体；不存在时 {@code null}
     */
    public EpisodeOfCare loadEpisodeOfCareById(EpisodeOfCareQueryCommand cmd) {
        // 单条主档读取：无则 null，避免读侧强抛阻断列表类场景。
        return repository.findById(cmd.getId()).orElse(null);
    }

    /**
     * 按业务单号加载就诊节（取首条匹配）。
     *
     * @param cmd 查询命令（{@code busNo} 必填）
     * @return 就诊节实体；无匹配时 {@code null}
     */
    public EpisodeOfCare loadEpisodeOfCareByBusNo(EpisodeOfCareQueryCommand cmd) {
        return repository.findFirstByBusNo(cmd.getBusNo()).orElse(null);
    }

    /**
     * 列出患者名下全部就诊节。
     *
     * @param cmd 查询命令（{@code patientId} 必填）
     * @return 就诊节列表，可能为空
     */
    public List<EpisodeOfCare> listEpisodeOfCareByPatient(EpisodeOfCareQueryCommand cmd) {
        return repository.findAllByPatientId(cmd.getPatientId());
    }

    /**
     * 列出机构维度下的就诊节。
     *
     * @param cmd 查询命令（{@code organizationId} 必填）
     * @return 就诊节列表，可能为空
     */
    public List<EpisodeOfCare> listEpisodeOfCareByOrganization(EpisodeOfCareQueryCommand cmd) {
        return repository.findAllByOrganizationId(cmd.getOrganizationId());
    }

    /**
     * 列出患者当前仍处于活动状态的就诊节。
     *
     * @param cmd 查询命令（{@code patientId} 必填）
     * @return 活动就诊节列表；状态码 {@code "2"} 表示活动态（与仓储派生方法约定一致）
     */
    public List<EpisodeOfCare> listActiveEpisodeOfCareByPatient(EpisodeOfCareQueryCommand cmd) {
        // 活动态过滤下推到仓储，避免读全量后在内存筛状态。
        return repository.findAllByPatientIdAndStatusEnum(cmd.getPatientId(), "2");
    }
}
