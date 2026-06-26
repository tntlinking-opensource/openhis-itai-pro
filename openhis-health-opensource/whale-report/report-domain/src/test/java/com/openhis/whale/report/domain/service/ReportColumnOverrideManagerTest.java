package com.openhis.whale.report.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.whale.report.domain.entity.ReportColumnOverride;
import com.openhis.whale.report.domain.repository.ReportColumnOverrideRepository;
import com.openhis.whale.report.domain.support.ReportColumnOverrideItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportColumnOverrideManagerTest {

    @Mock
    private ReportColumnOverrideRepository repository;

    private ReportColumnOverrideManager manager;

    @BeforeEach
    void setUp() {
        manager = new ReportColumnOverrideManager(repository, new ObjectMapper());
    }

    @Test
    void applyOverridesPrefersUserOverOrganization() {
        List<ReportColumnOverrideManager.ColumnView> defaults = List.of(
                new ReportColumnOverrideManager.ColumnView("a", "A", "left", 100),
                new ReportColumnOverrideManager.ColumnView("b", "B", "left", 100),
                new ReportColumnOverrideManager.ColumnView("c", "C", "left", 100));

        ReportColumnOverride userRow = new ReportColumnOverride();
        userRow.setColumnsJson("[{\"field\":\"c\",\"visible\":true,\"sortOrder\":0},{\"field\":\"a\",\"visible\":true,\"sortOrder\":1}]");

        when(repository.findFirstByTenantIdAndReportCodeAndUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(1L, "demo", 9L))
                .thenReturn(Optional.of(userRow));

        List<ReportColumnOverrideManager.ColumnView> merged =
                manager.applyOverrides(defaults, 1L, "demo", 9L, 2L);

        assertEquals(List.of("c", "a", "b"), merged.stream().map(ReportColumnOverrideManager.ColumnView::field).toList());
    }

    @Test
    void applyOverridesUsesOrganizationWhenUserMissing() {
        List<ReportColumnOverrideManager.ColumnView> defaults = List.of(
                new ReportColumnOverrideManager.ColumnView("a", "A", "left", 100),
                new ReportColumnOverrideManager.ColumnView("b", "B", "left", 100),
                new ReportColumnOverrideManager.ColumnView("c", "C", "left", 100));

        ReportColumnOverride orgRow = new ReportColumnOverride();
        orgRow.setColumnsJson("[{\"field\":\"b\",\"visible\":false,\"sortOrder\":0},{\"field\":\"a\",\"visible\":true,\"sortOrder\":1}]");

        when(repository.findFirstByTenantIdAndReportCodeAndUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(1L, "demo", 9L))
                .thenReturn(Optional.empty());
        when(repository.findFirstByTenantIdAndReportCodeAndOrganizationIdAndUserIdIsNullAndIsDeletedFalseOrderByUpdatedAtDesc(
                        1L, "demo", 2L))
                .thenReturn(Optional.of(orgRow));

        List<ReportColumnOverrideManager.ColumnView> merged =
                manager.applyOverrides(defaults, 1L, "demo", 9L, 2L);

        assertEquals(List.of("a", "c"), merged.stream().map(ReportColumnOverrideManager.ColumnView::field).toList());
    }

    @Test
    void resolveColumnsForSettingsKeepsHiddenColumns() {
        List<ReportColumnOverrideManager.ColumnView> defaults = List.of(
                new ReportColumnOverrideManager.ColumnView("a", "A", "left", 100),
                new ReportColumnOverrideManager.ColumnView("b", "B", "left", 100));

        ReportColumnOverride userRow = new ReportColumnOverride();
        userRow.setColumnsJson("[{\"field\":\"b\",\"visible\":false,\"sortOrder\":0},{\"field\":\"a\",\"visible\":true,\"sortOrder\":1}]");
        when(repository.findFirstByTenantIdAndReportCodeAndUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(1L, "demo", 9L))
                .thenReturn(Optional.of(userRow));

        List<ReportColumnOverrideManager.ColumnSettingView> settings =
                manager.resolveColumnsForSettings(defaults, 1L, "demo", 9L, null);

        assertEquals(2, settings.size());
        assertEquals("b", settings.get(0).field());
        assertFalse(settings.get(0).visible());
        assertEquals("a", settings.get(1).field());
        assertTrue(settings.get(1).visible());
    }

    @Test
    void saveUserOverridePersistsColumnsJson() {
        ReportColumnOverride saved = new ReportColumnOverride();
        when(repository.findFirstByTenantIdAndReportCodeAndUserIdAndIsDeletedFalseOrderByUpdatedAtDesc(1L, "demo", 9L))
                .thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(saved);

        List<ReportColumnOverrideItem> items = List.of(
                item("a", true, 0),
                item("b", false, 1));
        manager.saveUserOverride(1L, "demo", 9L, items);

        ArgumentCaptor<ReportColumnOverride> captor = ArgumentCaptor.forClass(ReportColumnOverride.class);
        verify(repository).save(captor.capture());
        assertTrue(captor.getValue().getColumnsJson().contains("\"field\":\"b\""));
    }

    private static ReportColumnOverrideItem item(String field, boolean visible, int sortOrder) {
        ReportColumnOverrideItem item = new ReportColumnOverrideItem();
        item.setField(field);
        item.setVisible(visible);
        item.setSortOrder(sortOrder);
        return item;
    }
}
