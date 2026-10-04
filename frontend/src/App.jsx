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

function ProtectedRoute({ children }) {
  const token = localStorage.getItem('token');
  return token ? children : <Navigate to="/" />;
}

function AdminRoute({ children }) {
  const token = localStorage.getItem('token');
  const role = localStorage.getItem('role');
  if (!token) return <Navigate to="/" />;
  if (role !== 'ADMIN' && role !== 'STAFF') return <Navigate to="/products" />;
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
              <Route path="*" element={<Navigate to="/" />} />
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