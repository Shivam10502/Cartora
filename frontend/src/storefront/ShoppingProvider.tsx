import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowRight, Check, GitCompareArrows, Heart, Minus, Plus, ShoppingBag, Trash2, X } from 'lucide-react'
import { addCartItem, addWishlistItem, formatPrice, getErrorMessage, getProduct, getWishlist, removeCartItem, removeWishlistItem, updateCartItem, type Product } from '../api'
import { useApp } from '../app-state'
import { productImage, ShopContext } from './shop-state'

type Overlay = { kind: 'quick'; product: Product } | { kind: 'compare' | 'bag' | 'help' } | null
type Toast = { text: string; error?: boolean } | null
const COMPARE_KEY = 'cartora.compare'

export function ShoppingProvider({ children }: { children: ReactNode }) {
  const { user, cart, refreshCart } = useApp()
  const location = useLocation(), navigate = useNavigate()
  const [compared, setCompared] = useState<Product[]>([])
  const [savedIds, setSavedIds] = useState<number[]>([])
  const [busyProduct, setBusyProduct] = useState<number | null>(null)
  const [overlay, setOverlay] = useState<Overlay>(null)
  const [toast, setToast] = useState<Toast>(null)
  const dialog = useRef<HTMLDialogElement>(null)
  const cartBusy = useRef(false)

  useEffect(() => {
    let active = true
    let ids: number[] = []
    try {
      const stored: unknown = JSON.parse(localStorage.getItem(COMPARE_KEY) ?? '[]')
      if (Array.isArray(stored)) ids = [...new Set(stored.filter((id): id is number => Number.isInteger(id) && id > 0))].slice(0, 3)
    } catch { /* A broken browser entry should not prevent shopping. */ }
    Promise.all(ids.map((id) => getProduct(id).catch(() => null))).then((products) => {
      if (active) setCompared((current) => current.length ? current : products.filter((product): product is Product => Boolean(product)))
    })
    return () => { active = false }
  }, [])

  useEffect(() => {
    let active = true
    if (user) getWishlist().then((items) => { if (active) setSavedIds(items.map((item) => item.productId)) }).catch(() => undefined)
    else setSavedIds([])
    return () => { active = false }
  }, [user, location.pathname])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(null), 4500)
    return () => window.clearTimeout(timer)
  }, [toast])

  useEffect(() => {
    if (!overlay || !dialog.current) return
    const element = dialog.current
    const returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    if (!element.open) element.showModal()
    return () => { element.close(); document.body.style.overflow = previousOverflow; if (returnFocus?.isConnected) returnFocus.focus({ preventScroll: true }) }
  }, [overlay])

  useEffect(() => { setOverlay(null) }, [location.pathname, location.search])

  const persistCompared = (products: Product[]) => {
    setCompared(products)
    try { localStorage.setItem(COMPARE_KEY, JSON.stringify(products.map((product) => product.id))) } catch { /* Storage may be unavailable in private browsing. */ }
  }
  const toggleCompare = (product: Product) => {
    if (compared.some((item) => item.id === product.id)) persistCompared(compared.filter((item) => item.id !== product.id))
    else if (compared.length < 3) persistCompared([...compared, product])
    else setToast({ text: 'Compare up to 3 pieces. Remove one to make room.', error: true })
  }
  const requireSignIn = (product?: Product) => {
    setOverlay(null)
    navigate('/login', { state: { from: product ? { pathname: `/product/${product.id}`, search: '', hash: '' } : location } })
  }
  const toggleSaved = async (product: Product) => {
    if (!user) { requireSignIn(product); return }
    if (busyProduct !== null) return
    setBusyProduct(product.id)
    try {
      const isSaved = savedIds.includes(product.id)
      if (isSaved) await removeWishlistItem(product.id)
      else await addWishlistItem(product.id)
      setSavedIds((current) => isSaved ? current.filter((id) => id !== product.id) : [...current, product.id])
      setToast({ text: isSaved ? 'Removed from your saved pieces.' : 'Saved for a little later.' })
    } catch (error) { setToast({ text: getErrorMessage(error), error: true }) }
    finally { setBusyProduct(null) }
  }
  const addToBag = async (product: Product, quantity = 1) => {
    if (!user) { requireSignIn(product); return false }
    if (cartBusy.current) return false
    cartBusy.current = true
    setBusyProduct(product.id)
    try {
      await addCartItem(product.id, quantity)
      await refreshCart()
      setToast({ text: `${product.name} added to your bag.` })
      return true
    } catch (error) { setToast({ text: getErrorMessage(error), error: true }); return false }
    finally { setBusyProduct(null); cartBusy.current = false }
  }
  const openBag = () => { if (user) setOverlay({ kind: 'bag' }); else requireSignIn() }
  const count = cart?.items.reduce((sum, item) => sum + item.quantity, 0) ?? 0

  return <ShopContext.Provider value={{ compared, savedIds, busyProduct, toggleCompare, clearCompare: () => persistCompared([]), toggleSaved, addToBag, quickView: (product) => setOverlay({ kind: 'quick', product }), openCompare: () => setOverlay({ kind: 'compare' }), openBag, openHelp: () => setOverlay({ kind: 'help' }), closeOverlay: () => setOverlay(null) }}>
    {children}
    {compared.length > 0 && <aside className="compare-tray" aria-label="Product comparison">
      <span className="compare-tray-icon"><GitCompareArrows size={19} /></span>
      <div><strong>Your shortlist <span>{compared.length}/3</span></strong><small>Find the piece that fits.</small></div>
      <div className="compare-thumbnails">{compared.map((product) => <button key={product.id} aria-label={`Remove ${product.name} from comparison`} onClick={() => toggleCompare(product)}><img src={productImage(product)} alt="" /><X size={11} /></button>)}</div>
      <button className="text-button compare-clear" onClick={() => persistCompared([])}>Clear</button>
      <button className="primary-button" disabled={compared.length < 2} onClick={() => setOverlay({ kind: 'compare' })}>Compare <ArrowRight size={15} /></button>
    </aside>}
    {toast && !overlay && <div className={`shop-toast ${toast.error ? 'toast-error' : ''}`} role={toast.error ? 'alert' : 'status'}><span>{toast.error ? <X size={18} /> : <Check size={18} />}</span><p>{toast.text}</p><button aria-label="Dismiss notification" onClick={() => setToast(null)}><X size={16} /></button></div>}
    {overlay && <dialog ref={dialog} className={`shop-dialog ${overlay.kind === 'bag' ? 'bag-dialog' : ''} ${overlay.kind === 'compare' ? 'compare-dialog' : ''}`} aria-labelledby="shop-dialog-title" onCancel={() => setOverlay(null)} onClick={(event) => { if (event.target === event.currentTarget) { const rect = event.currentTarget.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) setOverlay(null) } }}>
      <button className="dialog-close" aria-label="Close dialog" onClick={() => setOverlay(null)}><X size={20} /></button>
      {toast && <div className={`dialog-notice ${toast.error ? 'dialog-notice-error' : ''}`} role={toast.error ? 'alert' : 'status'}>{toast.error ? <X size={16} /> : <Check size={16} />}<span>{toast.text}</span><button aria-label="Dismiss notification" onClick={() => setToast(null)}><X size={14} /></button></div>}
      {overlay.kind === 'quick' && <QuickView key={overlay.product.id} product={overlay.product} busy={busyProduct === overlay.product.id} add={addToBag} saved={savedIds.includes(overlay.product.id)} save={() => void toggleSaved(overlay.product)} compare={() => toggleCompare(overlay.product)} compared={compared.some((product) => product.id === overlay.product.id)} />}
      {overlay.kind === 'compare' && <div className="comparison-content"><span className="eyebrow">A CLOSER LOOK</span><h2 id="shop-dialog-title">Good things, side by side.</h2><p>Compare the details. Choose what works for you.</p><div className="comparison-scroll"><table className="comparison-table"><thead><tr><th scope="col">The details</th>{compared.map((product) => <th scope="col" key={product.id}><img src={productImage(product)} alt="" /><Link to={`/product/${product.id}`}>{product.name}</Link><button className="text-button" onClick={() => toggleCompare(product)}>Remove</button></th>)}</tr></thead><tbody><tr><th scope="row">Price</th>{compared.map((product) => <td key={product.id}>{formatPrice(Number(product.price))}</td>)}</tr><tr><th scope="row">Availability</th>{compared.map((product) => <td key={product.id}>{product.stockQuantity > 0 ? `${product.stockQuantity} in stock` : 'Sold out'}</td>)}</tr><tr><th scope="row">Why you’ll like it</th>{compared.map((product) => <td key={product.id}>{product.description}</td>)}</tr><tr><th scope="row">Product code</th>{compared.map((product) => <td key={product.id}>{product.sku}</td>)}</tr><tr><th scope="row">Make it yours</th>{compared.map((product) => <td key={product.id}><button className="primary-button" disabled={!product.stockQuantity || busyProduct !== null} onClick={() => void addToBag(product)}>Add to bag</button></td>)}</tr></tbody></table></div></div>}
      {overlay.kind === 'bag' && <BagDrawer count={count} onError={(text) => setToast({ text, error: true })} />}
      {overlay.kind === 'help' && <div className="shopping-help"><span className="eyebrow">A LITTLE GUIDANCE</span><h2 id="shop-dialog-title">Make yourself at home.</h2><details open><summary>How do I order?</summary><p>Create an account, save your favorite pieces, and add them to your bag. At checkout, choose a saved address or enter a new one.</p></details><details><summary>Where can I track my order?</summary><p>Your orders page shows the latest status, shipping address, and order details. Pending orders can be cancelled there.</p><Link to="/orders" className="text-link">View my orders <ArrowRight size={14} /></Link></details><details><summary>Can I save something for later?</summary><p>Sign in and tap the heart on any piece. Your saved collection stays with your account. You can also compare up to three pieces without signing in.</p></details><details><summary>How do reviews work?</summary><p>Customers with a confirmed or fulfilled purchase can leave a rating and review on the product page.</p></details></div>}
    </dialog>}
  </ShopContext.Provider>
}

