import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { LanguageProvider } from './context/LanguageContext';
import { CompareProvider } from './context/CompareContext';
import Login from './pages/Login';
import Products from './pages/Products';
import ProductDetail from './pages/ProductDetail';
import Orders from './pages/Orders';
import Cart from './pages/Cart';
import WishlistPage from './pages/WishlistPage';
import Compare from './pages/Compare';
import Account from './pages/Account';
import Header from './components/Header';
import Footer from './components/Footer';
import ChatAssistant from './components/ChatAssistant';
import AdminLayout from './pages/admin/AdminLayout';
import AdminDashboard from './pages/admin/AdminDashboard';
import AdminProducts from './pages/admin/AdminProducts';
import AdminOrders from './pages/admin/AdminOrders';
import AdminCustomers from './pages/admin/AdminCustomers';
import AdminCategories from './pages/admin/AdminCategories';
import AdminCoupons from './pages/admin/AdminCoupons';
import AdminPayments from './pages/admin/AdminPayments';
import AdminReviews from './pages/admin/AdminReviews';
import AdminReports from './pages/admin/AdminReports';

/**
 * Reads the claims out of the JWT payload.
 *
 * The payload is only ever *decoded* here, never trusted for authorisation - the
 * server is what validates the signature. It is used as a fallback so the admin
 * guard still knows who you are when the role cached in localStorage is missing.
 */
function readClaims(token) {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);
    const bytes = atob(padded);
    // Decode as UTF-8 by hand: the payload can carry non-ASCII, and atob alone
    // would hand back mojibake for anything outside Latin-1.
    const json = decodeURIComponent(
      Array.from(bytes, (c) => `%${c.charCodeAt(0).toString(16).padStart(2, '0')}`).join('')
    );
    return JSON.parse(json);
  } catch {
    return null;
  }
}

function ProtectedRoute({ children }) {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/" replace />;
}

function AdminRoute({ children }) {
  const token = localStorage.getItem('token');
  if (!token) return <Navigate to="/" replace />;

  const claims = readClaims(token);
  // A token we cannot read is no use, and an expired one would only produce a
  // page of failed admin requests, so both go back to sign-in.
  if (!claims) return <Navigate to="/" replace />;
  if (claims.exp && claims.exp * 1000 <= Date.now()) return <Navigate to="/" replace />;

  // The role claim is signed by the server, so it survives the cached copy going
  // missing - which is what used to dump an admin on the customer catalogue on a
  // refresh - and unlike localStorage it cannot be edited from devtools.
  const role = claims.role || localStorage.getItem('role');
  if (role !== 'ADMIN' && role !== 'STAFF') return <Navigate to="/products" replace />;
  return children;
}

function App() {
  return (
    <LanguageProvider>
      <CompareProvider>
        <BrowserRouter>
          <Header />
          <main className="min-h-[70vh]">
            <Routes>
              <Route path="/" element={<Login />} />
              <Route path="/login" element={<Login />} />
              <Route path="/products" element={<ProtectedRoute><Products /></ProtectedRoute>} />
              <Route path="/product/:id" element={<ProtectedRoute><ProductDetail /></ProtectedRoute>} />
              <Route path="/compare" element={<ProtectedRoute><Compare /></ProtectedRoute>} />
              <Route path="/admin" element={<AdminRoute><AdminLayout /></AdminRoute>}>
                <Route index element={<AdminDashboard />} />
                <Route path="products" element={<AdminProducts />} />
                <Route path="orders" element={<AdminOrders />} />
                <Route path="customers" element={<AdminCustomers />} />
                <Route path="categories" element={<AdminCategories />} />
                <Route path="coupons" element={<AdminCoupons />} />
                <Route path="payments" element={<AdminPayments />} />
                <Route path="reviews" element={<AdminReviews />} />
                <Route path="reports" element={<AdminReports />} />
              </Route>
              <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
              <Route path="/wishlist" element={<ProtectedRoute><WishlistPage /></ProtectedRoute>} />
              <Route path="/cart" element={<ProtectedRoute><Cart /></ProtectedRoute>} />
              <Route path="/account" element={<ProtectedRoute><Account /></ProtectedRoute>} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
          <Footer />
          {/* Site-wide, so it is reachable from every page including checkout. */}
          <ChatAssistant />
        </BrowserRouter>
      </CompareProvider>
    </LanguageProvider>
  );
}

export default App;