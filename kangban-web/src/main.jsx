import React from 'react';
import { createRoot } from 'react-dom/client';
import App from './App.jsx';
import { AuthProvider } from './context/AuthContext.jsx';
import './styles/tokens.css';
import './styles/global.css';
import './styles/home.css';
import './styles/trends.css';
import './styles/account.css';
import './styles/stitch-forms.css';
import './styles/report.css';
import './styles/record-detail.css';
import './styles/medication-add.css';
import './styles/avatar-sync.css';
import './styles/knowledge-admin.css';

createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </React.StrictMode>,
);
