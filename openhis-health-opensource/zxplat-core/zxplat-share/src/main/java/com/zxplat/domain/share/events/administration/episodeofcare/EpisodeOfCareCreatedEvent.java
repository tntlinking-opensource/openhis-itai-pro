/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.episodeofcare;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * EpisodeOfCareCreatedEvent。
 */
public class EpisodeOfCareCreatedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;
    /**
     * busNo。
     */
    private String busNo;
    /**
     * organizationId。
     */
    private Long organizationId;
    /**
     * statusEnum。
     */
    private String statusEnum;

    public EpisodeOfCareCreatedEvent() { super(); }
    /**
     * 处理Long id, Long patientId, String busNo, Long organizationId, String statusEnum逻辑。
     * @return 返回结果
     */
    public EpisodeOfCareCreatedEvent(Long id, Long patientId, String busNo, Long organizationId, String statusEnum) {
        super(id, AggregateTypeConstants.EPISODE_OF_CARE);
        this.patientId = patientId;
        this.busNo = busNo;
        this.organizationId = organizationId;
        this.statusEnum = statusEnum;
    }
}
