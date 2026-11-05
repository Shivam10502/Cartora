import { createContext, type FormEvent, startTransition, useCallback, useContext, useEffect, useState } from 'react'
import { BrowserRouter, Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { ArrowDownUp, ArrowRight, Check, ChevronLeft, ChevronRight, Heart, LogOut, Search, ShoppingBag, Star, UserRound } from 'lucide-react'
import { api, addCartItem, addWishlistItem, cancelOrder, clearCart, createAdminCoupon, createOrder, createProductReview, deleteProductReview, formatPrice, getAdminCoupons, getCart, getCategories, getErrorMessage, getOrder, getOrders, getProduct, getProductRating, getProductReviews, getProducts, getRecentlyViewedProducts, getWishlist, recordRecentlyViewed, removeCartItem, removeWishlistItem, setAdminCouponActive, updateCartItem, updateProductReview } from './api'
import type { AdminCustomer, AdminDashboard, Cart, Category, Coupon, Order, PaymentRecord, Product, ProductPage, ProductRatingSummary, ProductReview, SalesPoint, User, WishlistItem } from './api'
import './App.css'
import './address.css'

type AppState = { user: User | null; cart: Cart | null; refreshCart: () => Promise<void>; signIn: (email: string, password: string) => Promise<void>; signOut: () => void; updateUser: (user: User) => void }
type RazorpayCheckoutResult = { razorpay_order_id: string; razorpay_payment_id: string; razorpay_signature: string }
type RazorpayCheckoutOptions = {
  key: string
  amount: number
  currency: string
  name: string
  description: string
  order_id: string
  handler: (result: RazorpayCheckoutResult) => void
  modal: { ondismiss: () => void }
}
type RazorpayCheckoutInstance = { open: () => void; on: (event: string, handler: (response: { error?: { description?: string } }) => void) => void }
type RazorpayCheckoutConstructor = new (options: RazorpayCheckoutOptions) => RazorpayCheckoutInstance
declare global { interface Window { Razorpay?: RazorpayCheckoutConstructor } }

function loadRazorpayCheckout(): Promise<RazorpayCheckoutConstructor> {
  if (window.Razorpay) return Promise.resolve(window.Razorpay)
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = 'https://checkout.razorpay.com/v1/checkout.js'
    script.async = true
    script.onload = () => window.Razorpay ? resolve(window.Razorpay) : reject(new Error('Razorpay checkout could not be loaded.'))
    script.onerror = () => reject(new Error('Razorpay checkout could not be loaded.'))
    document.head.appendChild(script)
  })
}

const State = createContext<AppState | null>(null)
const useApp = () => {
  const value = useContext(State)
  if (!value) throw new Error('App state is unavailable')
  return value
}

export default function App() {
  const [user, setUser] = useState<User | null>(null)
  const [cart, setCart] = useState<Cart | null>(null)
  const [ready, setReady] = useState(() => !localStorage.getItem('cartora.token'))
  const refreshCart = useCallback(async () => {
    if (!localStorage.getItem('cartora.token')) { setCart(null); return }
    try { setCart(await getCart()) } catch { setCart(null) }
  }, [])
  const signOut = useCallback(() => { localStorage.removeItem('cartora.token'); setUser(null); setCart(null) }, [])
  const updateUser = useCallback((updatedUser: User) => setUser(updatedUser), [])
  const signIn = useCallback(async (email: string, password: string) => {
    const { data } = await api.post<{ token: string }>('/api/auth/login', { email, password })
    localStorage.setItem('cartora.token', data.token)
    const { data: profile } = await api.get<User>('/api/users/me')
    setUser(profile)
    await refreshCart()
  }, [refreshCart])
  useEffect(() => {
    if (!localStorage.getItem('cartora.token')) return
    api.get<User>('/api/users/me').then(async ({ data }) => { setUser(data); await refreshCart() }).catch(signOut).finally(() => setReady(true))
  }, [refreshCart, signOut])
  useEffect(() => {
    const expireSession = () => { setUser(null); setCart(null) }
    window.addEventListener('cartora:unauthorized', expireSession)
    return () => window.removeEventListener('cartora:unauthorized', expireSession)
  }, [])
  return <BrowserRouter><State.Provider value={{ user, cart, refreshCart, signIn, signOut, updateUser }}>{ready ? <Routes><Route element={<StoreLayout />}>
    <Route index element={<Browse />} /><Route path="product/:id" element={<ProductDetails />} />
    <Route path="login" element={<AuthPage />} /><Route path="register" element={<AuthPage register />} />
    <Route path="forgot-password" element={<ForgotPasswordPage />} /><Route path="reset-password" element={<ResetPasswordPage />} />
    <Route element={<RequireAuth />}>
      <Route path="cart" element={<CartPage />} /><Route path="checkout" element={<CheckoutPage />} />
      <Route path="orders" element={<OrdersPage />} /><Route path="orders/:id" element={<OrderDetailsPage />} /><Route path="profile" element={<ProfilePage />} /><Route path="addresses" element={<AddressesPage />} /><Route path="wishlist" element={<WishlistPage />} />
    </Route>
    <Route element={<RequireAuth admin />}>
      <Route path="admin" element={<AdminPage />} /><Route path="admin/overview" element={<AdminDashboardPage />} /><Route path="admin/customers" element={<AdminCustomersPage />} /><Route path="admin/payments" element={<AdminPaymentsPage />} /><Route path="admin/categories" element={<AdminCategoriesPage />} /><Route path="admin/orders" element={<AdminOrdersPage />} /><Route path="admin/coupons" element={<AdminCouponsPage />} />
    </Route>
  </Route></Routes> : <main className="loading-screen">Cartora</main>}</State.Provider></BrowserRouter>
}

function RequireAuth({ admin = false }: { admin?: boolean }) {
  const { user } = useApp()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />
  return <Outlet />
}

function StoreLayout() {
  const { user, cart, signOut } = useApp()
  const count = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0
  return <div className="app-shell"><div className="announcement">Thoughtful finds for everyday living <span>Complimentary delivery over ₹6,000</span></div>
    <header className="topbar"><Link className="wordmark" to="/">cartora<span>.</span></Link><nav className="main-nav"><NavLink to="/">Shop</NavLink><NavLink to="/?sortBy=price&direction=asc">New in</NavLink><NavLink to="/orders">Orders</NavLink><NavLink to="/wishlist">Saved</NavLink>{user && <NavLink to="/addresses">Addresses</NavLink>}{user?.role === 'ADMIN' && <><NavLink to="/admin/overview">Studio</NavLink><NavLink to="/admin">Products</NavLink><NavLink to="/admin/categories">Categories</NavLink><NavLink to="/admin/orders">Order desk</NavLink><NavLink to="/admin/customers">Customers</NavLink><NavLink to="/admin/payments">Payments</NavLink><NavLink to="/admin/coupons">Coupons</NavLink></>}</nav>
      <div className="nav-actions">{user ? <><Link className="icon-link" title="Account" to="/profile"><UserRound size={19} /><span className="nav-label">Account</span></Link><button className="icon-link" title="Sign out" onClick={signOut}><LogOut size={18} /><span className="nav-label">Sign out</span></button></> : <Link className="icon-link" to="/login"><UserRound size={19} /><span className="nav-label">Sign in</span></Link>}<Link className="bag-link" to="/cart" aria-label={`Cart, ${count} items`}><ShoppingBag size={19} /><span>{count}</span></Link></div>
    </header><Outlet /><footer><Link className="wordmark" to="/">cartora<span>.</span></Link><span>Made for the things you keep.</span><span>Customer care · Shipping · Returns</span></footer></div>
}

