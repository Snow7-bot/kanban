import { ArrowUpRight, ChevronDown, MoreHorizontal } from 'lucide-react';

export function Card({ className = '', children, ...props }) {
  return <section className={`card ${className}`} {...props}>{children}</section>;
}

export function Button({ className = '', variant = '', children, ...props }) {
  return <button className={`button ${variant} ${className}`} {...props}>{children}</button>;
}

export function IconButton({ label, children, className = '', ...props }) {
  return <button className={`icon-button ${className}`} aria-label={label} {...props}>{children}</button>;
}

export function StatusChip({ tone = 'blue', children }) {
  return <span className={`chip ${tone}`}><span className="dot" />{children}</span>;
}

export function SectionHeading({ title, note, action }) {
  return <div className="section-heading"><h2>{title}</h2>{action || <span>{note}</span>}</div>;
}

export function MetricCard({ label, value, secondary, unit, hint, tone = 'blue', className = '', onClick, selected = false }) {
  return <button type="button" className={`card metric-card ${tone} ${selected ? 'selected' : ''} ${className}`} onClick={onClick}>
    <span className="metric-label">{label}</span>
    <strong className="metric-value">{value}{secondary && <small>{secondary}</small>}<small>{unit}</small></strong>
    <small className="metric-hint">{hint}</small>
  </button>;
}

export function MoreButton({ label = '更多操作' }) {
  return <button className="more-button" aria-label={label}><MoreHorizontal size={16} /></button>;
}

export function ArrowButton({ label = '查看详情' }) {
  return <IconButton label={label}><ArrowUpRight size={15} /></IconButton>;
}

export function SelectButton({ children, active, onClick }) {
  return <button className={`chart-tab ${active ? 'active' : ''}`} onClick={onClick}>{children}<ChevronDown size={12} /></button>;
}
