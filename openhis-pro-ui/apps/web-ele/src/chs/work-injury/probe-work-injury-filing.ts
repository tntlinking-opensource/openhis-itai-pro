/**
 * 确认挂号前：无 encounter 时经 Win 直查工伤 1101→9102（仅预览，不写 anchor）。
 */
import type { WorkInjuryWinInvokeResponse } from './work-injury.types';

import { invokeWorkInjuryPlugin, isWorkInjuryPluginOnline } from './work-injury-plugin';
import { httpWorkInjuryModuleCapability } from './work-injury.http';

export interface WorkInjuryFilingProbeDiagnosis {
  injureAffirmId: string;
  qualificationId?: string;
  injuredConclusion?: string;
  injuredPart?: string;
}

export interface WorkInjuryFilingProbeRow {
  qualificationId: string;
  label: string;
  approveType?: string;
  startDate?: string;
  endDate?: string;
  conclusion?: string;
  trtChkRslt?: string;
  terminReason?: string;
}

export interface WorkInjuryFilingProbeResult {
  psnNo: string;
  psnName?: string;
  certno?: string;
  /** 性别：平台 gend 1男/2女，已映射为 MALE/FEMALE */
  genderCode?: 'FEMALE' | 'MALE';
  /** 出生日期 YYYY-MM-DD */
  birthDate?: string;
  /** 年龄（字符串，与挂号表单一致） */
  age?: string;
  /** 联系电话（1101 若返回 tel/phone） */
  phone?: string;
  injureAffirmId?: string;
  /** 1101 diagnosis.qualification_id（若有） */
  qualificationIdFrom1101?: string;
  diagnoses: WorkInjuryFilingProbeDiagnosis[];
  rows: WorkInjuryFilingProbeRow[];
  /** 有至少一条审批行（9102 referral_id / qualification_id） */
  hasValidFiling: boolean;
  queriedAt: string;
}

