import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Outlet, useLocation, useSearchParams } from 'react-router-dom'
import { ArrowDownUp, ArrowRight, ArrowUpRight, Check, ChevronLeft, ChevronRight, Eye, GitCompareArrows, Heart, LayoutGrid, List, LogOut, Menu, PackageCheck, Search, ShieldCheck, ShoppingBag, SlidersHorizontal, Sparkles, UserRound, X } from 'lucide-react'
import { formatPrice, getCategories, getErrorMessage, getProducts, getRecentlyViewedProducts, type Category, type Product, type ProductPage } from '../api'
import { useApp } from '../app-state'
import { ShoppingProvider } from './ShoppingProvider'
import { productImage, useShop } from './shop-state'
import './storefront.css'

const collectionImages = ['/images/lighting.jpg', '/images/living.jpg', '/images/everyday.jpg', '/images/workspace.jpg']

export function StoreLayout() {
  return <ShoppingProvider><StoreChrome /></ShoppingProvider>
}

function StoreChrome() {
  const { user, cart, signOut } = useApp()
  const { openBag, openHelp, savedIds } = useShop()
  const [menuOpen, setMenuOpen] = useState(false)
  const location = useLocation()
  const previousPath = useRef(location.pathname)
  const count = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0
  useEffect(() => {
    setMenuOpen(false)
    const changedPath = previousPath.current !== location.pathname
    previousPath.current = location.pathname
    if (!location.hash && changedPath) window.scrollTo({ top: 0, behavior: 'instant' })
    else if (location.hash) {
      const timer = window.setTimeout(() => document.getElementById(location.hash.slice(1))?.scrollIntoView(), 100)
      return () => window.clearTimeout(timer)
    }
  }, [location.pathname, location.hash])
  useEffect(() => {
    if (!menuOpen) return
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') setMenuOpen(false) }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [menuOpen])
  return <div className="app-shell storefront-shell">
    <a className="skip-link" href="#main-content">Skip to content</a>
    <div className="announcement"><span><Sparkles size={12} /> Little things. A better everyday.</span><button onClick={openHelp}>Meet your next favorite <ArrowUpRight size={13} /></button></div>
    <header className="topbar">
      <Link className="wordmark" to="/" aria-label="Cartora home">cartora<span>✳</span></Link>
      <nav className="main-nav" aria-label="Main navigation"><Link className={location.pathname === '/' && !location.search ? 'active' : ''} to="/">The collection</Link><Link to="/?sortBy=createdAt&direction=desc#catalog">New arrivals <span className="nav-dot" /></Link><Link to="/#collections">Shop by mood</Link><button onClick={openHelp}>Our little guide</button></nav>
      <div className="nav-actions"><Link className="header-icon search-shortcut" to="/#catalog" aria-label="Search the collection"><Search size={20} /></Link><Link className="header-icon saved-shortcut" to="/wishlist" aria-label={`Saved pieces${savedIds.length ? `, ${savedIds.length} items` : ''}`}><Heart size={20} />{savedIds.length > 0 && <span className="icon-count">{savedIds.length}</span>}</Link><Link className="header-icon" to={user ? '/profile' : '/login'} aria-label={user ? 'Your account' : 'Sign in'}><UserRound size={20} /></Link><button className="bag-link" onClick={openBag} aria-label={`Open bag, ${count} items`}><ShoppingBag size={20} /><span>{count}</span></button><button className="mobile-menu-button header-icon" aria-label={menuOpen ? 'Close navigation' : 'Open navigation'} aria-expanded={menuOpen} aria-controls="mobile-navigation" onClick={() => setMenuOpen(!menuOpen)}>{menuOpen ? <X size={22} /> : <Menu size={22} />}</button></div>
      {menuOpen && <nav id="mobile-navigation" className="mobile-navigation" aria-label="Mobile navigation" onClick={() => setMenuOpen(false)}><Link to="/">The collection <ArrowUpRight size={17} /></Link><Link to="/?sortBy=createdAt&direction=desc#catalog">New arrivals <ArrowUpRight size={17} /></Link><Link to="/wishlist">Saved pieces <Heart size={17} /></Link><Link to="/orders">Your orders <PackageCheck size={17} /></Link><button onClick={openHelp}>Shopping guide <ArrowUpRight size={17} /></button></nav>}
    </header>
    {user && <div className="account-navigation"><span>Hello, {user.name.split(' ')[0]}.</span><Link to="/orders">Your orders</Link><Link to="/addresses">Addresses</Link>{user.role === 'ADMIN' && <details className="studio-menu"><summary>Manage store</summary><div>{[['/admin/overview', 'Overview'], ['/admin', 'Products'], ['/admin/categories', 'Categories'], ['/admin/orders', 'Orders'], ['/admin/customers', 'Customers'], ['/admin/payments', 'Payments'], ['/admin/coupons', 'Coupons']].map(([path, label]) => <Link key={path} to={path}>{label}</Link>)}</div></details>}<button onClick={signOut}><LogOut size={13} /> Sign out</button></div>}
    <div id="main-content" tabIndex={-1}><Outlet /></div>
    <footer className="store-footer"><div className="footer-main"><div><Link className="wordmark" to="/">cartora<span>✳</span></Link><p>For the corners you love.<br />And the everyday in between.</p></div><div className="footer-links"><span>TAKE A LOOK AROUND</span><Link to="/#catalog">The collection</Link><Link to="/wishlist">Your saved pieces</Link><Link to="/orders">Your orders</Link></div><div className="footer-links"><span>MAKE YOURSELF AT HOME</span><button onClick={openHelp}>Shopping guide</button><Link to={user ? '/profile' : '/register'}>{user ? 'Your account' : 'Join Cartora'}</Link><Link to="/addresses">Your addresses</Link></div><div className="footer-note"><span className="footer-symbol">✳</span><p>A little less ordinary.<br />A little more you.</p></div></div><div className="footer-bottom"><span>© {new Date().getFullYear()} Cartora. Good things, kept close.</span><span>Thoughtfully picked. Happily kept.</span></div></footer>
  </div>
}

export function Browse() {
  const { user } = useApp()
  const [params, setParams] = useSearchParams()
  const [page, setPage] = useState<ProductPage | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [recentProducts, setRecentProducts] = useState<Product[]>([])
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [retry, setRetry] = useState(0)
  const [view, setView] = useState<'grid' | 'list'>(() => { try { return localStorage.getItem('cartora.view') === 'list' ? 'list' : 'grid' } catch { return 'grid' } })
  const search = params.get('search') ?? '', categoryId = params.get('categoryId') ?? ''
  const minPrice = params.get('minPrice') ?? '', maxPrice = params.get('maxPrice') ?? ''
  const sortBy = ['createdAt', 'price', 'name'].includes(params.get('sortBy') ?? '') ? params.get('sortBy')! : 'createdAt'
  const direction = params.get('direction') === 'asc' ? 'asc' : 'desc'
  const requestedPage = Number(params.get('page') ?? 0)
  const pageNumber = Number.isInteger(requestedPage) && requestedPage >= 0 ? requestedPage : 0
  const activeCategory = categories.find((category) => String(category.categoryId) === categoryId)
  const hasFilters = Boolean(search || categoryId || minPrice || maxPrice)
  const update = (values: Record<string, string>, resetPage = true) => {
    // BrowserRouter updates the URL before its transition commits. Read that
    // latest URL so rapid filter changes do not merge against an older render.
    const next = new URLSearchParams(window.location.search)
    for (const [key, value] of Object.entries(values)) { if (value) next.set(key, value); else next.delete(key) }
    if (resetPage) next.delete('page')
    setParams(next, { preventScrollReset: true })
  }
  useEffect(() => { getCategories().then(setCategories).catch(() => setCategories([])) }, [])
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    getProducts({ search: search || undefined, categoryId: categoryId || undefined, minPrice: minPrice || undefined, maxPrice: maxPrice || undefined, sortBy, direction, page: pageNumber, size: 12 })
      .then((result) => { if (active) setPage(result) })
      .catch((cause) => { if (active) { setError(getErrorMessage(cause)); setPage(null) } })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [search, categoryId, minPrice, maxPrice, sortBy, direction, pageNumber, retry])
  useEffect(() => {
    let active = true
    getRecentlyViewedProducts(Boolean(user)).then((products) => { if (active) setRecentProducts(products) }).catch(() => undefined)
    return () => { active = false }
  }, [user])
  const changeView = (next: 'grid' | 'list') => { setView(next); try { localStorage.setItem('cartora.view', next) } catch { /* View still works without browser storage. */ } }
  const collectionCards = categories.length ? categories.slice(0, 4).map((category, index) => ({ title: category.name, description: category.description || 'Find your kind of everyday.', url: `/?categoryId=${category.categoryId}#catalog`, image: collectionImages[index % collectionImages.length] })) : [
    { title: 'Set the mood', description: 'A softer kind of glow.', url: '/?search=lamp#catalog', image: collectionImages[0] },
    { title: 'Stay a little longer', description: 'For your favorite corners.', url: '/#catalog', image: collectionImages[1] },
    { title: 'Out of the ordinary', description: 'Little things, big personality.', url: '/#catalog', image: collectionImages[2] },
    { title: 'Make room for focus', description: 'Everyday essentials, reimagined.', url: '/#catalog', image: collectionImages[3] },
  ]
  return <main className="browse-page">
    <section className="editorial-hero"><div className="editorial-hero-copy"><span className="hero-kicker"><i /> THE EVERYDAY, REIMAGINED</span><h1>A little more<br /><em>extraordinary.</em></h1><p>Thoughtful finds for the way you live.<br />Good design. Small joys. All yours.</p><div className="hero-buttons"><a href="#catalog" className="primary-button">Find your next favorite <ArrowUpRight size={18} /></a><a href="#collections" className="hero-secondary">Explore the edit <ArrowRight size={16} /></a></div><div className="hero-footnote"><span className="little-flower">✳</span><span>Less scrolling. More discovering.<br /><strong>Something good is just around the corner.</strong></span></div></div><div className="editorial-hero-image"><img src="/images/living-room.jpg" alt="A warm, sunlit living room with natural textures and thoughtfully arranged furniture" fetchPriority="high" /><div className="hero-stamp"><Sparkles size={21} /><span>GOOD FINDS.<br />GREAT FEELING.</span></div><div className="hero-image-caption"><span><small>THE ART OF EVERYDAY</small>A space that feels like you.</span><a href="#collections" aria-label="Explore collections"><ArrowUpRight size={24} /></a></div></div><span className="hero-side-label">THE CARTORA EDIT / VOL. 02</span></section>
    <div className="shopping-benefits"><div><ShieldCheck size={23} strokeWidth={1.4} /><span><strong>Your account, your space</strong><small>A personal place for all your finds.</small></span></div><div><Heart size={23} strokeWidth={1.4} /><span><strong>Love it? Keep it close.</strong><small>Save your favorites for later.</small></span></div><div><GitCompareArrows size={23} strokeWidth={1.4} /><span><strong>A better kind of choice</strong><small>Compare the details, side by side.</small></span></div><div><PackageCheck size={23} strokeWidth={1.4} /><span><strong>From bag to doorstep</strong><small>Your orders, all in one place.</small></span></div></div>
    <section className="collections-section section-width" id="collections"><div className="section-heading"><div><span className="eyebrow">FIND YOUR KIND OF GOOD</span><h2>A mood for every moment.</h2></div><a href="#catalog" className="section-link">Shop everything <ArrowUpRight size={16} /></a></div><div className="collection-grid">{collectionCards.map((collection, index) => <Link className="collection-card" to={collection.url} key={collection.title}><img src={collection.image} alt="" loading="lazy" /><div><span className="collection-number">0{index + 1}</span><h3>{collection.title}</h3><p>{collection.description}</p><span className="collection-arrow"><ArrowUpRight size={22} /></span></div></Link>)}</div></section>
    <section className="catalog" id="catalog"><div className="section-heading"><div><span className="eyebrow">THOUGHTFULLY PICKED. HAPPILY KEPT.</span><h2>{search ? `A little something for “${search}”.` : activeCategory ? activeCategory.name : 'Meet your next favorites.'}</h2></div><span className="result-count" aria-live="polite">{loading ? 'Finding good things…' : `${page?.totalElements ?? 0} pieces to discover`}</span></div>
      <div className="category-pills" aria-label="Browse categories"><button className={!categoryId ? 'selected' : ''} aria-pressed={!categoryId} onClick={() => update({ categoryId: '' })}>All pieces</button>{categories.map((category) => <button key={category.categoryId} className={categoryId === String(category.categoryId) ? 'selected' : ''} aria-pressed={categoryId === String(category.categoryId)} onClick={() => update({ categoryId: String(category.categoryId) })}>{category.name}</button>)}</div>
      <div className="discovery-toolbar"><DebouncedSearch value={search} /><button className={`filter-toggle ${filtersOpen || minPrice || maxPrice ? 'is-active' : ''}`} aria-expanded={filtersOpen} aria-controls="price-filters" onClick={() => setFiltersOpen(!filtersOpen)}><SlidersHorizontal size={16} /> Filters {(minPrice || maxPrice) && <span className="filter-dot" />}</button><label className="sort-box"><ArrowDownUp size={15} /><select aria-label="Sort products" value={`${sortBy}:${direction}`} onChange={(event) => { const [field, order] = event.target.value.split(':'); update({ sortBy: field, direction: order }) }}><option value="createdAt:desc">Newest first</option><option value="price:asc">Price: low to high</option><option value="price:desc">Price: high to low</option><option value="name:asc">Name: A to Z</option></select></label><div className="view-switch" aria-label="Product layout"><button aria-label="Grid view" aria-pressed={view === 'grid'} onClick={() => changeView('grid')}><LayoutGrid size={17} /></button><button aria-label="List view" aria-pressed={view === 'list'} onClick={() => changeView('list')}><List size={18} /></button></div></div>
      {filtersOpen && <PriceFilters key={`${minPrice}:${maxPrice}`} minPrice={minPrice} maxPrice={maxPrice} apply={(minimum, maximum) => update({ minPrice: minimum, maxPrice: maximum })} />}
      {hasFilters && <div className="active-filters" aria-label="Active filters">{search && <button onClick={() => update({ search: '' })}>Search: {search} <X size={12} /></button>}{activeCategory && <button onClick={() => update({ categoryId: '' })}>{activeCategory.name} <X size={12} /></button>}{(minPrice || maxPrice) && <button onClick={() => update({ minPrice: '', maxPrice: '' })}>{minPrice ? formatPrice(Number(minPrice)) : 'Any price'} – {maxPrice ? formatPrice(Number(maxPrice)) : 'and up'} <X size={12} /></button>}<button className="reset-filters" onClick={() => update({ search: '', categoryId: '', minPrice: '', maxPrice: '' })}>Clear all</button></div>}
      <div aria-busy={loading} aria-label="Product results">{loading ? <div className="discovery-grid skeleton-grid" aria-label="Loading products">{Array.from({ length: 4 }, (_, index) => <div className="product-skeleton" key={index}><div /><span /><span /></div>)}</div> : error ? <div className="empty-state collection-empty" role="alert"><ShoppingBag size={32} strokeWidth={1} /><h3>A little pause in the collection.</h3><p>{error}</p><button className="primary-button" onClick={() => setRetry(retry + 1)}>Try again <ArrowRight size={16} /></button></div> : page?.content.length ? <div className={`discovery-grid ${view === 'list' ? 'discovery-list' : ''}`}>{page.content.map((product, index) => <ProductCard key={product.id} product={product} index={index} />)}</div> : <div className="empty-state collection-empty"><Search size={32} strokeWidth={1} /><h3>{hasFilters ? 'Your next favorite is still out there.' : 'Good things are on their way.'}</h3><p>{hasFilters ? 'Try a different search, category, or price range.' : 'The collection is empty for now. Come back to discover new pieces.'}</p>{hasFilters && <button className="primary-button" onClick={() => update({ search: '', categoryId: '', minPrice: '', maxPrice: '' })}>Explore all pieces <ArrowRight size={16} /></button>}</div>}</div>
      {page && page.totalPages > 1 && <nav className="pagination" aria-label="Product pages"><button disabled={page.first || loading} onClick={() => update({ page: String(pageNumber - 1) }, false)}><ChevronLeft size={17} /> Previous</button><span>Page <strong>{page.page + 1}</strong> of {page.totalPages}</span><button disabled={page.last || loading} onClick={() => update({ page: String(pageNumber + 1) }, false)}>Next <ChevronRight size={17} /></button></nav>}
    </section>
    <section className="editorial-story section-width"><div className="story-image"><img src="/images/living.jpg" alt="Natural wood furniture with a simple, considered design" loading="lazy" /><span>GOOD DESIGN.<br />EVERY SINGLE DAY.</span></div><div className="story-copy"><span className="eyebrow">A LITTLE INTENTION GOES A LONG WAY</span><h2>Not just things.<br /><em>Your kind of things.</em></h2><p>The lamp that makes a corner feel like home. The piece you reach for every morning. We believe the best finds are the ones that become part of your day.</p><a href="#catalog" className="primary-button">Find something that feels like you <ArrowUpRight size={17} /></a></div></section>
    {recentProducts.length > 0 && <section className="recent-section"><div className="section-heading"><div><span className="eyebrow">STILL ON YOUR MIND?</span><h2>A little look back.</h2></div><span className="section-link">Recently viewed</span></div><div className="discovery-grid">{recentProducts.slice(0, 4).map((product, index) => <ProductCard key={product.id} product={product} index={index} />)}</div></section>}
    <section className="join-strip section-width"><span className="join-symbol">✳</span><div><span className="eyebrow">MAKE YOURSELF AT HOME</span><h2>Your favorites deserve a place.</h2><p>{user ? 'Your saved collection is waiting. Pick up where you left off.' : 'Save the pieces you love, track your orders, and find a little more you.'}</p></div><Link to={user ? '/wishlist' : '/register'} className="primary-button">{user ? 'See your saved pieces' : 'Join the good side'} <ArrowUpRight size={17} /></Link></section>
  </main>
}

function DebouncedSearch({ value }: { value: string }) {
  const [, setParams] = useSearchParams()
  const [text, setText] = useState(value)
  useEffect(() => { setText(value) }, [value])
  useEffect(() => {
    const timer = window.setTimeout(() => {
      if (text.trim() === value) return
      const next = new URLSearchParams(window.location.search)
      if (text.trim()) next.set('search', text.trim())
      else next.delete('search')
      next.delete('page')
      setParams(next, { replace: true, preventScrollReset: true })
    }, 350)
    return () => window.clearTimeout(timer)
  }, [text, value, setParams])
  return <label className="search-box"><Search size={18} /><input aria-label="Search products" value={text} onChange={(event) => setText(event.target.value)} placeholder="What are you looking for?" />{text && <button aria-label="Clear search" onClick={() => setText('')}><X size={15} /></button>}</label>
}

function PriceFilters({ minPrice, maxPrice, apply }: { minPrice: string; maxPrice: string; apply: (minimum: string, maximum: string) => void }) {
  const [error, setError] = useState('')
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget), minimum = String(form.get('minimum') ?? ''), maximum = String(form.get('maximum') ?? '')
    if (minimum && maximum && Number(minimum) > Number(maximum)) { setError('Minimum price must be less than or equal to maximum price.'); return }
    setError(''); apply(minimum, maximum)
  }
  return <div className="price-panel" id="price-filters"><div><strong>A little budget, a lot of possibility.</strong><span>Choose a price range</span><div className="budget-presets"><button onClick={() => apply('', '1500')}>Under ₹1,500</button><button onClick={() => apply('1500', '3000')}>₹1,500 – ₹3,000</button><button onClick={() => apply('3000', '')}>₹3,000 & up</button></div></div><form onSubmit={submit}><label>From (₹)<input name="minimum" aria-label="Minimum price" type="number" min="0" step="0.01" defaultValue={minPrice} placeholder="0" /></label><span>—</span><label>To (₹)<input name="maximum" aria-label="Maximum price" type="number" min="0" step="0.01" defaultValue={maxPrice} placeholder="Any" /></label><button className="primary-button">Apply <Check size={14} /></button>{error && <p role="alert">{error}</p>}</form></div>
}

