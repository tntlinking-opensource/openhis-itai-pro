-- cln_diagnostic_report 打印次数（AS-PEIS-REP-001 REP-03）

ALTER TABLE "public"."cln_diagnostic_report"
    ADD COLUMN IF NOT EXISTS "print_count" int4;

COMMENT ON COLUMN cln_diagnostic_report.print_count IS '打印次数';
