/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.patientstaff.service;

import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.plugin.campus.domain.campusdepartment.repository.CampusDepartmentRepository;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.patientstaff.entity.PatientStaff;
import com.zxplat.plugin.campus.domain.patientstaff.repository.PatientStaffRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * PatientStaffDomainService 关键：创建须绑定部门与校区。
 */
@ExtendWith(MockitoExtension.class)
class PatientStaffDomainServiceTest {

    @Mock
    private PatientStaffRepository repository;
    @Mock
    private CampusDepartmentRepository departmentRepository;
    @Mock
    private CampusSiteRepository campusSiteRepository;

    @InjectMocks
    private PatientStaffDomainService patientStaffDomainService;

    @Test
    void create_rejectsMissingDepartment() {
        PatientStaff staff = new PatientStaff();
        staff.setTenantId(1L);
        staff.setName("张三");
        staff.setEmployeeNo("E001");
        staff.setCampusSiteId(10L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientStaffDomainService.create(staff, 99L));
        assertEquals("DEPT_NOT_FOUND", ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    void create_rejectsMissingCampusSite() {
        PatientStaff staff = new PatientStaff();
        staff.setTenantId(1L);
        staff.setName("李四");
        staff.setEmployeeNo("E002");
        staff.setDepartmentId(20L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> patientStaffDomainService.create(staff, 99L));
        assertEquals("CAMPUS_NOT_FOUND", ex.getErrorCode());
        verify(repository, never()).save(any());
    }
}
