/**
 * 状态组件渲染辅助
 * @param {{ loading, error, empty, children, errorRender, emptyRender, loadingRender }}
 */
export function StateBoundary({ loading, error, empty, children, errorRender, emptyRender, loadingRender }) {
  if (loading) {
    return loadingRender || <div className="state-loading"><div className="state-spinner" /><p>加载中...</p></div>;
  }
  if (error) {
    return errorRender || <div className="state-error"><p>{error}</p></div>;
  }
  if (empty) {
    return emptyRender || <div className="state-empty"><p>暂无数据</p></div>;
  }
  return children;
}
