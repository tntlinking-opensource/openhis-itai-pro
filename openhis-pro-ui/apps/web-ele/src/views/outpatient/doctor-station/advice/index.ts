export { default as AdviceEditCard } from './components/advice-edit-card.vue';
export { default as AdviceOrderSelect } from './components/advice-order-select.vue';
export { calcAdviceTotalFee, useAdviceDrugList } from './composables/use-advice-drug-list';
export type { UseAdviceDrugListParams } from './composables/use-advice-drug-list';
export { default as AdviceList } from './index.vue';
export { formatMoneyStrYuan, formatUnitPriceYuan } from './services/advice-format';
export type {
  AdviceDrugItem,
  AdviceDrugPatchPayload,
  AdviceEditForm,
  AdviceProductOption,
  ProductColumn,
  ProductQueryParams,
} from './services/advice.types';
export { normalizeCatalogUnitBands } from './services/medication-unit-bands';
