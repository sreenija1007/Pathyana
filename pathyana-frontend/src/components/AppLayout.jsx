import { Link, useLocation } from 'react-router-dom';
import Logo from './Logo';
import Button from './Button';
import { useAuth } from '../context/AuthContext';

/**
 * Authenticated app shell.
 *
 * Nav links updated to include "Weekly" so users can find the new feature.
 */
export default function AppLayout({ children }) {
  const { user, logout } = useAuth();
  const location = useLocation();

  const navLinks = [
    { to: '/meals',         label: 'Meals',   match: (p) => p === '/meals' },
    { to: '/meals/weekly',  label: 'Weekly',  match: (p) => p.startsWith('/meals/weekly') },
    { to: '/profile',       label: 'Profile', match: (p) => p.startsWith('/profile') },
  ];

  return (
    <div className="min-h-screen bg-cream-50">
      <nav className="border-b border-cream-200 bg-white sticky top-0 z-10">
        <div className="container-pad flex items-center justify-between py-4">
          <div className="flex items-center gap-8">
            <Link to="/meals"><Logo size="md" /></Link>
            <div className="hidden sm:flex gap-2">
              {navLinks.map((link) => {
                const active = link.match(location.pathname);
                return (
                  <Link
                    key={link.to}
                    to={link.to}
                    className={`px-3 py-1.5 rounded-md text-sm font-medium transition ${
                      active
                        ? 'bg-terracotta-50 text-terracotta-700'
                        : 'text-ink-soft hover:text-ink hover:bg-cream-100'
                    }`}
                  >
                    {link.label}
                  </Link>
                );
              })}
            </div>
          </div>
          <div className="flex items-center gap-3">
            <span className="hidden sm:inline text-sm text-ink-soft">
              {user?.name}
            </span>
            <Button variant="ghost" size="sm" onClick={logout}>Sign out</Button>
          </div>
        </div>
      </nav>
      <main>{children}</main>
    </div>
  );
}
