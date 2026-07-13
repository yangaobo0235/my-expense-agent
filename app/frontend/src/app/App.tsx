import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Spin } from 'antd';
import { ProtectedRoute } from './ProtectedRoute';
import { AppLayout } from './AppLayout';
import { LoginPage } from '../features/auth/LoginPage';

const CaseListPage = lazy(() =>
  import('../features/cases/CaseListPage').then((module) => ({ default: module.CaseListPage })),
);
const CaseDetailPage = lazy(() =>
  import('../features/cases/CaseDetailPage').then((module) => ({ default: module.CaseDetailPage })),
);
const NewCasePage = lazy(() =>
  import('../features/cases/NewCasePage').then((module) => ({ default: module.NewCasePage })),
);
const ReviewQueuePage = lazy(() =>
  import('../features/reviews/ReviewQueuePage').then((module) => ({ default: module.ReviewQueuePage })),
);
const ReviewTaskDetailPage = lazy(() =>
  import('../features/reviews/ReviewTaskDetailPage').then((module) => ({ default: module.ReviewTaskDetailPage })),
);
const PolicyCatalogPage = lazy(() =>
  import('../features/policies/PolicyCatalogPage').then((module) => ({ default: module.PolicyCatalogPage })),
);
const EvaluationReportPage = lazy(() =>
  import('../features/evaluation/EvaluationReportPage').then((module) => ({ default: module.EvaluationReportPage })),
);
const ObservabilityPage = lazy(() =>
  import('../features/observability/ObservabilityPage').then((module) => ({ default: module.ObservabilityPage })),
);
const PromptGovernancePage = lazy(() =>
  import('../features/prompts/PromptGovernancePage').then((module) => ({ default: module.PromptGovernancePage })),
);

export function App() {
  return (
    <Suspense fallback={<div className="center-screen"><Spin size="large" /></div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route index element={<Navigate to="/cases" replace />} />
            <Route path="/cases" element={<CaseListPage />} />
            <Route path="/cases/:caseId" element={<CaseDetailPage />} />
            <Route element={<ProtectedRoute roles={['STUDENT', 'ADVISOR']} />}>
              <Route path="/cases/new" element={<NewCasePage />} />
            </Route>
            <Route
              element={<ProtectedRoute roles={['ADVISOR', 'COLLEGE_REVIEWER', 'FINANCE_ADMIN']} />}
            >
              <Route path="/reviews" element={<ReviewQueuePage />} />
              <Route path="/reviews/:taskId" element={<ReviewTaskDetailPage />} />
            </Route>
            <Route
              element={<ProtectedRoute roles={['COLLEGE_REVIEWER', 'FINANCE_ADMIN', 'AUDITOR']} />}
            >
              <Route path="/policies" element={<PolicyCatalogPage />} />
              <Route path="/evaluation" element={<EvaluationReportPage />} />
            </Route>
            <Route
              element={<ProtectedRoute roles={['PROMPT_AUTHOR', 'PROMPT_REVIEWER', 'PROMPT_PUBLISHER', 'FINANCE_ADMIN', 'AUDITOR']} />}
            >
              <Route path="/prompts" element={<PromptGovernancePage />} />
            </Route>
            <Route
              element={<ProtectedRoute roles={['COLLEGE_REVIEWER', 'FINANCE_ADMIN', 'AUDITOR']} />}
            >
              <Route path="/observability" element={<ObservabilityPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/cases" replace />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}
