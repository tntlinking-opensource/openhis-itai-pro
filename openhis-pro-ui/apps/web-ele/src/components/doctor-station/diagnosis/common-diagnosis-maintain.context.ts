import type { InjectionKey, Ref } from 'vue';

/**
 * 医生站工具栏「维护个人/科室常用诊断」变更计数。
 * DiagnosisList 注入后 watch，用于刷新左侧常用快捷树。
 */
export const COMMON_DIAGNOSIS_MAINTAIN_VERSION_KEY: InjectionKey<Ref<number>> = Symbol(
  'COMMON_DIAGNOSIS_MAINTAIN_VERSION',
);
