import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { useFromFinanceApproval } from '#/composables/use-from-finance-approval';
import {
  resolveFinanceApprovalViewSummaryItems,
  type FinanceApprovalViewSummaryItem,
} from '#/views/finance/approval/finance-approval-amount-columns';

/**
 * 财务审批「查看」深链：详情页脚合计（与外部列表金额列同口径，取自 query）。
 */
export function useFinanceApprovalViewSummary() {
  const route = useRoute();
  const { fromFinanceApproval } = useFromFinanceApproval();

  /** 是否展示页脚合计条 */
  const showFinanceApprovalSummary = computed(
    () => fromFinanceApproval.value && financeApprovalSummaryItems.value.length > 0,
  );

  /** 页脚合计项（无金额列值时为空） */
  const financeApprovalSummaryItems = computed((): FinanceApprovalViewSummaryItem[] => {
    if (!fromFinanceApproval.value) return [];
    return resolveFinanceApprovalViewSummaryItems(route.query as Record<string, unknown>);
  });

  return {
    fromFinanceApproval,
    showFinanceApprovalSummary,
    financeApprovalSummaryItems,
  };
}
