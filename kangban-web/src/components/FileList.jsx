import { FileText } from 'lucide-react';

export default function FileList({ files, selectedId, onSelect }) {
  return <div className="file-list">{files.map(file => <button key={file.id} className={`file-row ${file.tone} ${selectedId === file.id ? 'active' : ''}`} onClick={() => onSelect(file.id)}><span className="file-type"><FileText size={13} /></span><span><strong>{file.name}</strong><small>{file.meta}</small></span></button>)}</div>;
}
