import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ArrowRight, GitCompareArrows, Heart, Minus, Plus, ShoppingBag } from 'lucide-react'
import { getErrorMessage, getProduct, getProducts, type Product } from '../api'
import { useShop } from './shop-state'
import { ProductCard } from './Storefront'

export function ProductPurchase({ product }: { product: Product }) {
  const { addToBag, toggleSaved, toggleCompare, savedIds, compared, busyProduct } = useShop()
  const [quantity, setQuantity] = useState(1)
  const saved = savedIds.includes(product.id), comparing = compared.some((item) => item.id === product.id)
  return <div className="detail-purchase-tools"><div className="quick-purchase"><div className="quantity"><button aria-label="Decrease quantity" disabled={quantity <= 1} onClick={() => setQuantity(quantity - 1)}><Minus size={15} /></button><output aria-label="Quantity">{quantity}</output><button aria-label="Increase quantity" disabled={quantity >= product.stockQuantity} onClick={() => setQuantity(quantity + 1)}><Plus size={15} /></button></div><button className="primary-button" disabled={!product.stockQuantity || busyProduct !== null} onClick={() => void addToBag(product, quantity)}>{busyProduct === product.id ? 'Adding…' : 'Add to bag'} <ShoppingBag size={17} /></button></div><div className="quick-secondary"><button aria-pressed={saved} disabled={busyProduct !== null} onClick={() => void toggleSaved(product)}><Heart size={16} fill={saved ? 'currentColor' : 'none'} />{saved ? 'Saved for later' : 'Save for later'}</button><button aria-pressed={comparing} onClick={() => toggleCompare(product)}><GitCompareArrows size={16} />{comparing ? 'Comparing' : 'Compare this piece'}</button></div></div>
}

export function RelatedPieces({ product }: { product: Product }) {
  const [pieces, setPieces] = useState<Product[]>([])
  useEffect(() => {
    let active = true
    getProducts({ categoryId: product.categoryId, size: 5, sortBy: 'createdAt', direction: 'desc' }).then((page) => { if (active) setPieces(page.content.filter((item) => item.id !== product.id).slice(0, 4)) }).catch(() => { if (active) setPieces([]) })
    return () => { active = false }
  }, [product.id, product.categoryId])
  if (!pieces.length) return null
  return <section className="related-pieces"><div className="section-heading"><div><span className="eyebrow">GOOD COMPANY FOR A GOOD FIND</span><h2>You might also love.</h2></div><Link to={`/?categoryId=${product.categoryId}#catalog`} className="section-link">Explore the collection <ArrowRight size={15} /></Link></div><div className="discovery-grid">{pieces.map((piece, index) => <ProductCard key={piece.id} product={piece} index={index} />)}</div></section>
}

export function SavedPieces() {
  const { savedIds } = useShop()
  const [items, setItems] = useState<Product[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    Promise.all(savedIds.map((id) => getProduct(id))).then((products) => { if (active) setItems(products) }).catch((cause) => { if (active) setError(getErrorMessage(cause)) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [savedIds])
  return <main className="content-page"><span className="eyebrow">YOUR VERY OWN CARTORA EDIT</span><h1>Good things, saved.</h1><p>A little collection of the pieces that caught your eye.</p>{loading ? <p role="status">Finding your favorites…</p> : error ? <p className="notice error" role="alert">{error}</p> : items.length ? <div className="discovery-grid">{items.map((item, index) => <ProductCard key={item.id} product={item} index={index} />)}</div> : <div className="empty-state collection-empty"><Heart size={34} strokeWidth={1} /><h3>Your saved edit starts here.</h3><p>Tap the heart on any piece to keep it close.</p><Link className="primary-button" to="/#catalog">Find your favorites <ArrowRight size={16} /></Link></div>}</main>
}
