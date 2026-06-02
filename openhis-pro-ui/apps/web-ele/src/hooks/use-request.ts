import { ref, shallowRef } from 'vue';

import { ElMessage } from 'element-plus';

export interface UseRequestOptions<TData, TParams extends any[]> {
  manual?: boolean;
  defaultParams?: TParams;
  showErrorMessage?: boolean;
  errorMessage?: ((error: unknown) => string) | string;
  onSuccess?: (data: TData, params: TParams) => void;
  onError?: (error: unknown, params: TParams) => void;
  onFinally?: (params: TParams) => void;
}

export interface UseRequestResult<TData, TParams extends any[]> {
  loading: ReturnType<typeof ref<boolean>>;
  data: ReturnType<typeof shallowRef<TData | undefined>>;
  error: ReturnType<typeof shallowRef<unknown>>;
  run: (...params: TParams) => Promise<TData>;
}

export function useRequest<TData, TParams extends any[]>(
  service: (...params: TParams) => Promise<TData>,
  options?: UseRequestOptions<TData, TParams>,
): UseRequestResult<TData, TParams> {
  const mergedOptions: UseRequestOptions<TData, TParams> = {
    manual: true,
    ...options,
  };
  const loading = ref(false);
  const data = shallowRef<TData>();
  const error = shallowRef<unknown>();

  const run = async (...params: TParams) => {
    loading.value = true;
    error.value = undefined;
    try {
      const res = await service(...params);
      data.value = res;
      mergedOptions.onSuccess?.(res, params);
      return res;
    } catch (error_) {
      error.value = error_;
      mergedOptions.onError?.(error_, params);
      if (mergedOptions.showErrorMessage !== false) {
        const msg =
          typeof mergedOptions.errorMessage === 'function'
            ? mergedOptions.errorMessage(error_)
            : mergedOptions.errorMessage ||
              (error_ as any).message ||
              (error_ as any).msg ||
              '操作失败，请稍后重试';
        ElMessage.error(msg);
      }
      throw error_;
    } finally {
      loading.value = false;
      mergedOptions.onFinally?.(params);
    }
  };

  if (!mergedOptions.manual) {
    const initialParams = (mergedOptions.defaultParams ?? []) as unknown as TParams;
    run(...initialParams).catch(() => {});
  }

  return { loading, data, error, run };
}
