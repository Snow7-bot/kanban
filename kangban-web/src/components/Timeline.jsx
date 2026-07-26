export default function Timeline({ items, activeId }) {
  return <div className="timeline">{items.map(item => <div className="timeline-item" key={item.id}><div className={`timeline-dot ${activeId === item.id ? 'active' : ''}`} /><small>{item.date}</small><strong>{item.label}</strong></div>)}</div>;
}