function Browse() {
  const { user } = useApp()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = useState<ProductPage | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [recentProducts, setRecentProducts] = useState<Product[]>([])
  const [error, setError] = useState('')
  const search = params.get('search') ?? '', categoryId = params.get('categoryId') ?? ''
  const minPrice = params.get('minPrice') ?? '', maxPrice = params.get('maxPrice') ?? ''
  const sortBy = params.get('sortBy') ?? 'createdAt', direction = params.get('direction') ?? 'desc'
  const pageNumber = Number(params.get('page') ?? 0)
  useEffect(() => { getCategories().then(setCategories).catch(() => setCategories([])) }, [])
  useEffect(() => { getProducts({ search: search || undefined, categoryId: categoryId || undefined, sortBy, direction, page: pageNumber, size: 12 }).then(setPage).catch((cause) => setError(getErrorMessage(cause))) }, [search, categoryId, sortBy, direction, pageNumber])
  useEffect(() => {
    let active = true
    getRecentlyViewedProducts(Boolean(user))
      .then((products) => { if (active) setRecentProducts(products) })
      .catch(() => { if (active) setRecentProducts([]) })
    return () => { active = false }
  }, [user])
  const update = (key: string, value: string) => { const next = new URLSearchParams(params); if (value) next.set(key, value); else next.delete(key); next.delete('page'); setParams(next) }
  return <main><section className="hero-banner"><div className="hero-copy"><span className="eyebrow">OBJECTS WITH INTENTION</span><h1>Good things,<br /><em>kept close.</em></h1><p>Useful pieces, considered details, and a little joy in the everyday.</p><a className="primary-button" href="#catalog">Explore the collection <ArrowRight size={17} /></a></div><div className="hero-note"><span>01 / EDIT</span><b>Less, but<br />better.</b><small>Our current selection</small></div></section>
    <section className="catalog" id="catalog"><div className="section-heading"><div><span className="eyebrow">THE CARTORA EDIT</span><h2>Find your next favorite.</h2></div><span className="result-count">{page?.totalElements ?? '...'} pieces</span></div>
      <div className="toolbar"><DebouncedSearch key={search} value={search} /><select aria-label="Filter by category" value={categoryId} onChange={(event) => update('categoryId', event.target.value)}><option value="">All categories</option>{categories.map((category) => <option value={category.categoryId} key={category.categoryId}>{category.name}</option>)}</select><label className="price-filter">From<input aria-label="Minimum price" type="number" min="0" step="0.01" value={minPrice} onChange={(event) => update('minPrice', event.target.value)} placeholder="Min ₹" /></label><label className="price-filter">To<input aria-label="Maximum price" type="number" min="0" step="0.01" value={maxPrice} onChange={(event) => update('maxPrice', event.target.value)} placeholder="Max ₹" /></label><label className="sort-box"><ArrowDownUp size={16} /><select aria-label="Sort products" value={`${sortBy}:${direction}`} onChange={(event) => { const [field, order] = event.target.value.split(':'); update('sortBy', field); update('direction', order) }}><option value="createdAt:desc">Recently added</option><option value="price:asc">Price: low to high</option><option value="price:desc">Price: high to low</option><option value="name:asc">Name: A to Z</option></select></label></div>
      {error && <p className="notice error">{error}</p>}{page?.content.length ? <div className="product-grid">{page.content.map((product, index) => <ProductCard key={product.id} product={product} index={index} />)}</div> : <div className="empty-state"><ShoppingBag size={28} /><h3>{page ? 'No pieces found' : error ? 'The collection is unavailable' : 'Loading the collection'}</h3><p>{page ? 'Try another search or category.' : error ? 'The Cartora API could not be reached. Please try again shortly.' : 'Connecting to the Cartora collection.'}</p></div>}
      {page && page.totalPages > 1 && <div className="pagination"><button disabled={page.first} onClick={() => update('page', String(pageNumber - 1))}><ChevronLeft size={17} /> Previous</button><span>Page {page.page + 1} of {page.totalPages}</span><button disabled={page.last} onClick={() => update('page', String(pageNumber + 1))}>Next <ChevronRight size={17} /></button></div>}</section>{recentProducts.length > 0 && <section className="recent-section"><div className="section-heading"><div><span className="eyebrow">A LITTLE LOOK BACK</span><h2>Recently viewed.</h2></div></div><div className="product-grid">{recentProducts.map((recentProduct, index) => <ProductCard key={recentProduct.id} product={recentProduct} index={index} />)}</div></section>}</main>
}

function DebouncedSearch({ value }: { value: string }) {
  const [params, setParams] = useSearchParams()
  const [text, setText] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (text.trim() === value) return
      const next = new URLSearchParams(params)
      if (text.trim()) next.set('search', text.trim())
      else next.delete('search')
      next.delete('page')
      setParams(next)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [text, value, params, setParams])
  return <label className="search-box"><Search size={17} /><input value={text} onChange={(event) => setText(event.target.value)} placeholder="Search the collection" /></label>
}

const productPhotos = ['photo-1494438639946-1ebd1d20bf85', 'photo-1503602642458-232111445657', 'photo-1523275335684-37898b6baf30', 'photo-1526170375885-4d8ecf77b99f']
function ProductCard({ product, index }: { product: Product; index: number }) {
  const { user, refreshCart } = useApp(); const [message, setMessage] = useState('')
  const location = useLocation(), navigate = useNavigate()
  const requestSignIn = () => navigate('/login', { state: { from: location } })
  const add = async () => { if (!user) { requestSignIn(); return }; try { await addCartItem(product.id); await refreshCart(); setMessage('Added') } catch (error) { setMessage(getErrorMessage(error)) } }
  const save = async () => { if (!user) { requestSignIn(); return }; try { await addWishlistItem(product.id); setMessage('Saved') } catch (error) { setMessage(getErrorMessage(error)) } }
  return <article className="product-card"><Link to={`/product/${product.id}`} className="product-image"><img src={product.imageUrl || `https://images.unsplash.com/${productPhotos[index % 4]}?auto=format&fit=crop&w=800&q=80`} alt={product.name} /><span className="image-index">0{index % 4 + 1}</span></Link><button className="wishlist-toggle" title="Save to wishlist" aria-label={`Save ${product.name} to wishlist`} onClick={() => void save()}><Heart size={17} /></button><div className="product-meta"><Link to={`/product/${product.id}`}><h3>{product.name}</h3></Link><span>{formatPrice(Number(product.price))}</span></div><div className="product-sub"><span>{product.stockQuantity > 0 ? 'In stock' : 'Sold out'}</span><button disabled={!product.stockQuantity} onClick={add}>{message || 'Add to bag'} <ArrowRight size={14} /></button></div></article>
}