export function ProductCard({ product, index = 0 }: { product: Product; index?: number }) {
  const { compared, savedIds, busyProduct, toggleCompare, toggleSaved, addToBag, quickView } = useShop()
  const isCompared = compared.some((item) => item.id === product.id), saved = savedIds.includes(product.id)
  const inStock = product.stockQuantity > 0, busy = busyProduct === product.id
  return <article className="discovery-card" style={{ animationDelay: `${Math.min(index, 7) * 45}ms` }}>
    <div className="discovery-image"><Link to={`/product/${product.id}`} tabIndex={-1} aria-hidden="true"><img src={productImage(product)} alt="" loading="lazy" onError={(event) => { event.currentTarget.onerror = null; event.currentTarget.src = '/images/product-placeholder.svg' }} /></Link><span className={`product-badge ${!inStock ? 'badge-muted' : ''}`}>{!inStock ? 'Taking a little break' : product.stockQuantity <= 5 ? 'Just a few left' : 'The everyday edit'}</span><button className={`save-product ${saved ? 'is-saved' : ''}`} aria-label={`${saved ? 'Unsave' : 'Save'} ${product.name}`} aria-pressed={saved} disabled={busyProduct !== null} onClick={() => void toggleSaved(product)}><Heart size={18} fill={saved ? 'currentColor' : 'none'} /></button><button className="quick-view-button" onClick={() => quickView(product)} aria-label={`Quick view ${product.name}`}><Eye size={16} /> Quick view</button></div>
    <div className="discovery-card-info"><div className="product-overline"><span>{product.sku}</span><button className={isCompared ? 'is-compared' : ''} aria-label={`Compare ${product.name}`} aria-pressed={isCompared} onClick={() => toggleCompare(product)}><GitCompareArrows size={14} />{isCompared ? 'Comparing' : 'Compare'}</button></div><Link className="product-title" to={`/product/${product.id}`}><h3>{product.name}</h3></Link><p className="product-description">{product.description}</p><div className="product-price-row"><strong>{formatPrice(Number(product.price))}</strong><span className={`availability ${!inStock ? 'sold-out' : ''}`}><i />{inStock ? 'In stock' : 'Sold out'}</span></div><button className="card-add-button" disabled={!inStock || busyProduct !== null} onClick={() => void addToBag(product)}>{busy ? 'Adding…' : inStock ? 'Add to bag' : 'Currently unavailable'}<ShoppingBag size={15} /></button></div>
  </article>
}
