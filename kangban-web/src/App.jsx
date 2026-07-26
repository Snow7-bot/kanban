import { useEffect, useState } from 'react';
import { pageFromHash, hashForPage } from './navigation.js';
import { shellModeForPage } from './shellMode.js';
import { useAuth } from './context/AuthContext.jsx';
import {
  clearPendingRoute,
  consumePendingRoute,
  isProtectedPage,
  rememberPendingRoute,
} from './authNavigation.js';
import AppShell from './components/AppShell.jsx';
import HomePage from './pages/HomePage.jsx';
import HealthTrendsPage from './pages/HealthTrendsPage.jsx';
import ConsultationPage from './pages/ConsultationPage.jsx';
import MedicalRecordsPage from './pages/MedicalRecordsPage.jsx';
import MedicationsPage from './pages/MedicationsPage.jsx';
import FamilyPage from './pages/FamilyPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import SettingsPage from './pages/SettingsPage.jsx';
import HealthRecordPage from './pages/HealthRecordPage.jsx';
import FamilyAddPage from './pages/FamilyAddPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import PasswordResetPage from './pages/PasswordResetPage.jsx';
import HealthReportPage from './pages/HealthReportPage.jsx';
import RecordDetailPage from './pages/RecordDetailPage.jsx';
import SharedRecordPage from './pages/SharedRecordPage.jsx';
import MedicationAddPage from './pages/MedicationAddPage.jsx';

const PAGES = {
  home: HomePage, profile: ProfilePage, family: FamilyPage,
  'family-add': FamilyAddPage, 'health-record': HealthRecordPage,
  'health-report': HealthReportPage, trends: HealthTrendsPage,
  consultation: ConsultationPage, records: MedicalRecordsPage,
  'record-detail': RecordDetailPage, 'shared-record': SharedRecordPage,
  medications: MedicationsPage, 'medication-add': MedicationAddPage, settings: SettingsPage,
  login: LoginPage, register: RegisterPage, 'password-reset': PasswordResetPage,
};

export default function App() {
  const { authReady, isAuthenticated } = useAuth();
  const [pageId, setPageId] = useState(() => pageFromHash(window.location.hash).pageId);
  const [pageParams, setPageParams] = useState(() => pageFromHash(window.location.hash).params);
  const [loginPromptOpen, setLoginPromptOpen] = useState(false);
  const [loginCompleted, setLoginCompleted] = useState(false);

  useEffect(() => {
    const onHashChange = () => {
      const { pageId: pid, params } = pageFromHash(window.location.hash);
      setPageId(pid);
      setPageParams(params);
    };
    window.addEventListener('hashchange', onHashChange);
    return () => window.removeEventListener('hashchange', onHashChange);
  }, []);

  // 全局错误提示 Toast
  const [toast, setToast] = useState(null);
  useEffect(() => {
    const onError = (e) => {
      setToast(e.detail);
      setTimeout(() => setToast(null), 3000);
    };
    const onSuccess = (e) => {
      setToast({ type: 'success', message: e.detail });
      setTimeout(() => setToast(null), 3000);
    };
    window.addEventListener('app:error', onError);
    window.addEventListener('app:success', onSuccess);
    return () => {
      window.removeEventListener('app:error', onError);
      window.removeEventListener('app:success', onSuccess);
    };
  }, []);

  useEffect(() => {
    if (!authReady || isAuthenticated || !isProtectedPage(pageId)) return;
    // Logout/401 changes the hash to home before React receives hashchange.
    // Skip the stale protected page so session end never opens the login prompt.
    if (pageFromHash(window.location.hash).pageId !== pageId) return;
    rememberPendingRoute(pageId, pageParams);
    setLoginPromptOpen(true);
    window.location.hash = hashForPage('home');
  }, [authReady, isAuthenticated, pageId, pageParams]);

  useEffect(() => {
    if (!loginCompleted || !isAuthenticated) return;
    const pendingRoute = consumePendingRoute();
    setLoginCompleted(false);
    window.location.hash = pendingRoute
      ? hashForPage(pendingRoute.pageId, pendingRoute.params)
      : hashForPage('home');
  }, [isAuthenticated, loginCompleted]);

  const navigateTo = (nextPage, params = {}) => {
    if (!authReady) return;
    if (!isAuthenticated && isProtectedPage(nextPage)) {
      rememberPendingRoute(nextPage, params);
      setLoginPromptOpen(true);
      return;
    }
    window.location.hash = hashForPage(nextPage, params);
  };

  const goToLogin = () => {
    setLoginPromptOpen(false);
    window.location.hash = hashForPage('login');
  };

  const cancelLoginPrompt = () => {
    clearPendingRoute();
    setLoginPromptOpen(false);
  };

  const handleLoginSuccess = () => {
    setLoginCompleted(true);
  };

  const effectivePageId = !authReady || (!isAuthenticated && isProtectedPage(pageId)) ? 'home' : pageId;
  const Page = PAGES[effectivePageId] || HomePage;
  const page = <Page onNavigate={navigateTo} onLoginSuccess={handleLoginSuccess} />;

  return (
    <>
      {shellModeForPage(effectivePageId) === 'app'
        ? <AppShell pageId={effectivePageId} onNavigate={navigateTo} showTopBar={effectivePageId !== 'trends' && effectivePageId !== 'home' && effectivePageId !== 'health-report' && effectivePageId !== 'record-detail' && effectivePageId !== 'medication-add'}>{page}</AppShell>
        : page}
      {loginPromptOpen && (
        <div className="login-required-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) cancelLoginPrompt();
        }}>
          <section className="login-required-dialog" role="dialog" aria-modal="true" aria-labelledby="login-required-title">
            <span className="login-required-icon">+</span>
            <h2 id="login-required-title">登录后即可使用此功能</h2>
            <p>登录后可安全访问您的健康数据、病历、用药记录和智能问诊服务。</p>
            <div>
              <button type="button" onClick={cancelLoginPrompt}>暂不登录</button>
              <button type="button" className="primary" onClick={goToLogin}>立即登录</button>
            </div>
          </section>
        </div>
      )}
      {toast && (
        <div className={`app-toast ${toast?.type === 'success' ? 'app-toast-success' : ''}`} role="alert">
          {typeof toast === 'string' ? toast : toast.message}
        </div>
      )}
    </>
  );
}
