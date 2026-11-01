import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
assert.equal(process.env.OASIS_BROWSER_TEST, 'true')
const root = fileURLToPath(new URL('../../', import.meta.url))
const port = process.argv[2]
assert.match(port ?? '', /^\d{2,5}$/)
const { chromium } = await import(
  pathToFileURL(
    process.env.OASIS_PLAYWRIGHT_MODULE ??
      path.join(root, '.local/browser-tools/node_modules/playwright/index.mjs'),
  ).href
)
const output = path.join(root, '.local/browser-results')
await mkdir(output, { recursive: true })
const vite = spawn(
  process.execPath,
  [
    path.join(root, 'oasis-app/node_modules/vite/bin/vite.js'),
    '--host',
    '127.0.0.1',
    '--port',
    '5181',
    '--strictPort',
  ],
  {
    cwd: path.join(root, 'oasis-app'),
    stdio: 'pipe',
    env: { ...process.env, OASIS_API_TARGET: `http://127.0.0.1:${port}` },
  },
)
let log = ''
vite.stdout.on('data', (b) => {
  log += b
})
vite.stderr.on('data', (b) => {
  log += b
})
let browser
const origin = 'http://127.0.0.1:5181'
try {
  let ready = false
  for (let i = 0; i < 80; i++) {
    if (vite.exitCode !== null) throw new Error(log)
    try {
      if ((await fetch(origin)).ok) {
        ready = true
        break
      }
    } catch {
      /* startup */
    }
    await new Promise((r) => setTimeout(r, 200))
  }
  assert.ok(ready, 'Vite did not start')
  browser = await chromium.launch({ channel: 'chrome', headless: true })
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1050 },
  })
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.clock.setFixedTime(new Date('2030-01-01T00:00:00Z'))
  await page.goto(origin)
  async function login(email) {
    await page.getByRole('button', { name: 'Sign in', exact: true }).click()
    await page.getByLabel('Email', { exact: true }).fill(email)
    await page
      .getByLabel('Password', { exact: true })
      .fill('Synthetic-Test-Only-2030!')
    await page
      .getByRole('button', { name: 'Sign in to Oasis', exact: true })
      .click()
    await page.getByRole('button', { name: 'Sign out', exact: true }).waitFor()
  }
  async function logout() {
    await page.getByRole('button', { name: 'Sign out', exact: true }).click()
    await page.getByRole('button', { name: 'Sign in', exact: true }).waitFor()
  }
  async function notice(text) {
    await page.getByRole('status').filter({ hasText: text }).waitFor()
  }
  await login('customer@oasis.test')
  await page.getByRole('button', { name: 'My membership', exact: true }).click()
  const catalogue = page
    .getByRole('heading', { name: 'Find your membership rhythm', exact: true })
    .locator('..')
  await catalogue
    .getByRole('button', { name: 'Create test order', exact: true })
    .click()
  await notice('Order created')
  let pending = page
    .locator('article[data-order-id]')
    .filter({ has: page.getByText('pending', { exact: true }) })
  await pending.waitFor()
  const id = await pending.getAttribute('data-order-id')
  assert.ok(id)
  // A forged browser success redirect must not activate anything.
  await page.goto(`${origin}/?payment=success&order_id=${id}`)
  await page.getByRole('button', { name: 'My membership', exact: true }).click()
  const before = await (
    await page.request.get(`${origin}/api/v1/me/orders/${id}`)
  ).json()
  assert.equal(before.status, 'PENDING')
  assert.equal(before.grantId, null)
  pending = page.locator(`article[data-order-id="${id}"]`)
  await pending
    .getByRole('button', { name: 'Prepare checkout', exact: true })
    .click()
  await notice('Test checkout ready')
  assert.equal(await page.locator('input[autocomplete="cc-number"]').count(), 0)
  await pending
    .getByRole('button', {
      name: 'Simulate PromptPay confirmation',
      exact: true,
    })
    .click()
  await notice('Simulated confirmation accepted')
  await pending.getByText('paid', { exact: true }).waitFor()
  await pending
    .getByRole('button', { name: 'View receipt', exact: true })
    .click()
  await page
    .getByRole('heading', {
      name: 'SIMULATED RECEIPT — NO MONEY COLLECTED',
      exact: true,
    })
    .waitFor()
  assert.match(await page.locator('.receipt').innerText(), /THB\s*1,500\.50/)
  assert.match(await page.locator('.receipt').innerText(), /SIM-/)
  assert.match(await page.locator('.receipt').innerText(), /not a tax invoice/)
  await page.screenshot({
    path: path.join(output, 'phase3-simulated-receipt.png'),
    fullPage: true,
  })
  await page.getByRole('button', { name: 'Close receipt', exact: true }).click()
  await logout()
  await login('admin@oasis.test')
  await page.getByRole('button', { name: 'Manage studio', exact: true }).click()
  const adminOrder = page.locator(`article[data-admin-order-id="${id}"]`)
  await adminOrder
    .getByLabel('Refund reason for customer', { exact: true })
    .fill('Unused membership browser refund')
  await adminOrder
    .getByRole('button', {
      name: 'Request full unused-membership refund',
      exact: true,
    })
    .click()
  await notice('Refund requested')
  await page
    .getByRole('button', { name: 'Prepare refund dispatch', exact: true })
    .click()
  await notice('Refund dispatch prepared in the simulator')
  await page
    .getByRole('button', { name: 'Simulate refund success', exact: true })
    .click()
  await notice('Simulated refund confirmed')
  await page.screenshot({
    path: path.join(output, 'phase3-admin-payments.png'),
    fullPage: true,
  })
  await logout()
  await login('customer@oasis.test')
  await page.getByRole('button', { name: 'My membership', exact: true }).click()
  await page
    .locator(`article[data-order-id="${id}"]`)
    .getByText('Refund: succeeded', { exact: true })
    .waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
    'Mobile payment history overflow',
  )
  await page.screenshot({
    path: path.join(output, 'phase3-mobile-orders.png'),
    fullPage: true,
  })
  await page.setViewportSize({ width: 1440, height: 1050 })
  await catalogue
    .getByRole('button', { name: 'Create test order', exact: true })
    .click()
  await notice('Order created')
  pending = page
    .locator('article[data-order-id]')
    .filter({ has: page.getByText('pending', { exact: true }) })
  await pending.waitFor()
  const secondId = await pending.getAttribute('data-order-id')
  assert.ok(secondId)
  pending = page.locator(`article[data-order-id="${secondId}"]`)
  await pending
    .getByRole('button', { name: 'Prepare checkout', exact: true })
    .click()
  await notice('Test checkout ready')
  await pending
    .getByRole('button', { name: 'Simulate card confirmation', exact: true })
    .click()
  await notice('Simulated confirmation accepted')
  await pending.getByText('paid', { exact: true }).waitFor()
  assert.deepEqual(errors, [])
  console.log(
    'PHASE 3 BROWSER PASS: pending order, forged redirect ignored, simulated PromptPay/card confirmation, labelled receipt, full refund, mobile history.',
  )
} catch (error) {
  if (browser)
    for (const c of browser.contexts())
      for (const p of c.pages())
        await p
          .screenshot({
            path: path.join(output, 'phase3-failure.png'),
            fullPage: true,
          })
          .catch(() => {})
  throw error
} finally {
  await browser?.close()
  vite.kill()
}