function ProductDetails() {
  const { id } = useParams(), { user, refreshCart } = useApp()
  const location = useLocation(), navigate = useNavigate()
  const [product, setProduct] = useState<Product | null>(null), [message, setMessage] = useState('')
  const [reviews, setReviews] = useState<ProductReview[]>([]), [rating, setRating] = useState<ProductRatingSummary | null>(null), [reviewMessage, setReviewMessage] = useState('')
  const loadReviews = async (productId: number) => {
    const [reviewList, ratingSummary] = await Promise.all([getProductReviews(productId), getProductRating(productId)])
    startTransition(() => {
      setReviews(reviewList)
      setRating(ratingSummary)
    })
  }
  useEffect(() => {
    if (!id) return
    const productId = Number(id)
    getProduct(productId).then((result) => { recordRecentlyViewed(productId); startTransition(() => setProduct(result)) }).catch((error) => setMessage(getErrorMessage(error)))
    loadReviews(productId).catch((error) => setReviewMessage(getErrorMessage(error)))
  }, [id])
  const add = async () => { if (!user) { navigate('/login', { state: { from: location } }); return }; if (product) try { await addCartItem(product.id); await refreshCart(); setMessage('Added to your bag') } catch (error) { setMessage(getErrorMessage(error)) } }
  const submitReview = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!product) return
    const form = new FormData(event.currentTarget)
    const request = { rating: Number(form.get('rating')), title: String(form.get('title')), comment: String(form.get('comment')) }
    const currentReview = reviews.find((review) => review.userId === user?.id)
    try {
      if (currentReview) await updateProductReview(product.id, request)
      else await createProductReview(product.id, request)
      setReviewMessage('Your review has been saved.')
      await loadReviews(product.id)
    } catch (error) { setReviewMessage(getErrorMessage(error)) }
  }
  const removeReview = async () => {
    if (!product || !window.confirm('Delete your review?')) return
    try { await deleteProductReview(product.id); setReviewMessage('Your review has been removed.'); await loadReviews(product.id) }
    catch (error) { setReviewMessage(getErrorMessage(error)) }
  }
  if (!product) return <main className="content-page"><p>{message || 'Loading product...'}</p></main>
  const ownReview = reviews.find((review) => review.userId === user?.id)
  return <main className="content-page"><section className="detail-layout"><div className="detail-photo"><img src={product.imageUrl || `https://images.unsplash.com/${productPhotos[product.id % 4]}?auto=format&fit=crop&w=1200&q=85`} alt={product.name} /></div><div className="detail-info"><span className="eyebrow">THE CARTORA EDIT</span><h1>{product.name}</h1><div className="rating-summary"><span>{rating ? Number(rating.averageRating).toFixed(2) : '—'}</span><Star size={15} fill="currentColor" /><small>{rating?.reviewCount ?? 0} reviews</small></div><strong>{formatPrice(Number(product.price))}</strong><p>{product.description}</p><p className="stock-line">{product.stockQuantity > 0 ? `${product.stockQuantity} available` : 'Currently unavailable'}</p><button className="primary-button" disabled={!product.stockQuantity} onClick={add}>Add to bag <ArrowRight size={17} /></button>{message && <p className="notice">{message}</p>}</div></section><section className="reviews-section"><div className="section-heading"><div><span className="eyebrow">CUSTOMER NOTES</span><h2>Reviews.</h2></div><span>{rating?.reviewCount ?? 0} reviews</span></div>{reviewMessage && <p className="notice">{reviewMessage}</p>}{reviews.length ? <div className="review-list">{reviews.map((review) => <article className="review-card" key={review.id}><div className="review-heading"><div>{Array.from({ length: 5 }, (_, index) => <Star key={index} size={14} fill={index < review.rating ? 'currentColor' : 'none'} />)}</div><time>{new Date(review.createdAt).toLocaleDateString()}</time></div><h3>{review.title}</h3><p>{review.comment}</p><small>{review.reviewerName}{review.userId === user?.id ? ' · Your review' : ''}</small></article>)}</div> : <p className="review-empty">No reviews yet.</p>}{user && <form className="review-form" onSubmit={submitReview}><h3>{ownReview ? 'Update your review' : 'Reviewed this purchase?'}</h3><p>Reviews are available to customers with a confirmed or fulfilled order.</p><div className="review-fields"><label>Rating<select name="rating" defaultValue={ownReview?.rating ?? 5}><option value="5">5 stars</option><option value="4">4 stars</option><option value="3">3 stars</option><option value="2">2 stars</option><option value="1">1 star</option></select></label><label>Title<input name="title" maxLength={80} defaultValue={ownReview?.title} required /></label></div><label>Review<textarea name="comment" maxLength={1000} defaultValue={ownReview?.comment} required /></label><div className="review-actions"><button className="primary-button">{ownReview ? 'Update review' : 'Submit review'} <ArrowRight size={16} /></button>{ownReview && <button className="text-button" type="button" onClick={() => void removeReview()}>Delete review</button>}</div></form>}</section></main>
}

function AuthPage({ register = false }: { register?: boolean }) {
  const { signIn } = useApp(), navigate = useNavigate(), location = useLocation(); const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); setError(''); const form = new FormData(event.currentTarget)
    try {
      if (register) {
        await api.post('/api/users/register', { name: form.get('name'), email: form.get('email'), password: form.get('password') })
        navigate('/login')
      } else {
        await signIn(String(form.get('email')), String(form.get('password')))
        const destination = (location.state as { from?: { pathname: string; search: string; hash: string } } | null)?.from
        const returnPath = destination?.pathname.startsWith('/') && !destination.pathname.startsWith('//')
          ? `${destination.pathname}${destination.search}${destination.hash}`
          : '/'
        navigate(returnPath)
      }
    }
    catch (cause) { setError(getErrorMessage(cause)) }
  }
  return <main className="auth-page"><div className="auth-art"><span className="eyebrow">WELCOME TO CARTORA</span><h1>A more<br /><em>considered</em><br />everyday.</h1><span className="art-caption">EST. FOR DAILY LIFE / VOL. 01</span></div><form className="auth-form" onSubmit={submit}><span className="eyebrow">{register ? 'CREATE ACCOUNT' : 'YOUR ACCOUNT'}</span><h2>{register ? 'Join the edit.' : 'Welcome back.'}</h2><p>{register ? 'A thoughtful collection, one account away.' : 'Sign in to pick up where you left off.'}</p>{register && <label>Your name<input name="name" required maxLength={30} autoComplete="name" /></label>}<label>Email address<input name="email" type="email" required autoComplete="email" /></label><label>Password<input name="password" type="password" required minLength={8} maxLength={72} pattern={register ? '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s])\\S+$' : undefined} autoComplete={register ? 'new-password' : 'current-password'} /></label>{register && <small>Use uppercase, lowercase, a number, and a special character.</small>}{!register && <Link className="text-link" to="/forgot-password">Forgot password?</Link>}{error && <p className="notice error">{error}</p>}<button className="primary-button">{register ? 'Create account' : 'Sign in'} <ArrowRight size={17} /></button><span className="form-switch">{register ? 'Already a member?' : 'New to Cartora?'} <Link to={register ? '/login' : '/register'}>{register ? 'Sign in' : 'Create an account'}</Link></span></form></main>
}

function ForgotPasswordPage() {
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setMessage(''); setError('')
    const form = new FormData(event.currentTarget)
    try {
      const { data } = await api.post('/api/auth/forgot-password', { email: form.get('email') })
      setMessage(data)
    } catch (cause) { setError(getErrorMessage(cause)) }
  }
  return <main className="content-page auth-reset"><span className="eyebrow">CARTORA / ACCOUNT</span><h1>Reset your password.</h1><p>Enter your account email. If it exists, we’ll send a one-time link that expires in 30 minutes.</p><form className="auth-form" onSubmit={submit}><label>Email address<input name="email" type="email" required maxLength={50} /></label>{message && <p className="notice">{message}</p>}{error && <p className="notice error">{error}</p>}<button className="primary-button">Send reset link <ArrowRight size={17} /></button><Link className="text-link" to="/login">Back to sign in</Link></form></main>
}

function ResetPasswordPage() {
  const [params] = useSearchParams()
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault(); setMessage(''); setError('')
    const form = new FormData(event.currentTarget)
    try {
      await api.put('/api/auth/reset-password', { token: params.get('token'), newPassword: form.get('newPassword') })
      setMessage('Password updated. You can sign in now.')
    } catch (cause) { setError(getErrorMessage(cause)) }
  }
  return <main className="content-page auth-reset"><span className="eyebrow">CARTORA / ACCOUNT</span><h1>Choose a new password.</h1><form className="auth-form" onSubmit={submit}><label>New password<input name="newPassword" type="password" required minLength={8} maxLength={72} pattern="^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S+$" autoComplete="new-password" /></label><small>Use uppercase, lowercase, a number, and a special character.</small>{message && <p className="notice">{message}</p>}{error && <p className="notice error">{error}</p>}<button className="primary-button">Update password <ArrowRight size={17} /></button><Link className="text-link" to="/login">Back to sign in</Link></form></main>
}

