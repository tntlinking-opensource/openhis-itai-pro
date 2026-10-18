-- 补救：物理删除 3 个因 CSV 字段内换行被拆脏行的通知文件（含此前软删残留）
-- 执行后：重启已修解析器的后端 → 重新扫描 → 对这 3 个包再入库
-- DOC IDs：
--   335582622496329728  附件1：F服务目录29-33号文数据更新目录
--   335582887035277312  附件：F服务目录数据更新目录（2026.3.24）
--   335583304825704448  附件：F服务目录数据更新目录（2026.3.24） (1)

BEGIN;

DELETE FROM yb_catalog_notice_line
WHERE doc_id IN (
        335582622496329728,
        335582887035277312,
        335583304825704448
    );

DELETE FROM yb_catalog_notice_apply_log
WHERE doc_id IN (
        335582622496329728,
        335582887035277312,
        335583304825704448
    );

DELETE FROM yb_catalog_notice_todo
WHERE doc_id IN (
        335582622496329728,
        335582887035277312,
        335583304825704448
    );

DELETE FROM yb_catalog_notice_doc
WHERE id IN (
        335582622496329728,
        335582887035277312,
        335583304825704448
    );

COMMIT;

-- 校验：应均为 0
-- SELECT COUNT(*) FROM yb_catalog_notice_doc WHERE id IN (335582622496329728,335582887035277312,335583304825704448);
-- SELECT COUNT(*) FROM yb_catalog_notice_line WHERE doc_id IN (335582622496329728,335582887035277312,335583304825704448);