function QuickView({ product, busy, add, saved, save, compare, compared }: { product: Product; busy: boolean; add: (product: Product, quantity: number) => Promise<boolean>; saved: boolean; save: () => void; compare: () => void; compared: boolean }) {
  const [quantity, setQuantity] = useState(1)
  return <div className="quick-view"><div className="quick-photo"><img src={productImage(product)} alt={product.name} /></div><div className="quick-info"><span className="eyebrow">MEET YOUR NEXT FAVORITE</span><h2 id="shop-dialog-title">{product.name}</h2><strong className="quick-price">{formatPrice(Number(product.price))}</strong><p>{product.description}</p><span className={`availability ${product.stockQuantity ? '' : 'sold-out'}`}><i />{product.stockQuantity > 0 ? `${product.stockQuantity} available` : 'Currently sold out'}</span><div className="quick-purchase"><div className="quantity"><button aria-label="Decrease quantity" disabled={quantity <= 1 || busy} onClick={() => setQuantity(quantity - 1)}><Minus size={15} /></button><output aria-label="Quantity">{quantity}</output><button aria-label="Increase quantity" disabled={quantity >= product.stockQuantity || busy} onClick={() => setQuantity(quantity + 1)}><Plus size={15} /></button></div><button className="primary-button" disabled={!product.stockQuantity || busy} onClick={() => void add(product, quantity)}>{busy ? 'Adding…' : 'Add to bag'} <ShoppingBag size={17} /></button></div><div className="quick-secondary"><button onClick={save} disabled={busy}><Heart size={16} fill={saved ? 'currentColor' : 'none'} />{saved ? 'Saved' : 'Save for later'}</button><button onClick={compare} aria-pressed={compared}><GitCompareArrows size={16} />{compared ? 'Comparing' : 'Compare'}</button></div><Link className="detail-link" to={`/product/${product.id}`}>View full details & reviews <ArrowRight size={16} /></Link></div></div>
}