function formatDay(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}${m}${day}`;
}

function asRecord(value: unknown): null | Record<string, unknown> {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return null;
}

function pickString(...values: unknown[]): string {
  for (const value of values) {
    if (value == null) {
      continue;
    }
    const text = String(value).trim();
    if (text && text !== 'null' && text !== 'undefined') {
      return text;
    }
  }
  return '';
}

/** Win output 可能是对象、数组，或旧版解析失败的 { raw: 整包JSON }。 */
export function normalizeWorkInjuryWinOutput(output: unknown): Record<string, unknown> {
  if (Array.isArray(output)) {
    return { output };
  }
  const root = asRecord(output);
  if (!root) {
    return {};
  }
  if (typeof root.raw === 'string' && root.raw.trim()) {
    try {
      const envelope = JSON.parse(root.raw) as unknown;
      const env = asRecord(envelope);
      if (env && 'output' in env) {
        const nested = env.output;
        if (Array.isArray(nested)) {
          return { output: nested };
        }
        const nestedObj = asRecord(nested);
        if (nestedObj) {
          return nestedObj;
        }
      }
    } catch {
      // keep root
    }
  }
  return root;
}

function formatBirthday(value: unknown): string {
  const digits = String(value ?? '').replaceAll(/\D/g, '');
  if (digits.length !== 8) {
    return '';
  }
  return `${digits.slice(0, 4)}-${digits.slice(4, 6)}-${digits.slice(6, 8)}`;
}

/** 工伤 gend：1男 2女 → HIS MALE/FEMALE */
function mapWiGendToGenderCode(gend: unknown): 'FEMALE' | 'MALE' | undefined {
  const code = String(gend ?? '').trim();
  if (code === '1' || code === '男' || code.toUpperCase() === 'MALE') {
    return 'MALE';
  }
  if (code === '2' || code === '女' || code.toUpperCase() === 'FEMALE') {
    return 'FEMALE';
  }
  return undefined;
}

function parse1101Person(output: unknown): {
  psnNo: string;
  psnName?: string;
  certno?: string;
  genderCode?: 'FEMALE' | 'MALE';
  birthDate?: string;
  age?: string;
  phone?: string;
  diagnoses: WorkInjuryFilingProbeDiagnosis[];
  injureAffirmId?: string;
  qualificationIdFrom1101?: string;
} {
  const nested = normalizeWorkInjuryWinOutput(output);
  const body = asRecord(nested.output) ?? nested;
  const psnNo = pickString(body.psn_no, body.psnNo);
  const psnName = pickString(body.psn_name, body.psnName) || undefined;
  const certno = pickString(body.certno, body.certNo) || undefined;
  const genderCode = mapWiGendToGenderCode(body.gend ?? body.gender ?? body.sex);
  const birthDate =
    formatBirthday(body.birthday ?? body.brdy ?? body.birthDate ?? body.birth_date) || undefined;
  const ageRaw = pickString(body.age, body.psn_age, body.psnAge);
  const age = ageRaw ? String(Math.trunc(Number(ageRaw)) || ageRaw) : undefined;
  const phone =
    pickString(body.tel, body.phone, body.mobile, body.mob, body.psn_tel, body.psnTel) || undefined;
  const diagnoses: WorkInjuryFilingProbeDiagnosis[] = [];
  const rawDiag = body.diagnosis;
  if (Array.isArray(rawDiag)) {
    for (const item of rawDiag) {
      const row = asRecord(item);
      if (!row) {
        continue;
      }
      const injureAffirmId = pickString(
        row.injureAffirmID,
        row.injureAffirmId,
        row.injure_affirm_id,
      );
      if (!injureAffirmId || injureAffirmId === '0') {
        continue;
      }
      const qualificationId = pickString(row.qualification_id, row.qualificationId) || undefined;
      diagnoses.push({
        injureAffirmId,
        qualificationId: qualificationId && qualificationId !== '0' ? qualificationId : undefined,
        injuredConclusion:
          pickString(
            row.injured_conclusion,
            row.injuredConclusion,
            row.injured_injured_conclusion,
          ) || undefined,
        injuredPart:
          pickString(
            row.injured_part,
            row.injuredPart,
            row.injured_sites,
            row.injured_part_detail,
            row.injured_site_details,
          ) || undefined,
      });
    }
  }
  return {
    psnNo,
    psnName,
    certno,
    genderCode,
    birthDate,
    age,
    phone,
    diagnoses,
    injureAffirmId: diagnoses[0]?.injureAffirmId,
    qualificationIdFrom1101: diagnoses[0]?.qualificationId,
  };
}

function extract9102List(output: unknown): unknown[] {
  const root = normalizeWorkInjuryWinOutput(output);
  if (Array.isArray(root.output)) {
    return root.output;
  }
  for (const key of ['approveinfo', 'rows', 'list', 'data', 'result'] as const) {
    const candidate = root[key];
    if (Array.isArray(candidate)) {
      return candidate;
    }
  }
  return [];
}

function parse9102Rows(output: unknown): WorkInjuryFilingProbeRow[] {
  const rawList = extract9102List(output);
  const enriched: WorkInjuryFilingProbeRow[] = [];
  for (const item of rawList) {
    const row = asRecord(item);
    if (!row) {
      continue;
    }
    // 吉林工伤 9102 主键多为 referral_id，手册字段为 qualification_id
    const qualificationId = pickString(
      row.qualification_id,
      row.qualificationId,
      row.referral_id,
      row.referralId,
    );
    if (!qualificationId || qualificationId === '0') {
      continue;
    }
    const approveType = pickString(row.approveType, row.approve_type) || undefined;
    const startDate = pickString(row.startDate, row.start_date) || undefined;
    const endDate = pickString(row.endDate, row.end_date) || undefined;
    const terminReason = pickString(row.terminReason, row.termin_reason) || undefined;
    const conclusion =
      pickString(
        row.injured_injured_conclusion,
        row.injured_conclusion,
        row.injuredConclusion,
        terminReason,
      ) || undefined;
    const trtChkRslt = pickString(row.trt_chk_rslt, row.trtChkRslt) || undefined;
    enriched.push({
      qualificationId,
      label: conclusion || approveType || qualificationId,
      approveType,
      startDate,
      endDate,
      conclusion,
      trtChkRslt,
      terminReason,
    });
  }
  return enriched;
}

async function invokeInfno(
  infno: string,
  input: Record<string, unknown>,
  fixmedinsCode?: string,
): Promise<WorkInjuryWinInvokeResponse> {
  return invokeWorkInjuryPlugin({
    stepToken: `probe-${infno}-${Date.now()}`,
    infno,
    fixmedinsCode,
    input,
  });
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function isRetryableProbeError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return /异常错误|未知错误|读卡忙冲突|外部组件|External component|Si_Busi/i.test(message);
}

/** 模块内串行：避免待遇区连点 / 读卡并发打爆 JLSiInterface。 */
let probeChain: Promise<unknown> = Promise.resolve();

async function invoke1101WithRetry(fixmedinsCode?: string): Promise<WorkInjuryWinInvokeResponse> {
  const maxAttempts = 3;
  let lastError: unknown;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      return await invokeInfno('1101', {}, fixmedinsCode);
    } catch (error) {
      lastError = error;
      if (attempt >= maxAttempts || !isRetryableProbeError(error)) {
        throw error;
      }
      await sleep(600 * attempt);
    }
  }
  throw lastError instanceof Error ? lastError : new Error('工伤 1101 失败');
}

/**
 * Win 直查：1101（读卡）→ 9102（审批列表）。不写 Whale anchor。
 * 读卡器上需有社保卡（与 NEUSI idfi_mode=1 一致）。
 */
export async function probeWorkInjuryFilingViaWin(): Promise<WorkInjuryFilingProbeResult> {
  const run = async (): Promise<WorkInjuryFilingProbeResult> => {
    if (!(await isWorkInjuryPluginOnline())) {
      throw new Error('请启动 OpenHIS.Win 并启用工伤模块（/api/work-injury/health）');
    }
    let fixmedinsCode: string | undefined;
    try {
      const cap = await httpWorkInjuryModuleCapability();
      fixmedinsCode = cap?.fixmedinsCode?.trim() || undefined;
    } catch {
      // capability 失败不阻断，Win 用本地配置定点码
    }

    const res1101 = await invoke1101WithRetry(fixmedinsCode);
    const person = parse1101Person(res1101.output);
    if (!person.psnNo) {
      throw new Error('工伤 1101 未返回 psn_no，请确认已放卡且专线正常');
    }

    const end = new Date();
    const start = new Date();
    // 院点审批跨度常超 30 天；预览查近 3 年
    start.setFullYear(start.getFullYear() - 3);
    const res9102 = await invokeInfno(
      '9102',
      {
        psn_no: person.psnNo,
        startTime: formatDay(start),
        endTime: formatDay(end),
      },
      fixmedinsCode,
    );
    const rows = parse9102Rows(res9102.output);

    return {
      psnNo: person.psnNo,
      psnName: person.psnName,
      certno: person.certno,
      genderCode: person.genderCode,
      birthDate: person.birthDate,
      age: person.age,
      phone: person.phone,
      injureAffirmId: person.injureAffirmId,
      qualificationIdFrom1101: person.qualificationIdFrom1101,
      diagnoses: person.diagnoses,
      rows,
      hasValidFiling: rows.length > 0,
      queriedAt: new Date().toISOString(),
    };
  };

  const next = probeChain.then(run, run);
  probeChain = next.then(
    () => undefined,
    () => undefined,
  );
  return next;
}
