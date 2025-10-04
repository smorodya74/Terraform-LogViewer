import ReactDOM from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import AppProviders from './providers';
import router from './router/routes';
import '../../assets/index.css';

ReactDOM.createRoot(document.getElementById('root') as HTMLElement).render(
  <AppProviders>
    <RouterProvider router={router} />
  </AppProviders>,
);
