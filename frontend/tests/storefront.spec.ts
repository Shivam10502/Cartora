import { expect, test, type Page } from '@playwright/test'

const categories = [{ categoryId: 1, name: 'Lighting', description: 'A softer glow.' }, { categoryId: 2, name: 'Living', description: 'Your favorite corners.' }]
const products = Array.from({ length: 25 }, (_, index) => ({ id: index + 1, name: `Thoughtful Piece ${String(index + 1).padStart(2, '0')}`, description: 'A considered piece for the everyday.', price: (index + 1) * 250, stockQuantity: index === 24 ? 0 : 12, sku: `TEST-${index + 1}`, categoryId: index % 2 + 1, imageUrl: '/images/lighting.jpg' }))

async function mockShop(page: Page, authenticated = false) {
  let cartItems: { cartItemId: number; productId: number; productName: string; price: number; quantity: number; subtotal: number }[] = []
  let unavailable = false
  const queries: URL[] = []
  if (authenticated) await page.addInitScript(() => localStorage.setItem('cartora.token', 'test-session'))
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url()), path = url.pathname, method = route.request().method()
    let body: unknown = []
    if (path === '/api/users/me') body = { id: 1, name: 'Sam Test', email: 'sam@example.test', role: 'CUSTOMER' }
    else if (path === '/api/categories') body = categories
    else if (path === '/api/products') {
      queries.push(url)
      if (unavailable) return route.fulfill({ status: 503, json: { message: 'Collection temporarily unavailable.' } })
      let items = products.filter((product) => (!url.searchParams.get('categoryId') || product.categoryId === Number(url.searchParams.get('categoryId'))) && (!url.searchParams.get('minPrice') || product.price >= Number(url.searchParams.get('minPrice'))) && (!url.searchParams.get('maxPrice') || product.price <= Number(url.searchParams.get('maxPrice'))) && (!url.searchParams.get('search') || product.name.toLowerCase().includes(url.searchParams.get('search')!.toLowerCase())))
      const sort = url.searchParams.get('sortBy'), sign = url.searchParams.get('direction') === 'desc' ? -1 : 1
      items = items.toSorted((a, b) => sort === 'price' ? (a.price - b.price) * sign : sort === 'name' ? a.name.localeCompare(b.name) * sign : a.id - b.id)
      const number = Number(url.searchParams.get('page') || 0), size = Number(url.searchParams.get('size') || 12), pages = Math.ceil(items.length / size)
      body = { content: items.slice(number * size, (number + 1) * size), page: number, size, totalElements: items.length, totalPages: pages, first: number === 0, last: number >= pages - 1 }
    } else if (/^\/api\/products\/\d+$/.test(path)) body = products.find((product) => product.id === Number(path.split('/').at(-1)))
    else if (path.endsWith('/reviews/summary')) body = { averageRating: 0, reviewCount: 0 }
    else if (path === '/api/cart/items' && method === 'POST') {
      const request = route.request().postDataJSON(), product = products.find((item) => item.id === request.productId)!
      const existing = cartItems.find((item) => item.productId === product.id)
      if (existing) { existing.quantity += request.quantity; existing.subtotal = existing.quantity * existing.price }
      else cartItems.push({ cartItemId: product.id, productId: product.id, productName: product.name, price: product.price, quantity: request.quantity, subtotal: product.price * request.quantity })
      body = { cartId: 1, userId: 1, items: cartItems }
    } else if (path.startsWith('/api/cart/items/')) {
      const id = Number(path.split('/').at(-1))
      if (method === 'DELETE') cartItems = cartItems.filter((item) => item.cartItemId !== id)
      else { const item = cartItems.find((entry) => entry.cartItemId === id)!; item.quantity = route.request().postDataJSON().quantity; item.subtotal = item.quantity * item.price }
      body = { cartId: 1, userId: 1, items: cartItems }
    } else if (path === '/api/cart') body = { cartId: 1, userId: 1, items: cartItems }
    await route.fulfill({ json: body })
  })
  return { queries, setUnavailable: (value: boolean) => { unavailable = value } }
}

test('price and sort filters reach the API together and validate reversed ranges', async ({ page }) => {
  const { queries } = await mockShop(page)
  await page.goto('/#catalog')
  await page.getByRole('button', { name: 'Filters', exact: true }).click()
  await page.getByRole('spinbutton', { name: 'Minimum price' }).fill('2500')
  await page.getByRole('spinbutton', { name: 'Maximum price' }).fill('500')
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await expect(page.getByRole('alert')).toHaveText('Minimum price must be less than or equal to maximum price.')
  await page.getByRole('spinbutton', { name: 'Minimum price' }).fill('500')
  await page.getByRole('spinbutton', { name: 'Maximum price' }).fill('2500')
  await page.getByRole('button', { name: 'Apply', exact: true }).click()
  await page.getByRole('combobox', { name: 'Sort products' }).selectOption('price:desc')
  await expect.poll(() => queries.at(-1)?.searchParams.get('sortBy')).toBe('price')
  expect(queries.at(-1)?.searchParams.get('direction')).toBe('desc')
  expect(queries.at(-1)?.searchParams.get('minPrice')).toBe('500')
  expect(queries.at(-1)?.searchParams.get('maxPrice')).toBe('2500')
  await expect(page.locator('#catalog .product-title').first()).toHaveText('Thoughtful Piece 10')
  await page.getByRole('button', { name: 'Clear all', exact: true }).click()
  await expect(page).not.toHaveURL(/minPrice|maxPrice/)
})

