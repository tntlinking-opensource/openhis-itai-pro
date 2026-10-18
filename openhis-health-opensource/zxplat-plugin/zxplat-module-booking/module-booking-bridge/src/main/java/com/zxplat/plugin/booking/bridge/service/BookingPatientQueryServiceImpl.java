/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.bridge.service;

import com.zxplat.domain.administration.patient.queries.PatientPagedQuery;
import com.zxplat.domain.administration.patient.queries.model.PatientPageItemQueryModel;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.plugin.booking.domain.masterdata.result.BookingPatientItem;
import com.zxplat.plugin.booking.domain.masterdata.result.BookingPatientPageResult;
import com.zxplat.plugin.booking.domain.masterdata.service.BookingPatientQueryService;
import com.zxplat.plugin.booking.domain.masterdata.queries.BookingPatientSearchQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookingPatientQueryServiceImpl implements BookingPatientQueryService {

    private final PatientQueryService patientQueryService;

    @Override
    public BookingPatientPageResult pagePatientsForSelect(BookingPatientSearchQuery query) {
        PatientPagedQuery patientQuery = new PatientPagedQuery();
        if (query != null) {
            patientQuery.setKeyword(query.getKeyword());
            patientQuery.setPageNo(query.getPageNo());
            patientQuery.setPageSize(query.getPageSize());
        }
        Page<PatientPageItemQueryModel> page = patientQueryService.searchPatientsPaged(patientQuery);

        BookingPatientPageResult result = new BookingPatientPageResult();
        List<BookingPatientItem> items = new ArrayList<>();
        for (PatientPageItemQueryModel row : page.getContent()) {
            if (row == null || row.getId() == null) {
                continue;
            }
            BookingPatientItem item = new BookingPatientItem();
            item.setPatientId(row.getId());
            item.setPatientBusNo(row.getBusNo());
            item.setName(row.getName());
            item.setGenderCode(row.getGenderEnum());
            item.setBirthDate(row.getBirthDate());
            if (row.getIdCard() != null && row.getIdCard().length() > 4) {
                item.setIdCardNoMasked(row.getIdCard().substring(0, 2) + "****" + row.getIdCard().substring(row.getIdCard().length() - 2));
            }
            if (row.getPhone() != null && row.getPhone().length() > 4) {
                item.setPhoneMasked(row.getPhone().substring(0, 3) + "****" + row.getPhone().substring(row.getPhone().length() - 2));
            }
            items.add(item);
        }
        result.setContent(items);
        result.setTotalCount(page.getTotalElements());
        result.setPageNo(page.getNumber() + 1);
        result.setPageSize(page.getSize());
        return result;
    }

    @Override
    public Map<Long, String> findPatientNamesByIds(List<Long> patientIds) {
        return patientQueryService.findPatientNamesByIds(patientIds);
    }
}
