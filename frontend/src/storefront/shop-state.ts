import { createContext, useContext } from 'react'
import type { Product } from '../api'

export type ShopState = {
  compared: Product[]
  savedIds: number[]
  busyProduct: number | null
  toggleCompare: (product: Product) => void
  clearCompare: () => void
  toggleSaved: (product: Product) => Promise<void>
  addToBag: (product: Product, quantity?: number) => Promise<boolean>
  quickView: (product: Product) => void
  openCompare: () => void
  openBag: () => void
  openHelp: () => void
  closeOverlay: () => void
}

export const ShopContext = createContext<ShopState | null>(null)
export function useShop() {
  const state = useContext(ShopContext)
  if (!state) throw new Error('Shopping state is unavailable')
  return state
}

export const productImage = (product: Product) => product.imageUrl || '/images/product-placeholder.svg'
