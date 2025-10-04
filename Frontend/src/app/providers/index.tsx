import { PropsWithChildren } from 'react';
import QueryProvider from './QueryProvider';
import ThemeProvider from './ThemeProvider';

const AppProviders = ({ children }: PropsWithChildren) => (
  <ThemeProvider>
    <QueryProvider>{children}</QueryProvider>
  </ThemeProvider>
);

export default AppProviders;
