-- yb_invoke_log：接口中文名 + 失败原因，便于联调排查

ALTER TABLE yb_invoke_log
    ADD COLUMN IF NOT EXISTS infname VARCHAR(128),
    ADD COLUMN IF NOT EXISTS err_msg VARCHAR(512);

COMMENT ON COLUMN yb_invoke_log.infname IS '交易编号对应中文名称（如 2401 入院办理）';
COMMENT ON COLUMN yb_invoke_log.err_msg IS '平台 err_msg 或调用异常摘要';
