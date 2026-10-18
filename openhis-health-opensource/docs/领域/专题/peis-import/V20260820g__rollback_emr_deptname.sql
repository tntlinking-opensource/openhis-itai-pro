-- 回滚：病历正文 deptName「中医内科」→「中医科」
-- 依赖 _bak_20260820g_emr_document_deptname

BEGIN;

DO $$
BEGIN
  IF to_regclass('public._bak_20260820g_emr_document_deptname') IS NULL THEN
    RAISE EXCEPTION '缺少 bak 表';
  END IF;
END $$;

UPDATE emr_document d
   SET content = b.content,
       updated_at = NOW()
  FROM _bak_20260820g_emr_document_deptname b
 WHERE d.id = b.id;

DO $$
DECLARE
  n integer;
BEGIN
  SELECT COUNT(*) INTO n
    FROM emr_document d
   WHERE d.id IN (SELECT id FROM _bak_20260820g_emr_document_deptname)
     AND CAST(d.content AS text) LIKE '%中医科%';
  IF n <> (SELECT COUNT(*) FROM _bak_20260820g_emr_document_deptname) THEN
    RAISE EXCEPTION '回滚后含中医科行数=% 与 bak 不一致', n;
  END IF;
END $$;

COMMIT;