function CartPage() {
  const { user, cart, refreshCart } = useApp(); const [error, setError] = useState('')
  useEffect(() => { if (user) void refreshCart() }, [user, refreshCart])
  if (!user) return <SignInPrompt />
  const change = async (id: number, quantity: number) => { try { await updateCartItem(id, quantity); await refreshCart() } catch (cause) { setError(getErrorMessage(cause)) } }
  const remove = async (id: number) => { try { await removeCartItem(id); await refreshCart() } catch (cause) { setError(getErrorMessage(cause)) } }
  const total = cart?.items.reduce((sum, item) => sum + item.subtotal, 0) ?? 0
  return <main className="content-page"><div className="section-heading"><div><span className="eyebrow">YOUR SELECTION</span><h1>Your bag.</h1></div>{cart?.items.length ? <button className="text-button" onClick={async () => { await clearCart(); await refreshCart() }}>Clear bag</button> : null}</div>{error && <p className="notice error">{error}</p>}{!cart?.items.length ? <div className="empty-state"><ShoppingBag size={28} /><h3>Your bag is taking a breather.</h3><Link to="/" className="text-link">Continue shopping <ArrowRight size={15} /></Link></div> : <div className="cart-layout"><div className="cart-lines">{cart.items.map((item) => <div className="cart-line" key={item.cartItemId}><div className="cart-swatch"><ShoppingBag size={21} /></div><div className="cart-line-name"><h3>{item.productName}</h3><span>{formatPrice(Number(item.price))}</span></div><div className="quantity"><button aria-label="Decrease quantity" onClick={() => void change(item.cartItemId, item.quantity - 1)}>−</button><span>{item.quantity}</span><button aria-label="Increase quantity" onClick={() => void change(item.cartItemId, item.quantity + 1)}>+</button></div><strong>{formatPrice(Number(item.subtotal))}</strong><button className="text-button" onClick={() => void remove(item.cartItemId)}>Remove</button></div>)}</div><aside className="summary"><span>ORDER SUMMARY</span><div><span>Subtotal</span><strong>{formatPrice(total)}</strong></div><div><span>Shipping</span><span>Calculated at checkout</span></div><Link className="primary-button" to="/checkout">Continue to checkout <ArrowRight size={17} /></Link></aside></div>}</main>
}

type AddressForm = { recipientName: string; houseNumberOrBuildingNumber: string; localityOrLandmark: string; city: string; state: string; postalCode: string; country: string }
function CheckoutPage() {
  const { user, refreshCart } = useApp()
  const [error, setError] = useState('')
  const [done, setDone] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [savedAddresses, setSavedAddresses] = useState<SavedAddress[]>([])
  const [selectedAddressId, setSelectedAddressId] = useState('')
  useEffect(() => {
    if (!user) return
    api.get<SavedAddress[]>('/api/addresses').then(({ data }) => {
      setSavedAddresses(data)
      const preferred = data.find((address) => address.defaultAddress) ?? data[0]
      if (preferred) setSelectedAddressId(String(preferred.id))
    }).catch(() => setSavedAddresses([]))
  }, [user])
  const selectedAddress = savedAddresses.find((address) => String(address.id) === selectedAddressId)
  if (!user) return <SignInPrompt />
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setError('')
    setDone('')
    let awaitingGateway = false
    const form = new FormData(event.currentTarget)
    const shippingAddress: AddressForm = {
      recipientName: String(form.get('recipientName')),
      houseNumberOrBuildingNumber: String(form.get('houseNumberOrBuildingNumber')),
      localityOrLandmark: String(form.get('localityOrLandmark') ?? ''),
      city: String(form.get('city')),
      state: String(form.get('state')),
      postalCode: String(form.get('postalCode')),
      country: String(form.get('country')),
    }
    const couponCode = String(form.get('couponCode') ?? '').trim() || null
    try {
      await api.post('/api/checkout', { shippingAddress, couponCode })
      const { data: provider } = await api.get<{ provider: string }>('/api/payments/provider')
      const Razorpay = provider.provider === 'RAZORPAY' ? await loadRazorpayCheckout() : null
      const order = await createOrder(shippingAddress, couponCode)

      if (Razorpay) {
        const { data: gatewayOrder } = await api.post<{ razorpayOrderId: string; keyId: string; amount: number; currency: string }>(
          '/api/payments/gateway-order', { orderId: order.orderId },
        )
        const checkout = new Razorpay({
          key: gatewayOrder.keyId,
          amount: gatewayOrder.amount,
          currency: gatewayOrder.currency,
          name: 'Cartora',
          description: `Order ${order.orderId}`,
          order_id: gatewayOrder.razorpayOrderId,
          handler: (result) => {
            void (async () => {
              try {
                const { data: payment } = await api.post<{ status: string }>('/api/payments/verify', {
                  orderId: order.orderId,
                  razorpayOrderId: result.razorpay_order_id,
                  razorpayPaymentId: result.razorpay_payment_id,
                  razorpaySignature: result.razorpay_signature,
                })
                if (payment.status === 'SUCCESS') {
                  await refreshCart()
                  setDone(`Order ${order.orderId} confirmed. Total: ${formatPrice(Number(order.totalAmount))}${Number(order.discountAmount) > 0 ? ` · Saved ${formatPrice(Number(order.discountAmount))}` : ''}`)
                } else setError('Payment could not be confirmed. Check your orders before retrying.')
              } catch (cause) { setError(getErrorMessage(cause)) }
              finally { setSubmitting(false) }
            })()
          },
          modal: { ondismiss: () => { setError('Payment window closed. You can retry or cancel from your orders.'); setSubmitting(false) } },
        })
        checkout.on('payment.failed', (response) => {
          setError(response.error?.description ?? 'Payment failed. You can retry from your orders.')
          setSubmitting(false)
        })
        awaitingGateway = true
        checkout.open()
        return
      }

      const { data: payment } = await api.post<{ status: string }>('/api/payments', {
        orderId: order.orderId,
        paymentMethod: form.get('paymentMethod'),
      })
      if (payment.status === 'SUCCESS') {
        await refreshCart()
        setDone(`Order ${order.orderId} confirmed. Total: ${formatPrice(Number(order.totalAmount))}${Number(order.discountAmount) > 0 ? ` · Saved ${formatPrice(Number(order.discountAmount))}` : ''}`)
      } else setDone(`Payment was not approved. Order ${order.orderId} remains pending; you can retry from your orders.`)
    } catch (cause) { setError(getErrorMessage(cause)) }
    finally { if (!awaitingGateway) setSubmitting(false) }
  }
  if (done) return <main className="content-page confirmation"><div className="confirm-mark"><Check size={26} /></div><span className="eyebrow">CARTORA / CHECKOUT</span><h1>{done.startsWith('Order') ? 'Thank you.' : 'Payment update.'}</h1><p>{done}</p><Link className="primary-button" to="/orders">View your orders <ArrowRight size={17} /></Link></main>
  return <main className="content-page checkout-page"><div>
    <span className="eyebrow">CARTORA / CHECKOUT</span><h1>Almost yours.</h1><p>Where should we send your selection?</p>
    <form key={selectedAddressId || 'manual'} className="checkout-form" onSubmit={submit}>
      {savedAddresses.length > 0 && <label>Delivery address<select value={selectedAddressId} onChange={(event) => setSelectedAddressId(event.target.value)}><option value="">Enter a new address</option>{savedAddresses.map((address) => <option key={address.id} value={address.id}>{address.recipientName} · {address.city}{address.defaultAddress ? ' (default)' : ''}</option>)}</select></label>}
      {!savedAddresses.length && <Link className="text-link" to="/addresses">Manage saved addresses <ArrowRight size={14} /></Link>}
      <label>Recipient name<input name="recipientName" defaultValue={selectedAddress?.recipientName} required maxLength={100} /></label>
      <label>House / building<input name="houseNumberOrBuildingNumber" defaultValue={selectedAddress?.houseNumberOrBuildingNumber} required maxLength={100} /></label>
      <label>Locality / landmark<input name="localityOrLandmark" defaultValue={selectedAddress?.localityOrLandmark} maxLength={150} /></label>
      <label>City<input name="city" defaultValue={selectedAddress?.city} required maxLength={100} /></label>
      <label>State<input name="state" defaultValue={selectedAddress?.state} required maxLength={100} /></label>
      <label>Postal code<input name="postalCode" defaultValue={selectedAddress?.postalCode} inputMode="numeric" pattern="[0-9]{6}" required /></label>
      <label>Country<input name="country" defaultValue={selectedAddress?.country ?? 'India'} required maxLength={100} /></label>
      <label>Coupon code<input name="couponCode" maxLength={30} pattern="[A-Za-z0-9_-]*" autoCapitalize="characters" /></label>
      <label>Payment method<select name="paymentMethod"><option value="UPI">UPI</option><option value="CARD">Card</option><option value="NET_BANKING">Net banking</option><option value="COD">Cash on delivery</option></select></label>
      {error && <p className="notice error">{error}</p>}
      <button className="primary-button" disabled={submitting}>{submitting ? 'Processing…' : 'Place order'} <ArrowRight size={17} /></button>
    </form>
  </div><aside className="checkout-aside"><span>SECURE PAYMENT</span><p>Checkout uses the configured payment provider. Payment verification and order totals are confirmed by the Cartora backend.</p></aside></main>
}

