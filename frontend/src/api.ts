import axios from 'axios'

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('cartora.token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401 && !error.config?.url?.endsWith('/api/auth/login')) {
      localStorage.removeItem('cartora.token')
      window.dispatchEvent(new Event('cartora:unauthorized'))
    }
    return Promise.reject(error)
  },
)

export type Product = {
  id: number
  name: string
  description: string
  imageUrl?: string | null
  price: number
  stockQuantity: number
  sku: string
  categoryId: number
}

export type ProductPage = {
  content: Product[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type Category = { categoryId: number; name: string; description: string }
export type CartItem = {
  cartItemId: number
  productId: number
  productName: string
  price: number
  quantity: number
  subtotal: number
}
export type Cart = { cartId: number; userId: number; items: CartItem[] }
export type User = { id: number; name: string; email: string; role: 'CUSTOMER' | 'ADMIN' }
export type Order = {
  orderId: number
  totalAmount: number
  discountAmount?: number
  couponCode?: string | null
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'SHIPPED' | 'DELIVERED' | 'REFUNDED'
  orderItems: { productId: number; productName: string; quantity: number; price: number; subtotal: number }[]
  createdAt: string
  shippingAddress?: AddressInput | null
}
export type AddressInput = {
  recipientName: string
  houseNumberOrBuildingNumber: string
  localityOrLandmark: string
  city: string
  state: string
  postalCode: string
  country: string
}
export type WishlistItem = {
  productId: number
  productName: string
  description: string
  price: number
  stockQuantity: number
  addedAt: string
}
export type ProductReview = {
  id: number
  productId: number
  userId: number
  reviewerName: string
  rating: number
  title: string
  comment: string
  createdAt: string
  updatedAt: string
}
export type ProductRatingSummary = { averageRating: number; reviewCount: number }
export type Coupon = {
  id: number
  code: string
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT'
  discountValue: number
  minimumOrderAmount: number
  maximumDiscountAmount: number | null
  validFrom: string | null
  validUntil: string | null
  active: boolean
}
export type AdminCustomer = { id: number; name: string; email: string; role: User['role']; createdAt: string }
export type PaymentRecord = {
  paymentId: number
  orderId: number
  amount: number
  status: string
  paymentMethod: string
  transactionId: string
  createdAt: string
  providerOrderId: string | null
  providerPaymentId: string | null
  failureReason: string | null
}
export type AdminDashboard = {
  customerCount: number
  productCount: number
  lowStockProductCount: number
  pendingOrderCount: number
  ordersInFulfilmentCount: number
  paidRevenue: number
}
export type SalesPoint = { date: string; revenue: number }

const RECENT_PRODUCTS_KEY = 'cartora.recent-products'
export function getRecentlyViewedIds(): number[] {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(RECENT_PRODUCTS_KEY) ?? '[]')
    return Array.isArray(stored)
      ? stored.filter((id): id is number => Number.isInteger(id) && id > 0).slice(0, 5)
      : []
  } catch {
    return []
  }
}

export function recordRecentlyViewed(productId: number) {
  if (localStorage.getItem('cartora.token')) {
    void api.post(`/api/users/me/recently-viewed/${productId}`).catch(() => undefined)
    return
  }
  const recent = getRecentlyViewedIds().filter((id) => id !== productId)
  localStorage.setItem(RECENT_PRODUCTS_KEY, JSON.stringify([productId, ...recent].slice(0, 5)))
}

export function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message ?? error.message
  }
  return error instanceof Error ? error.message : 'Something went wrong.'
}

export const formatPrice = (amount: number) =>
  new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(amount)

export const getProducts = (params: Record<string, string | number | undefined>) =>
  api.get<ProductPage>('/api/products', { params }).then(({ data }) => data)
export const getProduct = (id: number) => api.get<Product>(`/api/products/${id}`).then(({ data }) => data)
export async function getRecentlyViewedProducts(isAuthenticated: boolean) {
  if (isAuthenticated) {
    // Merge this browser's guest history into the account before loading its list.
    const guestHistory = getRecentlyViewedIds().reverse()
    let merged = true
    for (const productId of guestHistory) {
      try {
        await api.post(`/api/users/me/recently-viewed/${productId}`)
      } catch {
        merged = false
        break
      }
    }
    if (merged && guestHistory.length) localStorage.removeItem(RECENT_PRODUCTS_KEY)
    return api.get<Product[]>('/api/users/me/recently-viewed').then(({ data }) => data)
  }
  const products = await Promise.all(getRecentlyViewedIds().map((productId) =>
    getProduct(productId).catch(() => null),
  ))
  return products.filter((product): product is Product => product !== null)
}
export const getCategories = () => api.get<Category[]>('/api/categories').then(({ data }) => data)
export const getCart = () => api.get<Cart>('/api/cart').then(({ data }) => data)
export const addCartItem = (productId: number, quantity = 1) =>
  api.post<Cart>('/api/cart/items', { productId, quantity }).then(({ data }) => data)
export const updateCartItem = (id: number, quantity: number) =>
  api.put<Cart>(`/api/cart/items/${id}`, { quantity }).then(({ data }) => data)
export const removeCartItem = (id: number) => api.delete(`/api/cart/items/${id}`)
export const clearCart = () => api.delete('/api/cart')
export const getOrders = () => api.get<Order[]>('/api/orders').then(({ data }) => data)
export const getOrder = (id: number) => api.get<Order>(`/api/orders/${id}`).then(({ data }) => data)
export const createOrder = (shippingAddress: AddressInput, couponCode?: string | null) =>
  api.post<Order>('/api/orders', { shippingAddress, couponCode }).then(({ data }) => data)
export const cancelOrder = (id: number) => api.put<Order>(`/api/orders/${id}/cancel`).then(({ data }) => data)
export const getWishlist = () => api.get<WishlistItem[]>('/api/wishlist').then(({ data }) => data)
export const addWishlistItem = (productId: number) =>
  api.post<WishlistItem>('/api/wishlist/items', { productId }).then(({ data }) => data)
export const removeWishlistItem = (productId: number) => api.delete(`/api/wishlist/items/${productId}`)
export const getProductReviews = (productId: number) =>
  api.get<ProductReview[]>(`/api/products/${productId}/reviews`).then(({ data }) => data)
export const getProductRating = (productId: number) =>
  api.get<ProductRatingSummary>(`/api/products/${productId}/reviews/summary`).then(({ data }) => data)
export const createProductReview = (productId: number, review: Omit<ProductReview, 'id' | 'productId' | 'userId' | 'reviewerName' | 'createdAt' | 'updatedAt'>) =>
  api.post<ProductReview>(`/api/products/${productId}/reviews`, review).then(({ data }) => data)
export const updateProductReview = (productId: number, review: Omit<ProductReview, 'id' | 'productId' | 'userId' | 'reviewerName' | 'createdAt' | 'updatedAt'>) =>
  api.put<ProductReview>(`/api/products/${productId}/reviews/me`, review).then(({ data }) => data)
export const deleteProductReview = (productId: number) => api.delete(`/api/products/${productId}/reviews/me`)
export const getAdminCoupons = () => api.get<Coupon[]>('/api/admin/coupons').then(({ data }) => data)
export const createAdminCoupon = (coupon: Omit<Coupon, 'id' | 'active'>) =>
  api.post<Coupon>('/api/admin/coupons', coupon).then(({ data }) => data)
export const setAdminCouponActive = (id: number, active: boolean) =>
  api.patch<Coupon>(`/api/admin/coupons/${id}/active`, undefined, { params: { active } }).then(({ data }) => data)
