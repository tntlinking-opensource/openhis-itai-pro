import { computed } from 'vue';
import { useRoute } from 'vue-router';

/**
 * 是否从「财务审批」页「查看」深链进入（query.fromFinanceApproval=1|true）。
 * 财务审批族单据进入业务工作站后：只读 + 仅审驳。
 */
export function useFromFinanceApproval() {
  const route = useRoute();

  const fromFinanceApproval = computed(() => {
    const raw = route.query.fromFinanceApproval;
    if (Array.isArray(raw)) {
      return raw[0] === '1' || raw[0] === 'true';
    }
    return raw === '1' || raw === 'true';
  });

  return { fromFinanceApproval };
}
