/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printtemplate.service;

import com.openhis.health.domain.print.printtemplate.repository.PrtTemplateRepository;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 打印模板业务键生成器：{@code PT} + {@code yyyyMMdd} + 4 位当日序号。
 */
@Service
@RequiredArgsConstructor
public class PrtBusNoGenerator {

  private static final String PREFIX = "PT";
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
  private static final int MAX_DAILY_SEQ = 9999;

  private final PrtTemplateRepository prtTemplateRepository;

  /**
   * 为租户生成下一个 busNo。
   *
   * @param tenantId 租户
   * @return 形如 PT202606260001
   */
  public String nextBusNo(Long tenantId) {
    if (tenantId == null) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "租户不能为空");
    }
    String datePart = LocalDate.now(ZONE).format(GeneralSharedConstants.DATE_COMPACT_FORMATTER);
    String dayPrefix = PREFIX + datePart;
    var latest =
        prtTemplateRepository.findFirstByTenantIdAndBusNoStartingWithAndIsDeletedFalseOrderByBusNoDesc(
            tenantId, dayPrefix);
    int nextSeq = 1;
    if (latest != null && latest.getBusNo() != null) {
      String suffix = latest.getBusNo().substring(dayPrefix.length());
      if (!suffix.isEmpty()) {
        try {
          nextSeq = Integer.parseInt(suffix) + 1;
        } catch (NumberFormatException ignored) {
          nextSeq = 1;
        }
      }
    }
    if (nextSeq > MAX_DAILY_SEQ) {
      throw new BusinessException(ErrorCodeConstants.PRT_INVALID, "当日模板编号已用尽");
    }
    return dayPrefix + String.format("%04d", nextSeq);
  }
}
