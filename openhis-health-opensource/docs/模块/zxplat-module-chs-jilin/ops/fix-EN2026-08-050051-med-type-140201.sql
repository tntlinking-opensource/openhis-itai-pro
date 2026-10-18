-- =============================================================================
-- 运维脚本：王素会 EN2026-08-050051 结肠息肉 med_type 140104 → 140201
-- 库：whale-pro @ 172.16.101.2
-- 目的：明天诊间医保结账按「门诊特病」走，避免再报「未查询到门慢登记」
--
-- 【已核对现状 2026-08-05】
--   encounter_id = 343222932850348032
--   诊断行 id    = 343224466531160064  med_type=140104  病种 M10000
--   平台映射：
--     med_type=11     mdtrt=...122488  有效  ← 挂号费已 2207，禁止动
--     med_type=140104 mdtrt=...136687  有效  ← 当日失败预结算建档，需作废本地映射
--   yb_clinic_pre_settle / yb_settlement_record：仅有 med_type=11 已 SETTLED 挂号费
--   待收费 adm_charge_item：17 条 PLANNED（不存 med_type，结账时读诊断）
--   失败链路：2203/2204(140104) → 2206 失败 → 已 2205 撤明细；平台 140104 就诊可能仍在
--
-- 【本脚本会做】
--   1) 诊断 med_type 改为 140201
--   2) 软删该就诊下 med_type=140104 的 yb_encounter_platform_map（禁止复用错误 mdtrt）
--
-- 【本脚本不会做 / 禁止改】
--   - med_type=11 的平台映射、预结算、结算记录、费用上传（挂号已结）
--   - 收费行、药品医嘱（无 med_type 列，绑的是诊断 id）
--   - 平台侧 2202 撤销（SQL 做不到；见下方「平台收尾」）
--
-- 【明天结账预期】
--   勾选特病相关待收费 → 分组 med_type=140201 → 新建 2201 映射 → 2203/2204/2206
--   未绑诊断的耗材「—」会并入当次唯一 med_type（特病）
--   若同次勾到其它慢病诊断，仍会分次结算拦截
-- =============================================================================

BEGIN;

-- ---------- 0. 执行前核对（应各 1 行）----------
SELECT e.id AS encounter_id, e.bus_no, ed.id AS diagnosis_id, ed.med_type, cd.condition_code, cd.name
FROM adm_encounter e
JOIN adm_encounter_diagnosis ed ON ed.encounter_id = e.id AND ed.is_deleted IS NOT TRUE
LEFT JOIN cln_condition_definition cd ON cd.id = ed.condition_definition_id
WHERE e.bus_no = 'EN2026-08-050051'
  AND ed.id = 343224466531160064;

SELECT id, med_type, mdtrt_id, is_deleted, created_at
FROM yb_encounter_platform_map
WHERE encounter_id = 343222932850348032
ORDER BY id;

-- 若上面诊断 med_type 已不是 140104，请 STOP；不要重复执行 UPDATE。

-- ---------- 1. 诊断：门诊慢病 → 门诊特病 ----------
UPDATE adm_encounter_diagnosis
SET med_type = '140201',
    updated_at = NOW(),
    version = COALESCE(version, 0) + 1
WHERE id = 343224466531160064
  AND encounter_id = 343222932850348032
  AND med_type = '140104'
  AND is_deleted IS NOT TRUE;

-- 期望：UPDATE 1

-- ---------- 2. 软删错误医疗类别的平台就诊映射（勿改 med_type=11）----------
-- 说明：该 mdtrt 是按 140104 建的；改成 140201 也不能复用，必须让结账重新 2201。
UPDATE yb_encounter_platform_map
SET is_deleted = TRUE,
    deleted_at = NOW(),
    updated_at = NOW()
WHERE id = 343227435578298368
  AND encounter_id = 343222932850348032
  AND med_type = '140104'
  AND is_deleted IS NOT TRUE;

-- 期望：UPDATE 1

-- ---------- 3. 执行后核对 ----------
SELECT ed.id, ed.med_type, cd.condition_code, cd.name
FROM adm_encounter_diagnosis ed
LEFT JOIN cln_condition_definition cd ON cd.id = ed.condition_definition_id
WHERE ed.id = 343224466531160064;
-- 期望 med_type = 140201

SELECT id, med_type, mdtrt_id, is_deleted
FROM yb_encounter_platform_map
WHERE encounter_id = 343222932850348032
ORDER BY id;
-- 期望：11 仍 is_deleted=false；140104 为 true

-- 核对无误后提交；有误则 ROLLBACK
COMMIT;
-- ROLLBACK;

-- =============================================================================
-- 【平台收尾 | 建议明天结账前做，非本 SQL】
-- 失败预结算曾成功 2201(140104)，本地映射已软删，平台就诊可能仍在：
--   mdtrt_id = 220000202608051103180168136687
--   psn_no   = 22000011000015092093
-- 若院端有「撤销门诊挂号/2202」运维能力，可对该 mdtrt 做 2202，避免平台残留。
-- 不做 2202 一般不阻塞新建 140201 的 2201，但建议有空清掉。
--
-- 【结账前 UI 自检】
--   1. 医生站诊断：结肠息肉角标应为「特」
--   2. 收费：只勾特病相关（或特病+未绑诊断耗材），勿与其它慢病混勾
--   3. 预结算成功后再正式结算
-- =============================================================================