test('pagination changes page without resetting filters and browser reload preserves it', async ({ page }) => {
  await mockShop(page)
  await page.goto('/?minPrice=250#catalog')
  await page.getByRole('button', { name: 'Next', exact: true }).click()
  await expect(page).toHaveURL(/page=1/)
  await expect(page.locator('#catalog .product-title').first()).toHaveText('Thoughtful Piece 13')
  await page.reload()
  await expect(page.locator('#catalog .product-title').first()).toHaveText('Thoughtful Piece 13')
  await expect(page).toHaveURL(/minPrice=250/)
  await page.getByRole('button', { name: 'Previous', exact: true }).click()
  await expect(page.locator('#catalog .product-title').first()).toHaveText('Thoughtful Piece 01')
})

test('quick view supports quantity, Escape, focus restoration, and a product-specific sign-in return', async ({ page }) => {
  await mockShop(page)
  await page.goto('/#catalog')
  const open = page.getByRole('button', { name: 'Quick view Thoughtful Piece 01', exact: true })
  await page.locator('.discovery-card').first().hover()
  await open.click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Increase quantity', exact: true }).click()
  await expect(dialog.getByLabel('Quantity', { exact: true })).toHaveText('2')
  await page.keyboard.press('Escape')
  await expect(dialog).not.toBeVisible()
  await expect(open).toBeFocused()
  await open.click()
  await page.getByRole('dialog').getByRole('button', { name: 'Add to bag', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
})

test('comparison enforces three pieces, persists after reload, and shows real product details', async ({ page }) => {
  await mockShop(page)
  await page.goto('/#catalog')
  for (let id = 1; id <= 3; id++) await page.getByRole('button', { name: `Compare Thoughtful Piece 0${id}`, exact: true }).click()
  await page.getByRole('button', { name: 'Compare Thoughtful Piece 04', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('Compare up to 3 pieces')
  await page.reload()
  await expect(page.getByRole('complementary', { name: 'Product comparison' })).toContainText('3/3')
  await page.getByRole('complementary', { name: 'Product comparison' }).getByRole('button', { name: 'Compare', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('Good things, side by side.')
  await expect(page.getByRole('dialog')).toContainText('TEST-1')
  await expect(page.getByRole('dialog')).toContainText('12 in stock')
  await page.getByRole('dialog').getByRole('button', { name: 'Close dialog' }).click()
  await page.getByRole('complementary', { name: 'Product comparison' }).getByRole('button', { name: 'Clear', exact: true }).click()
  await expect(page.getByRole('complementary', { name: 'Product comparison' })).not.toBeVisible()
})

test('authenticated quick add and bag quantity changes use the cart API', async ({ page }) => {
  await mockShop(page, true)
  await page.goto('/#catalog')
  await page.locator('.discovery-card').first().hover()
  await page.getByRole('button', { name: 'Quick view Thoughtful Piece 01', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Increase quantity', exact: true }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Add to bag', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Open bag, 2 items', exact: true })).toBeAttached()
  await page.getByRole('dialog').getByRole('button', { name: 'Close dialog' }).click()
  await page.getByRole('button', { name: 'Open bag, 2 items', exact: true }).click()
  const bag = page.getByRole('dialog')
  await expect(bag).toContainText('Thoughtful Piece 01')
  await bag.getByRole('button', { name: 'Increase Thoughtful Piece 01 quantity', exact: true }).click()
  await expect(bag.getByRole('heading', { name: 'Your bag (3)' })).toBeVisible()
  await bag.getByRole('button', { name: 'Remove Thoughtful Piece 01 from bag', exact: true }).click()
  await expect(bag).toContainText('A little room for something good.')
})

test('search, list preference, empty results, and retry all work', async ({ page }) => {
  const shop = await mockShop(page)
  await page.goto('/#catalog')
  await page.getByRole('button', { name: 'List view', exact: true }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: 'List view', exact: true })).toHaveAttribute('aria-pressed', 'true')
  await page.getByRole('textbox', { name: 'Search products', exact: true }).fill('nothing matches this')
  await expect(page.locator('#catalog')).toContainText('Your next favorite is still out there.')
  await page.getByRole('button', { name: 'Explore all pieces', exact: true }).click()
  await expect(page.locator('#catalog .discovery-card')).toHaveCount(12)
  shop.setUnavailable(true)
  await page.getByRole('button', { name: 'Lighting', exact: true }).click()
  await expect(page.locator('#catalog')).toContainText('Collection temporarily unavailable.')
  shop.setUnavailable(false)
  await page.getByRole('button', { name: 'Try again', exact: true }).click()
  await expect(page.locator('#catalog .discovery-card')).toHaveCount(12)
})

test('navigation and dialogs fit the viewport without horizontal page overflow', async ({ page }, testInfo) => {
  await mockShop(page)
  await page.goto('/')
  if (testInfo.project.name === 'mobile') {
    await page.getByRole('button', { name: 'Open navigation', exact: true }).click()
    await expect(page.getByRole('navigation', { name: 'Mobile navigation' })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('navigation', { name: 'Mobile navigation' })).not.toBeVisible()
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.locator('.discovery-card').first().hover()
  await page.getByRole('button', { name: 'Quick view Thoughtful Piece 01', exact: true }).click()
  const bounds = await page.getByRole('dialog').boundingBox()
  expect(bounds!.width).toBeLessThanOrEqual(page.viewportSize()!.width)
  await expect(page.getByRole('dialog').getByRole('heading', { name: 'Thoughtful Piece 01' })).toBeAttached()
})
