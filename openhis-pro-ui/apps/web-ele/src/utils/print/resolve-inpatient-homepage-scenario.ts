import { PRINT_SCENARIO, type PrintScenario } from './constants/print-scenario';

/**
 * 按医院机构类型开关选择病案首页打印场景。
 *
 * <p>与 loadPage {@code tcmInfoEnabled}（医院 {@code medins_type}）同口径：
 * 中医 → {@link PRINT_SCENARIO.ipHomepageTcmPrint}（{@code ip-homepage-tcm-print}）；
 * 西医 → {@link PRINT_SCENARIO.ipHomepageWmPrint}（{@code ip-homepage-wm-print}）。</p>
 *
 * @param tcmInfoEnabled 是否中医院首页（中医信息段开启）
 */
export function resolveInpatientHomepagePrintScenario(tcmInfoEnabled: boolean): PrintScenario {
  if (tcmInfoEnabled) {
    return PRINT_SCENARIO.ipHomepageTcmPrint;
  }
  return PRINT_SCENARIO.ipHomepageWmPrint;
}
