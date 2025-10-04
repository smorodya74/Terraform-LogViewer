import { createBrowserRouter } from 'react-router-dom';
import App from '../App';
import ImportPage from '../../pages/ImportPage';
import AnalyzePage from '../../pages/AnalyzePage';
import TimelinePage from '../../pages/TimelinePage';
import ReportsPage from '../../pages/ReportsPage';

const router = createBrowserRouter([
  {
    path: '/',
    element: <App />,
    children: [
      {
        index: true,
        element: <ImportPage />,
      },
      {
        path: 'import',
        element: <ImportPage />,
      },
      {
        path: 'analyze',
        element: <AnalyzePage />,
      },
      {
        path: 'timeline',
        element: <TimelinePage />,
      },
      {
        path: 'reports',
        element: <ReportsPage />,
      },
    ],
  },
]);

export default router;
