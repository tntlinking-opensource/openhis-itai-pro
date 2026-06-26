/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.service;

import com.openhis.chs.domain.catalog.command.UpsertNationalCatalogRowCommand;
import com.openhis.chs.domain.catalog.repository.YbNationalCatalogItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YbNationalCatalogDomainServiceTest {

    @Mock
    private YbNationalCatalogItemRepository repository;

    @Mock
    private YbNationalCatalogBatchWriter batchWriter;

    @InjectMocks
    private YbNationalCatalogDomainService domainService;

    @Test
    void upsertFilePackageBatch_freshPackage_usesInsertOnly() {
        UpsertNationalCatalogRowCommand row = row("Y001", 1);
        when(batchWriter.writeFilePackageFreshBatch(eq(1L), eq(10L), eq(100L), eq("FQ001"), any()))
            .thenReturn(1);

        int count = domainService.upsertFilePackageBatch(1L, 10L, 100L, "FQ001", List.of(row), true);

        assertThat(count).isEqualTo(1);
        verify(batchWriter).writeFilePackageFreshBatch(1L, 10L, 100L, "FQ001", List.of(row));
        verify(batchWriter, never()).writeFilePackageUpsertBatch(any(), any(), any(), any(), any());
    }

    @Test
    void upsertFilePackageBatch_reimport_usesUpsert() {
        UpsertNationalCatalogRowCommand row = row("Y001", 1);
        when(batchWriter.writeFilePackageUpsertBatch(eq(1L), eq(10L), eq(100L), eq("FQ001"), any()))
            .thenReturn(1);

        int count = domainService.upsertFilePackageBatch(1L, 10L, 100L, "FQ001", List.of(row), false);

        assertThat(count).isEqualTo(1);
        verify(batchWriter).writeFilePackageUpsertBatch(1L, 10L, 100L, "FQ001", List.of(row));
    }

    @Test
    void existsFilePackage_delegatesToRepository() {
        when(repository.existsByContractIdAndListTypeAndSourceFileQuryNoAndTenantIdAndIsDeletedFalse(
            1L, "101", "FQ001", 10L)).thenReturn(true);

        assertThat(domainService.existsFilePackage(1L, 10L, "101", "FQ001")).isTrue();
    }

    private static UpsertNationalCatalogRowCommand row(String code, int lineNo) {
        UpsertNationalCatalogRowCommand cmd = new UpsertNationalCatalogRowCommand();
        cmd.setListType("101");
        cmd.setHilistCode(code);
        cmd.setSourceLineNo(lineNo);
        return cmd;
    }
}
