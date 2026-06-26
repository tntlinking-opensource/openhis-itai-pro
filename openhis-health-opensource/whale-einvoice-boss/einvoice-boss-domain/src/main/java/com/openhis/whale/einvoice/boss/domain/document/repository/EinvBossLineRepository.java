package com.openhis.whale.einvoice.boss.domain.document.repository;

import com.openhis.whale.einvoice.boss.domain.document.entity.EinvBossLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 博思报文明细行仓储。
 */
public interface EinvBossLineRepository extends JpaRepository<EinvBossLine, Long> {

    /**
     * 按单据主键查询未删除明细行，按行号升序。
     *
     * @param documentId 单据主键
     * @return 明细行列表
     */
    List<EinvBossLine> findByDocumentIdAndIsDeletedFalseOrderByLineNoAsc(Long documentId);
}
