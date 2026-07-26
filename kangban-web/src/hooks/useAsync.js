import { useState, useCallback, useRef } from 'react';

/**
 * @param {Function} asyncFn - (params) => Promise 异步函数
 * @param {object} options
 * @param {any} options.initialData - 初始数据
 * @param {boolean} options.immediate - 是否立即执行
 * @param {any} options.immediateParams - 立即执行的参数
 * @returns {{ data, loading, error, empty, execute, refresh, setData, reset }}
 */
export function useAsync(asyncFn, options = {}) {
  const { initialData = null, immediate = false, immediateParams } = options;
  const [data, setData] = useState(initialData);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const mountedRef = useRef(true);
  const lastArgsRef = useRef(immediateParams === undefined ? [] : [immediateParams]);

  const execute = useCallback(async (...args) => {
    lastArgsRef.current = args;
    setLoading(true);
    setError(null);
    try {
      const result = await asyncFn(...args);
      if (mountedRef.current) {
        setData(result);
      }
      return result;
    } catch (err) {
      if (mountedRef.current) {
        setError(err.message || '操作失败');
      }
      throw err;
    } finally {
      if (mountedRef.current) {
        setLoading(false);
      }
    }
  }, [asyncFn]);

  const refresh = useCallback(() => {
    return execute(...lastArgsRef.current);
  }, [execute]);

  const reset = useCallback(() => {
    setData(initialData);
    setError(null);
    setLoading(false);
  }, [initialData]);

  // 立即执行
  const executedRef = useRef(false);
  if (immediate && !executedRef.current && asyncFn) {
    executedRef.current = true;
    setTimeout(() => { execute(...lastArgsRef.current); }, 0);
  }

  const empty = !loading && !error && (
    data === null || data === undefined ||
    (Array.isArray(data) && data.length === 0)
  );

  return { data, loading, error, empty, execute, refresh, setData, reset };
}
