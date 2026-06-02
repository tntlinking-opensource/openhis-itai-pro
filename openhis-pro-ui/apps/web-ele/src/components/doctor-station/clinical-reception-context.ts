import type { InjectionKey } from 'vue';

/**
 * 新增诊断 / 新增医嘱等临床写操作前调用：未接诊则写库接诊；已接诊则仅切到「在诊」并选中。
 * 返回 `true` 表示可继续后续操作；`false` 表示应中止（未选患者、接诊中、登录缺失、接口失败等）。
 */
export type EnsureClinicalReceptionFn = () => Promise<boolean>;

export const ENSURE_CLINICAL_RECEPTION_KEY: InjectionKey<EnsureClinicalReceptionFn> = Symbol(
  'doctorStationEnsureClinicalReception',
);
