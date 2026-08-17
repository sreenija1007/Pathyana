import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import App from './App';
import './index.css';

/**
 * Application entry point.
 *
 * Wrapper order matters: BrowserRouter must be outside AuthProvider because
 * AuthContext could use useNavigate in the future (e.g., redirect after logout).
 */
ReactDOM.createRoot(document.getElementById('root')).render(
  <BrowserRouter>
    <AuthProvider>
      <App />
    </AuthProvider>
  </BrowserRouter>
);