function OrdersPage() {
  const { user } = useApp(); const [orders, setOrders] = useState<Order[]>([]), [error, setError] = useState(''), [paymentMethod, setPaymentMethod] = useState('UPI')
  const load = () => getOrders().then(setOrders).catch((cause) => setError(getErrorMessage(cause)))
  useEffect(() => { if (user) void load() }, [user])
  if (!user) return <SignInPrompt />
  const retryPayment = async (orderId: number) => {
    setError('')
    try {
      const { data: provider } = await api.get<{ provider: string }>('/api/payments/provider')
      if (provider.provider === 'RAZORPAY') {
        if (!window.confirm(`Open secure payment for order ${orderId}?`)) return
        const Razorpay = await loadRazorpayCheckout()
        const { data: gatewayOrder } = await api.post<{ razorpayOrderId: string; keyId: string; amount: number; currency: string }>(
          '/api/payments/gateway-order', { orderId },
        )
        const checkout = new Razorpay({
          key: gatewayOrder.keyId, amount: gatewayOrder.amount, currency: gatewayOrder.currency,
          name: 'Cartora', description: `Order ${orderId}`, order_id: gatewayOrder.razorpayOrderId,
          handler: (result) => {
            void api.post<{ status: string }>('/api/payments/verify', {
              orderId, razorpayOrderId: result.razorpay_order_id,
              razorpayPaymentId: result.razorpay_payment_id, razorpaySignature: result.razorpay_signature,
            }).then(({ data }) => {
              setError(data.status === 'SUCCESS' ? `Payment for order ${orderId} succeeded.` : 'Payment could not be confirmed.')
              return load()
            }).catch((cause: unknown) => setError(getErrorMessage(cause)))
          },
          modal: { ondismiss: () => setError('Payment window closed. You can retry or cancel the order.') },
        })
        checkout.on('payment.failed', (response) => {
          setError(response.error?.description ?? 'Payment failed. You can retry or cancel the order.')
          void load()
        })
        checkout.open()
        return
      }
      const { data } = await api.post<{ status: string }>('/api/payments', { orderId, paymentMethod })
      setError(data.status === 'SUCCESS' ? `Payment for order ${orderId} succeeded.` : `Payment for order ${orderId} failed. You can try again.`)
      await load()
    }
    catch (cause) { setError(getErrorMessage(cause)) }
  }
  return <main className="content-page"><span className="eyebrow">YOUR CARTORA HISTORY</span><h1>Orders.</h1>{error && <p className="notice error">{error}</p>}{orders.length ? <div className="orders-list">{orders.map((order) => <article className="order-row" key={order.orderId}><div><span className="eyebrow">ORDER #{order.orderId}</span><h3>{order.orderItems.map((item) => `${item.productName} × ${item.quantity}`).join(', ')}</h3><small>{new Date(order.createdAt).toLocaleDateString()}</small></div><strong>{formatPrice(Number(order.totalAmount))}</strong><span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span><Link className="text-button" to={`/orders/${order.orderId}`}>Details</Link>{order.status === 'PENDING' && <div className="order-actions"><select aria-label={`Payment method for order ${order.orderId}`} value={paymentMethod} onChange={(event) => setPaymentMethod(event.target.value)}><option value="UPI">UPI</option><option value="CARD">Card</option><option value="NET_BANKING">Net banking</option><option value="COD">Cash on delivery</option></select><button className="text-button" onClick={() => void retryPayment(order.orderId)}>Retry payment</button><button className="text-button" onClick={async () => { try { await cancelOrder(order.orderId); await load() } catch (cause) { setError(getErrorMessage(cause)) } }}>Cancel</button></div>}</article>)}</div> : !error && <div className="empty-state"><h3>No orders, just possibilities.</h3><Link className="text-link" to="/">Browse the collection <ArrowRight size={15} /></Link></div>}</main>
}

function OrderDetailsPage() {
  const { id } = useParams()
  const { user } = useApp()
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { if (user && id) getOrder(Number(id)).then(setOrder).catch((cause) => setError(getErrorMessage(cause))) }, [user, id])
  if (!user) return <SignInPrompt />
  if (error) return <main className="content-page"><p className="notice error">{error}</p><Link className="text-link" to="/orders">Back to orders</Link></main>
  if (!order) return <main className="content-page"><p>Loading order...</p></main>
  return <main className="content-page"><Link className="text-link" to="/orders"><ChevronLeft size={15} /> All orders</Link><span className="eyebrow order-detail-label">ORDER #{order.orderId}</span><h1>Order details.</h1><p>Placed {new Date(order.createdAt).toLocaleString()}</p><span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>{order.shippingAddress && <section className="order-shipping"><h2>Shipping address</h2><p>{order.shippingAddress.recipientName}<br />{order.shippingAddress.houseNumberOrBuildingNumber}{order.shippingAddress.localityOrLandmark ? `, ${order.shippingAddress.localityOrLandmark}` : ''}<br />{order.shippingAddress.city}, {order.shippingAddress.state} {order.shippingAddress.postalCode}<br />{order.shippingAddress.country}</p></section>}<div className="order-detail-items">{order.orderItems.map((item) => <div className="order-detail-item" key={item.productId}><div><strong>{item.productName}</strong><span>{item.quantity} × {formatPrice(Number(item.price))}</span></div><strong>{formatPrice(Number(item.subtotal))}</strong></div>)}</div><div className="order-detail-total"><span>Total</span><strong>{formatPrice(Number(order.totalAmount))}</strong></div></main>
}

function ProfilePage() {
  const { user, updateUser } = useApp(); const [message, setMessage] = useState('')
  const [savedName, setSavedName] = useState(user?.name ?? '')
  if (!user) return <SignInPrompt />
  const save = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const form = new FormData(event.currentTarget); const name = String(form.get('name')).trim(); try { const { data } = await api.put<User>('/api/users/me', { name }); updateUser(data); setSavedName(data.name); setMessage('Profile updated.') } catch (error) { setMessage(getErrorMessage(error)) } }
  const changePassword = async (event: FormEvent<HTMLFormElement>) => { event.preventDefault(); const form = new FormData(event.currentTarget); try { await api.put('/api/users/me/password', { currentPassword: form.get('currentPassword'), newPassword: form.get('newPassword') }); event.currentTarget.reset(); setMessage('Password changed.') } catch (error) { setMessage(getErrorMessage(error)) } }
  return <main className="content-page profile-page"><span className="eyebrow">YOUR ACCOUNT</span><h1>Profile.</h1><div className="profile-sections"><form className="auth-form profile-form" onSubmit={save}><h2>Personal details</h2><label>Name<input name="name" value={savedName} onChange={(event) => setSavedName(event.target.value)} required maxLength={30} /></label><label>Email<input value={user.email} disabled /></label><span className="role-label">Account type <strong>{user.role}</strong></span><button className="primary-button">Save changes <Check size={16} /></button></form><form className="auth-form profile-form" onSubmit={changePassword}><h2>Change password</h2><label>Current password<input name="currentPassword" type="password" required autoComplete="current-password" /></label><label>New password<input name="newPassword" type="password" required minLength={8} maxLength={72} pattern="^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S+$" autoComplete="new-password" /></label><small>Use uppercase, lowercase, a number, and a special character.</small><button className="primary-button">Update password <Check size={16} /></button></form></div>{message && <p className="notice">{message}</p>}</main>
}

type SavedAddress = AddressForm & { id: number; defaultAddress: boolean }
function WishlistPage() {
  const { user, refreshCart } = useApp()
  const [items, setItems] = useState<WishlistItem[]>([])
  const [error, setError] = useState('')
  useEffect(() => { if (user) getWishlist().then(setItems).catch((cause) => setError(getErrorMessage(cause))) }, [user])
  if (!user) return <SignInPrompt />
  const remove = async (productId: number) => { try { await removeWishlistItem(productId); setItems((current) => current.filter((item) => item.productId !== productId)) } catch (cause) { setError(getErrorMessage(cause)) } }
  const addToBag = async (productId: number) => { try { await addCartItem(productId); await refreshCart() } catch (cause) { setError(getErrorMessage(cause)) } }
  return <main className="content-page"><span className="eyebrow">YOUR CARTORA EDIT</span><h1>Saved for later.</h1>{error && <p className="notice error">{error}</p>}{items.length ? <div className="product-grid">{items.map((item, index) => <article className="product-card" key={item.productId}><div className="product-image"><img src={`https://images.unsplash.com/${productPhotos[index % 4]}?auto=format&fit=crop&w=800&q=80`} alt="" /></div><button className="wishlist-toggle" title="Remove from wishlist" aria-label={`Remove ${item.productName} from wishlist`} onClick={() => void remove(item.productId)}><Heart size={17} fill="currentColor" /></button><div className="product-meta"><h3>{item.productName}</h3><span>{formatPrice(Number(item.price))}</span></div><div className="product-sub"><span>{item.stockQuantity > 0 ? 'In stock' : 'Sold out'}</span><button disabled={!item.stockQuantity} onClick={() => void addToBag(item.productId)}>Add to bag <ArrowRight size={14} /></button></div></article>)}</div> : !error && <div className="empty-state"><Heart size={26} /><h3>Your saved edit starts here.</h3><Link className="text-link" to="/">Browse the collection <ArrowRight size={15} /></Link></div>}</main>
}

function AddressesPage() {
  const { user } = useApp()
  const [addresses, setAddresses] = useState<SavedAddress[]>([])
  const [editing, setEditing] = useState<SavedAddress | null>(null)
  const [message, setMessage] = useState('')
  const load = () => api.get<SavedAddress[]>('/api/addresses').then(({ data }) => setAddresses(data)).catch((error) => setMessage(getErrorMessage(error)))
  useEffect(() => { if (user) void load() }, [user])
  if (!user) return <SignInPrompt />
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = Object.fromEntries(new FormData(event.currentTarget)) as unknown as AddressForm
    try {
      if (editing) await api.put(`/api/addresses/${editing.id}`, form)
      else await api.post('/api/addresses', form)
      setEditing(null); setMessage('Address saved.'); await load()
    } catch (error) { setMessage(getErrorMessage(error)) }
  }
  const selectDefault = async (address: SavedAddress) => { try { await api.patch(`/api/addresses/${address.id}/default`); await load() } catch (error) { setMessage(getErrorMessage(error)) } }
  const remove = async (address: SavedAddress) => { if (!window.confirm('Delete this saved address?')) return; try { await api.delete(`/api/addresses/${address.id}`); await load() } catch (error) { setMessage(getErrorMessage(error)) } }
  return <main className="content-page address-page"><div className="section-heading"><div><span className="eyebrow">YOUR ACCOUNT / DELIVERY</span><h1>Saved addresses.</h1></div><button className="text-button" onClick={() => setEditing({ id: 0, defaultAddress: false, recipientName: '', houseNumberOrBuildingNumber: '', localityOrLandmark: '', city: '', state: '', postalCode: '', country: 'India' })}>Add address <ArrowRight size={15} /></button></div>{message && <p className="notice">{message}</p>}<div className="address-grid">{addresses.map((address) => <article className="address-card" key={address.id}><div className="address-card-top"><strong>{address.recipientName}</strong>{address.defaultAddress && <span>DEFAULT</span>}</div><p>{address.houseNumberOrBuildingNumber}{address.localityOrLandmark ? `, ${address.localityOrLandmark}` : ''}<br />{address.city}, {address.state} {address.postalCode}<br />{address.country}</p><div className="address-actions"><button className="text-button" onClick={() => setEditing(address)}>Edit</button>{!address.defaultAddress && <button className="text-button" onClick={() => void selectDefault(address)}>Make default</button>}<button className="text-button" onClick={() => void remove(address)}>Delete</button></div></article>)}{!addresses.length && <div className="empty-state"><h3>No saved addresses yet.</h3><p>Add one to keep checkout moving.</p></div>}</div>{editing && <form className="address-form" onSubmit={submit}><div className="section-heading"><h2>{editing.id ? 'Edit address' : 'New address'}</h2><button className="text-button" type="button" onClick={() => setEditing(null)}>Close</button></div><div className="checkout-form">{([['recipientName', 'Recipient name'], ['houseNumberOrBuildingNumber', 'House / building'], ['localityOrLandmark', 'Locality / landmark'], ['city', 'City'], ['state', 'State'], ['postalCode', 'Postal code'], ['country', 'Country']] as const).map(([field, label]) => <label key={field}>{label}<input name={field} required={field !== 'localityOrLandmark'} pattern={field === 'postalCode' ? '[0-9]{6}' : undefined} defaultValue={editing[field]} /></label>)}<button className="primary-button">Save address <Check size={16} /></button></div></form>}</main>
}

