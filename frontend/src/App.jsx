import { tokenIsCurrent, adminGuard, staffGuard } from './utils/session';
import { BrowserRouter, Routes, Route, Navigate, useParams } from 'react-router-dom';
import { useEffect, useState } from 'react';
import axiosClient from './api/axiosClient';
import { LanguageProvider } from './context/LanguageContext';
import { CompareProvider } from './context/CompareContext';
import Login from './pages/Login';
import Products from './pages/Products';
import ProductDetail from './pages/ProductDetail';
import Orders from './pages/Orders';
import Cart from './pages/Cart';
import WishlistPage from './pages/WishlistPage';
import GroupOrders from './pages/GroupOrders';
import GroupOrderDetail from './pages/GroupOrderDetail';
import Compare from './pages/Compare';
import GiftFinder from './pages/GiftFinder';
import Account from './pages/Account';
import Header from './components/Header';
import Footer from './components/Footer';
import ChatAssistant from './components/ChatAssistant';
import CompareDock from './components/CompareDock';
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
import AdminGroupOrders from './pages/admin/AdminGroupOrders';
import AdminStaff from './pages/admin/AdminStaff';
import AdminStaffTasks from './pages/admin/AdminStaffTasks';
import AdminStaffApprovals from './pages/admin/AdminStaffApprovals';
import AdminStaffLogs from './pages/admin/AdminStaffLogs';
import StaffApply from './pages/staff/StaffApply';
import StaffLayout from './pages/staff/StaffLayout';
import StaffDashboard from './pages/staff/StaffDashboard';
import StaffTasks from './pages/staff/StaffTasks';
import StaffApprovals from './pages/staff/StaffApprovals';
import StaffProfile from './pages/staff/StaffProfile';

function ProtectedRoute({ children }) {
  const token = localStorage.getItem('token');
  return tokenIsCurrent(token) ? children : <Navigate to="/" replace />;
}

function AdminRoute({ children }) {
  // The rule lives in utils/session so it can be tested: reading the role from
  // localStorage is what used to dump an admin on the customer catalogue every
  // time the page was refreshed.
  const blocked = adminGuard(localStorage.getItem('token'), localStorage.getItem('role'));
  return blocked ? <Navigate to={blocked} replace /> : children;
}

/** Joins a group from a shared invite link, then lands on the group page. */
function JoinViaLink() {
  const { inviteCode } = useParams();
  const [state, setState] = useState('joining');
  useEffect(() => {
    axiosClient.post('/group-orders/join', { code: inviteCode })
      .then((res) => {
        window.location.assign(`/group-orders/${res.data.id}`);
      })
      .catch(() => setState('failed'));
  }, [inviteCode]);
  if (state === 'failed') return <Navigate to="/group-orders" replace />;
  return <p className="max-w-[1320px] mx-auto px-6 py-16">…</p>;
}

function StaffRoute({ children }) {
  const blocked = staffGuard(localStorage.getItem('token'), localStorage.getItem('role'));
  return blocked ? <Navigate to={blocked} replace /> : children;
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
              <Route path="/products" element={<Products />} />
              <Route path="/product/:id" element={<ProductDetail />} />
              <Route path="/compare" element={<Compare />} />
              <Route path="/gift-finder" element={<ProtectedRoute><GiftFinder /></ProtectedRoute>} />
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
                <Route path="group-orders" element={<AdminGroupOrders />} />
                <Route path="staff" element={<AdminStaff />} />
                <Route path="staff/tasks" element={<AdminStaffTasks />} />
                <Route path="staff/approvals" element={<AdminStaffApprovals />} />
                <Route path="staff/logs" element={<AdminStaffLogs />} />
              </Route>
              <Route path="/staff/apply" element={<StaffApply />} />
              <Route path="/staff" element={<StaffRoute><StaffLayout /></StaffRoute>}>
                <Route index element={<Navigate to="/staff/dashboard" replace />} />
                <Route path="dashboard" element={<StaffDashboard />} />
                <Route path="tasks" element={<StaffTasks />} />
                <Route path="approvals" element={<StaffApprovals />} />
                <Route path="profile" element={<StaffProfile />} />
              </Route>
              <Route path="/orders" element={<ProtectedRoute><Orders /></ProtectedRoute>} />
              <Route path="/group-orders" element={<ProtectedRoute><GroupOrders /></ProtectedRoute>} />
              <Route path="/group-orders/:id" element={<ProtectedRoute><GroupOrderDetail /></ProtectedRoute>} />
              <Route path="/group-orders/join/:inviteCode" element={<ProtectedRoute><JoinViaLink /></ProtectedRoute>} />
              <Route path="/wishlist" element={<ProtectedRoute><WishlistPage /></ProtectedRoute>} />
              <Route path="/cart" element={<ProtectedRoute><Cart /></ProtectedRoute>} />
              <Route path="/account" element={<ProtectedRoute><Account /></ProtectedRoute>} />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </main>
          <Footer />
          {/* Site-wide, so it is reachable from every page including checkout. */}
          <ChatAssistant />
          <CompareDock />
        </BrowserRouter>
      </CompareProvider>
    </LanguageProvider>
  );
}

export default App;