function BagDrawer({ count, onError }: { count: number; onError: (text: string) => void }) {
  const { cart, refreshCart } = useApp()
  const [updating, setUpdating] = useState(false)
  const total = cart?.items.reduce((sum, item) => sum + Number(item.subtotal), 0) ?? 0
  const change = async (id: number, quantity: number) => {
    if (updating) return
    setUpdating(true)
    try { if (quantity <= 0) await removeCartItem(id); else await updateCartItem(id, quantity); await refreshCart() }
    catch (error) { onError(getErrorMessage(error)) }
    finally { setUpdating(false) }
  }
  return <div className="bag-content"><span className="eyebrow">GOOD CHOICES, ALL YOURS</span><h2 id="shop-dialog-title">Your bag <span>({count})</span></h2>{!cart?.items.length ? <div className="bag-empty"><ShoppingBag size={42} strokeWidth={1} /><h3>A little room for something good.</h3><p>Your next favorite is waiting in the collection.</p><Link className="primary-button" to="/">Explore the collection <ArrowRight size={16} /></Link></div> : <><div className="drawer-items">{cart.items.map((item) => <article key={item.cartItemId}><Link className="bag-item-icon" to={`/product/${item.productId}`}><ShoppingBag size={24} strokeWidth={1} /></Link><div><Link to={`/product/${item.productId}`}>{item.productName}</Link><span>{formatPrice(Number(item.price))}</span><div className="quantity"><button disabled={updating} aria-label={`Decrease ${item.productName} quantity`} onClick={() => void change(item.cartItemId, item.quantity - 1)}><Minus size={13} /></button><output>{item.quantity}</output><button disabled={updating} aria-label={`Increase ${item.productName} quantity`} onClick={() => void change(item.cartItemId, item.quantity + 1)}><Plus size={13} /></button></div></div><button className="remove-bag-item" disabled={updating} aria-label={`Remove ${item.productName} from bag`} onClick={() => void change(item.cartItemId, 0)}><Trash2 size={16} /></button></article>)}</div><div className="bag-totals"><div><span>Subtotal</span><strong>{formatPrice(total)}</strong></div><p>Review your address and any coupon at checkout.</p><Link to="/checkout" className="primary-button">Continue to checkout <ArrowRight size={17} /></Link><Link to="/cart" className="text-link">View your full bag</Link></div></>}</div>
}
