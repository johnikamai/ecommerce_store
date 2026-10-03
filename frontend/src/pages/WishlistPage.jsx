import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosClient from '../api/axiosClient';
import { useCart } from '../context/CartContext';
import { getCustomerId } from '../utils/customer';
import ProductImage from '../components/ProductImage';

function WishlistPage() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const { addToCart } = useCart();
  const navigate = useNavigate();

  const fetchWishlist = async () => {
    try {
      const res = await axiosClient.get(`/wishlist/customer/${getCustomerId()}`);
      setItems(res.data);
    } catch (err) {
      console.error('Failed to load wishlist');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchWishlist();
  }, []);

  const handleRemove = async (productId) => {
    try {
      await axiosClient.delete(`/wishlist/customer/${getCustomerId()}/product/${productId}`);
      fetchWishlist();
    } catch (err) {
      console.error('Failed to remove');
    }
  };

  const handleMoveToCart = (product) => {
    addToCart(product, 1);
    handleRemove(product.id);
  };

  if (loading) return <p className="max-w-[1320px] mx-auto px-6 py-12">Loading wishlist...</p>;

  if (items.length === 0) {
    return (
      <div className="max-w-[1320px] mx-auto px-6 py-20 text-center">
        <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-[var(--color-card-bg-tint)] mb-4 text-2xl">
          ♡
        </div>
        <h4 className="font-[family-name:var(--font-heading)] text-lg font-semibold mb-1">Your wishlist is empty</h4>
        <p className="text-[var(--color-text-muted)] mb-4">Save items you like for later</p>
        <button
          onClick={() => navigate('/products')}
          className="rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white px-5 py-2.5 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
        >
          Browse Products
        </button>
      </div>
    );
  }

  return (
    <div className="max-w-[1320px] mx-auto px-6 py-8">
      <h2 className="font-[family-name:var(--font-heading)] text-[32px] font-bold mb-6">My Wishlist</h2>

      <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-4 gap-5">
        {items.map((item) => (
          <div key={item.id} className="rounded-[var(--radius-lg)] bg-[var(--color-card-bg)] shadow-[var(--shadow-sm)] overflow-hidden flex flex-col">
            <div className="relative aspect-square bg-[var(--color-card-bg-tint)] flex items-center justify-center overflow-hidden">
<ProductImage product={item.product} className="w-full h-full object-cover" />
              <button
                onClick={() => handleRemove(item.product.id)}
                className="absolute top-2 right-2 w-8 h-8 rounded-full bg-white shadow-[var(--shadow-sm)] flex items-center justify-center text-[var(--color-secondary)]"
                aria-label="Remove from wishlist"
              >
                ♥
              </button>
            </div>
            <div className="p-4 flex flex-col flex-1">
              <h3 className="font-[family-name:var(--font-heading)] font-semibold mb-1">{item.product.name}</h3>
              <p className="font-[family-name:var(--font-heading)] text-lg font-bold mb-3">₹{item.product.price}</p>
              <button
                onClick={() => handleMoveToCart(item.product)}
                className="mt-auto rounded-[var(--radius-md)] bg-[var(--color-primary)] text-white py-2 text-sm font-semibold hover:bg-[var(--color-primary-hover)] transition-colors"
              >
                Move to Cart
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default WishlistPage;