function AdminPage() {
  const { user } = useApp(); const [message, setMessage] = useState(''), [categories, setCategories] = useState<Category[]>([]), [products, setProducts] = useState<Product[]>([]), [editingProduct, setEditingProduct] = useState<Product | null>(null), [editingCategory, setEditingCategory] = useState<Category | null>(null)
  const fetchProducts = () => getProducts({ page: 0, size: 100, sortBy: 'createdAt', direction: 'desc' }).then((result) => result.content)
  const loadProducts = async () => { setProducts(await fetchProducts()) }
  useEffect(() => { if (user?.role === 'ADMIN') { getCategories().then(setCategories).catch(() => undefined); fetchProducts().then(setProducts).catch((error) => setMessage(getErrorMessage(error))) } }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1><p>This area is for Cartora administrators.</p></main>
  const submit = async (event: FormEvent<HTMLFormElement>, kind: 'product' | 'category') => { event.preventDefault(); const form = new FormData(event.currentTarget)
    try { if (kind === 'product') { const payload = { name: form.get('name'), description: form.get('description'), imageUrl: form.get('imageUrl') || null, sku: form.get('sku'), price: Number(form.get('price')), stockQuantity: Number(form.get('stockQuantity')), categoryId: Number(form.get('categoryId')) }; if (editingProduct) await api.put(`/api/products/${editingProduct.id}`, payload); else await api.post('/api/products', payload); setEditingProduct(null); await loadProducts() } else { const payload = { name: form.get('name'), description: form.get('description') }; if (editingCategory) await api.put(`/api/categories/${editingCategory.categoryId}`, payload); else await api.post('/api/categories', payload); setEditingCategory(null); setCategories(await getCategories()) }; setMessage(`${kind} saved.`); event.currentTarget.reset() } catch (error) { setMessage(getErrorMessage(error)) }
  }
  const deleteProduct = async (product: Product) => { if (!window.confirm(`Delete ${product.name}?`)) return; try { await api.delete(`/api/products/${product.id}`); setMessage('Product deleted.'); await loadProducts() } catch (error) { setMessage(getErrorMessage(error)) } }
  const uploadProductImage = async (product: Product, file?: File) => {
    if (!file) return
    const form = new FormData()
    form.append('file', file)
    try { await api.post(`/api/products/${product.id}/image`, form); setMessage(`Image updated for ${product.name}.`); await loadProducts() }
    catch (error) { setMessage(getErrorMessage(error)) }
  }
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Manage the edit.</h1><p>Catalog controls connected to your Cartora inventory.</p><div className="admin-grid"><form key={editingProduct?.id ?? 'new-product'} className="admin-form" onSubmit={(event) => void submit(event, 'product')}><h2>{editingProduct ? 'Edit product' : 'Add a product'}</h2><label>Name<input name="name" defaultValue={editingProduct?.name} required /></label><label>Description<textarea name="description" defaultValue={editingProduct?.description} required /></label><label>Image URL<input name="imageUrl" type="url" maxLength={2048} defaultValue={editingProduct?.imageUrl ?? ''} placeholder="https://..." /></label><label>SKU<input name="sku" defaultValue={editingProduct?.sku} required /></label><div className="form-pair"><label>Price<input name="price" type="number" min="0.01" step="0.01" defaultValue={editingProduct?.price} required /></label><label>Stock<input name="stockQuantity" type="number" min="0" defaultValue={editingProduct?.stockQuantity} required /></label></div><label>Category<select name="categoryId" defaultValue={editingProduct?.categoryId ?? categories[0]?.categoryId} required>{categories.map((category) => <option key={category.categoryId} value={category.categoryId}>{category.name}</option>)}</select></label><button className="primary-button">{editingProduct ? 'Save product' : 'Create product'} <ArrowRight size={16} /></button>{editingProduct && <button className="text-button" type="button" onClick={() => setEditingProduct(null)}>Cancel editing</button>}</form><form className="admin-form" onSubmit={(event) => void submit(event, 'category')}><h2>Add a category</h2><label>Name<input name="name" required maxLength={30} /></label><label>Description<textarea name="description" maxLength={200} /></label><button className="primary-button">Create category <ArrowRight size={16} /></button></form></div><section className="inventory-section"><div className="section-heading"><h2>Inventory</h2><span>{products.length} products</span></div>{products.length ? <div className="inventory-table-wrap"><table className="inventory-table"><thead><tr><th>Product</th><th>SKU</th><th>Price</th><th>Stock</th><th>Actions</th></tr></thead><tbody>{products.map((product) => <tr key={product.id}><td>{product.name}</td><td>{product.sku}</td><td>{formatPrice(Number(product.price))}</td><td>{product.stockQuantity}</td><td><button className="text-button" onClick={() => setEditingProduct(product)}>Edit</button><label className="text-button upload-image-button">Image<input type="file" accept="image/jpeg,image/png,image/webp" aria-label={`Upload image for ${product.name}`} onChange={(event) => { void uploadProductImage(product, event.currentTarget.files?.[0]); event.currentTarget.value = '' }} /></label><button className="text-button" onClick={() => void deleteProduct(product)}>Delete</button></td></tr>)}</tbody></table></div> : <p>No products to manage.</p>}</section>{message && <p className="notice">{message}</p>}</main>
}

function AdminCouponsPage() {
  const { user } = useApp()
  const [coupons, setCoupons] = useState<Coupon[]>([])
  const [message, setMessage] = useState('')
  useEffect(() => {
    if (user?.role === 'ADMIN') getAdminCoupons().then(setCoupons).catch((cause) => setMessage(getErrorMessage(cause)))
  }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1></main>
  const create = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    try {
      await createAdminCoupon({
        code: String(form.get('code')),
        discountType: String(form.get('discountType')) as Coupon['discountType'],
        discountValue: Number(form.get('discountValue')),
        minimumOrderAmount: Number(form.get('minimumOrderAmount')),
        maximumDiscountAmount: form.get('maximumDiscountAmount') ? Number(form.get('maximumDiscountAmount')) : null,
        validFrom: String(form.get('validFrom') || '') || null,
        validUntil: String(form.get('validUntil') || '') || null,
      })
      setMessage('Coupon created.')
      event.currentTarget.reset()
      setCoupons(await getAdminCoupons())
    } catch (cause) { setMessage(getErrorMessage(cause)) }
  }
  const toggle = async (coupon: Coupon) => {
    try {
      const updated = await setAdminCouponActive(coupon.id, !coupon.active)
      setCoupons((current) => current.map((item) => item.id === updated.id ? updated : item))
    } catch (cause) { setMessage(getErrorMessage(cause)) }
  }
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Coupons.</h1><p>Create fixed or percentage discounts with minimum spend, optional caps, and a validity window.</p><div className="admin-grid"><form className="admin-form" onSubmit={create}><h2>Create a coupon</h2><label>Code<input name="code" required minLength={3} maxLength={30} pattern="[A-Za-z0-9_-]{3,30}" /></label><label>Discount type<select name="discountType"><option value="PERCENTAGE">Percentage</option><option value="FIXED_AMOUNT">Fixed amount</option></select></label><div className="form-pair"><label>Discount value<input name="discountValue" type="number" min="0.01" step="0.01" required /></label><label>Minimum order<input name="minimumOrderAmount" type="number" min="0" step="0.01" defaultValue="0" required /></label></div><label>Maximum discount (optional)<input name="maximumDiscountAmount" type="number" min="0.01" step="0.01" /></label><div className="form-pair"><label>Valid from<input name="validFrom" type="datetime-local" /></label><label>Valid until<input name="validUntil" type="datetime-local" /></label></div><button className="primary-button">Create coupon <ArrowRight size={16} /></button></form><section className="coupon-list"><h2>Coupon codes</h2>{coupons.map((coupon) => <article key={coupon.id}><div><strong>{coupon.code}</strong><span>{coupon.discountType === 'PERCENTAGE' ? `${coupon.discountValue}%` : formatPrice(coupon.discountValue)} off · min. {formatPrice(coupon.minimumOrderAmount)}</span></div><span className={`status ${coupon.active ? 'status-confirmed' : 'status-cancelled'}`}>{coupon.active ? 'ACTIVE' : 'INACTIVE'}</span><button className="text-button" onClick={() => void toggle(coupon)}>{coupon.active ? 'Deactivate' : 'Activate'}</button></article>)}</section></div>{message && <p className="notice">{message}</p>}</main>
}

function AdminOrdersPage() {
  const { user } = useApp()
  const [orders, setOrders] = useState<Order[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    if (user?.role === 'ADMIN') {
      api.get<Order[]>('/api/admin/orders').then(({ data }) => setOrders(data)).catch((cause) => setError(getErrorMessage(cause)))
    }
  }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1></main>
  const advance = async (order: Order, status: 'SHIPPED' | 'DELIVERED') => {
    try {
      const { data: updated } = await api.patch<Order>(`/api/admin/orders/${order.orderId}/status`, { status })
      setOrders((current) => current.map((item) => item.orderId === updated.orderId ? updated : item))
    } catch (cause) { setError(getErrorMessage(cause)) }
  }
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Order desk.</h1>{error && <p className="notice error">{error}</p>}{orders.length ? <div className="admin-orders">{orders.map((order) => <article key={order.orderId}><div><span className="eyebrow">ORDER #{order.orderId}</span><h2>{order.orderItems.map((item) => `${item.productName} × ${item.quantity}`).join(', ')}</h2><small>{new Date(order.createdAt).toLocaleString()}</small></div><strong>{formatPrice(Number(order.totalAmount))}</strong><span className={`status status-${order.status.toLowerCase()}`}>{order.status}</span>{order.status === 'CONFIRMED' && <button className="text-button" onClick={() => void advance(order, 'SHIPPED')}>Mark shipped <ArrowRight size={14} /></button>}{order.status === 'SHIPPED' && <button className="text-button" onClick={() => void advance(order, 'DELIVERED')}>Mark delivered <Check size={14} /></button>}</article>)}</div> : !error && <div className="empty-state"><h3>No orders to manage.</h3></div>}</main>
}

function AdminCategoriesPage() {
  const { user } = useApp()
  const [categories, setCategories] = useState<Category[]>([])
  const [editing, setEditing] = useState<Category | null>(null)
  const [message, setMessage] = useState('')
  const load = () => getCategories().then(setCategories).catch((error) => setMessage(getErrorMessage(error)))
  useEffect(() => { if (user?.role === 'ADMIN') void load() }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1></main>
  const save = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const payload = { name: form.get('name'), description: form.get('description') }
    try {
      if (editing) await api.put(`/api/categories/${editing.categoryId}`, payload)
      else await api.post('/api/categories', payload)
      setEditing(null)
      setMessage('Category saved.')
      await load()
    } catch (error) { setMessage(getErrorMessage(error)) }
  }
  const remove = async (category: Category) => {
    if (!window.confirm(`Delete ${category.name}?`)) return
    try { await api.delete(`/api/categories/${category.categoryId}`); setMessage('Category deleted.'); await load() }
    catch (error) { setMessage(getErrorMessage(error)) }
  }
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Category management.</h1><div className="admin-grid"><form key={editing?.categoryId ?? 'new-category'} className="admin-form" onSubmit={save}><h2>{editing ? 'Edit category' : 'Add a category'}</h2><label>Name<input name="name" defaultValue={editing?.name} required maxLength={30} /></label><label>Description<textarea name="description" defaultValue={editing?.description} maxLength={200} /></label><button className="primary-button">{editing ? 'Save category' : 'Create category'} <ArrowRight size={16} /></button>{editing && <button className="text-button" type="button" onClick={() => setEditing(null)}>Cancel editing</button>}</form><section className="category-list"><h2>Categories</h2>{categories.map((category) => <article key={category.categoryId}><div><strong>{category.name}</strong><span>{category.description}</span></div><button className="text-button" onClick={() => setEditing(category)}>Edit</button><button className="text-button" onClick={() => void remove(category)}>Delete</button></article>)}</section></div>{message && <p className="notice">{message}</p>}</main>
}

function SignInPrompt() { return <main className="content-page"><span className="eyebrow">CARTORA / ACCOUNT</span><h1>Sign in to continue.</h1><Link className="primary-button" to="/login">Sign in <ArrowRight size={17} /></Link></main> }

function AdminDashboardPage() {
  const { user } = useApp()
  const [summary, setSummary] = useState<AdminDashboard | null>(null)
  const [sales, setSales] = useState<SalesPoint[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    if (user?.role === 'ADMIN') Promise.all([
      api.get<AdminDashboard>('/api/admin/dashboard'),
      api.get<SalesPoint[]>('/api/admin/dashboard/sales', { params: { days: 14 } }),
    ]).then(([dashboard, report]) => { setSummary(dashboard.data); setSales(report.data) })
      .catch((cause) => setError(getErrorMessage(cause)))
  }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1></main>
  const maxRevenue = Math.max(1, ...sales.map((point) => Number(point.revenue)))
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Store overview.</h1><p>Sales and operations from your live Cartora data.</p>{error && <p className="notice error">{error}</p>}{summary ? <div className="dashboard-metrics"><article><span>Customers</span><strong>{summary.customerCount}</strong></article><article><span>Products</span><strong>{summary.productCount}</strong></article><article><span>Low stock (5 or fewer)</span><strong>{summary.lowStockProductCount}</strong></article><article><span>Orders awaiting payment</span><strong>{summary.pendingOrderCount}</strong></article><article><span>In fulfilment</span><strong>{summary.ordersInFulfilmentCount}</strong></article><article><span>Paid revenue</span><strong>{formatPrice(Number(summary.paidRevenue))}</strong></article></div> : !error && <p>Loading store metrics…</p>}<section className="dashboard-sales"><div className="section-heading"><div><span className="eyebrow">LAST 14 DAYS</span><h2>Daily sales.</h2></div></div>{sales.length ? <div className="sales-bars">{sales.map((point) => <div className="sales-bar-column" key={point.date} title={`${point.date}: ${formatPrice(Number(point.revenue))}`}><div className="sales-bar" style={{ height: `${Math.max(3, Number(point.revenue) / maxRevenue * 100)}%` }} /><small>{new Date(`${point.date}T00:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}</small></div>)}</div> : <p>No completed sales in this period yet.</p>}</section></main>
}

function AdminCustomersPage() {
  const { user } = useApp()
  const [customers, setCustomers] = useState<AdminCustomer[]>([])
  const [error, setError] = useState('')
  useEffect(() => { if (user?.role === 'ADMIN') api.get<AdminCustomer[]>('/api/admin/customers').then(({ data }) => setCustomers(data)).catch((cause) => setError(getErrorMessage(cause))) }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1></main>
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Customers.</h1>{error && <p className="notice error">{error}</p>}{customers.length ? <div className="inventory-table-wrap"><table className="inventory-table"><thead><tr><th>Name</th><th>Email</th><th>Role</th><th>Joined</th></tr></thead><tbody>{customers.map((customer) => <tr key={customer.id}><td>{customer.name}</td><td>{customer.email}</td><td>{customer.role}</td><td>{new Date(customer.createdAt).toLocaleDateString()}</td></tr>)}</tbody></table></div> : !error && <div className="empty-state"><h3>No customers yet.</h3></div>}</main>
}

function AdminPaymentsPage() {
  const { user } = useApp()
  const [payments, setPayments] = useState<PaymentRecord[]>([])
  const [error, setError] = useState('')
  const load = () => api.get<PaymentRecord[]>('/api/admin/payments').then(({ data }) => setPayments(data)).catch((cause) => setError(getErrorMessage(cause)))
  useEffect(() => { if (user?.role === 'ADMIN') void load() }, [user])
  if (!user) return <SignInPrompt />
  if (user.role !== 'ADMIN') return <main className="content-page"><h1>Access restricted.</h1></main>
  const refund = async (payment: PaymentRecord) => {
    if (!payment.providerPaymentId) return
    const reconciling = payment.status === 'REFUND_PENDING'
    const enteredAmount = window.prompt(reconciling
      ? `A refund for order ${payment.orderId} is awaiting confirmation. Leave blank to safely retry the same request.`
      : `Refund amount in INR for order ${payment.orderId}. Leave blank for the remaining amount.`)
    if (enteredAmount === null) return
    const amount = enteredAmount.trim() ? Number(enteredAmount) : undefined
    if (amount !== undefined && (!Number.isFinite(amount) || amount <= 0)) {
      setError('Enter a refund amount greater than zero, or leave it blank for the remaining amount.')
      return
    }
    try { const { data } = await api.post<{ status: string }>(`/api/admin/payments/${payment.paymentId}/refunds`, amount === undefined ? {} : { amount }); setError(data.status === 'REQUESTED' ? 'Refund request is awaiting provider confirmation. Retry safely from here to reconcile it.' : 'Refund submitted. The provider will report its final status.'); await load() }
    catch (cause) { setError(getErrorMessage(cause)) }
  }
  return <main className="content-page admin-page"><span className="eyebrow">CARTORA / STUDIO</span><h1>Payments.</h1><p>Provider transactions and refund actions.</p>{error && <p className="notice">{error}</p>}{payments.length ? <div className="inventory-table-wrap"><table className="inventory-table"><thead><tr><th>Order</th><th>Amount</th><th>Provider</th><th>Status</th><th>Transaction</th><th>Action</th></tr></thead><tbody>{payments.map((payment) => <tr key={payment.paymentId}><td>#{payment.orderId}</td><td>{formatPrice(Number(payment.amount))}</td><td>{payment.paymentMethod}</td><td>{payment.status}</td><td>{payment.providerPaymentId ?? payment.transactionId}</td><td>{['SUCCESS', 'PARTIALLY_REFUNDED', 'REFUND_PENDING'].includes(payment.status) && payment.providerPaymentId ? <button className="text-button" onClick={() => void refund(payment)}>{payment.status === 'REFUND_PENDING' ? 'Reconcile refund' : 'Refund'}</button> : '—'}</td></tr>)}</tbody></table></div> : !error && <div className="empty-state"><h3>No payments yet.</h3></div>}</main>
}
