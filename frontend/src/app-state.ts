import { createContext, useContext } from 'react'
import type { Cart, User } from './api'

export type AppState = {
  user: User | null
  cart: Cart | null
  refreshCart: () => Promise<void>
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
  updateUser: (user: User) => void
}

export const State = createContext<AppState | null>(null)

export function useApp() {
  const value = useContext(State)
  if (!value) throw new Error('App state is unavailable')
  return value
}
