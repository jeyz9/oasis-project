// Invoked only by the guarded PostgreSQL JUnit fixture, never against a normal database.
import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('../../', import.meta.url))
const port = process.argv[2]
assert.match(port ?? '', /^\d{2,5}$/)
assert.equal(
  process.env.OASIS_BROWSER_TEST,
  'true',
  'Browser fixture requires explicit opt-in',
)
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
    '5179',
    '--strictPort',
  ],
  {
    cwd: path.join(root, 'oasis-app'),
    stdio: 'pipe',
    env: { ...process.env, OASIS_API_TARGET: `http://127.0.0.1:${port}` },
  },
)
let viteLog = ''
vite.stdout.on('data', (chunk) => {
  viteLog += chunk
})
vite.stderr.on('data', (chunk) => {
  viteLog += chunk
})
const origin = 'http://127.0.0.1:5179'
let browser
try {
  let available = false
  for (let attempt = 0; attempt < 80; attempt++) {
    if (vite.exitCode !== null) throw new Error(`Vite exited: ${viteLog}`)
    try {
      if ((await fetch(origin)).ok) {
        available = true
        break
      }
    } catch {
      /* wait for startup */
    }
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  assert.ok(available, `Vite did not start: ${viteLog}`)
  browser = await chromium.launch({ channel: 'chrome', headless: true })
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  })
  const page = await context.newPage()
  const pageErrors = []
  page.on('pageerror', (error) => pageErrors.push(error.message))
  await page.clock.setFixedTime(new Date('2030-01-01T00:00:00Z'))
  await page.goto(origin)
  await page.getByRole('heading', { name: 'Morning Yoga' }).waitFor()
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
  await login('customer@oasis.test')
  await page
    .getByRole('button', { name: 'Book class · 1 credit', exact: true })
    .click()
  await page
    .getByRole('status')
    .filter({ hasText: 'Booking confirmed' })
    .waitFor()
  await page.getByRole('button', { name: 'My membership', exact: true }).click()
  await page.locator('.credit-count').filter({ hasText: '9' }).waitFor()
  await page.getByRole('button', { name: 'My bookings', exact: true }).click()
  await page
    .getByRole('button', { name: 'Cancel booking', exact: true })
    .waitFor()
  await page.screenshot({
    path: path.join(output, 'customer-booking.png'),
    fullPage: true,
  })
  await logout()
  await login('trainer@oasis.test')
  await page
    .getByRole('button', { name: 'Trainer schedule', exact: true })
    .click()
  await page
    .getByRole('button', { name: 'View attendees', exact: true })
    .click()
  await page
    .getByRole('listitem')
    .filter({ hasText: 'customer · confirmed' })
    .waitFor()
  await logout()
  await login('customer@oasis.test')
  await page.getByRole('button', { name: 'My bookings', exact: true }).click()
  await page
    .getByRole('button', { name: 'Cancel booking', exact: true })
    .click()
  await page
    .getByRole('status')
    .filter({ hasText: 'One credit has been restored' })
    .waitFor()
  await page.getByRole('button', { name: 'My membership', exact: true }).click()
  await page.locator('.credit-count').filter({ hasText: '10' }).waitFor()
  await page
    .getByRole('button', { name: 'Explore classes', exact: true })
    .click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({
    path: path.join(output, 'mobile-schedule.png'),
    fullPage: true,
  })
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
    'Mobile horizontal overflow',
  )
  await logout()
  await page.setViewportSize({ width: 1440, height: 1000 })
  await login('admin@oasis.test')
  await page.getByRole('button', { name: 'Manage studio', exact: true }).click()
  const service = page
    .locator('form')
    .filter({
      has: page.getByRole('heading', { name: 'Create service', exact: true }),
    })
  await service
    .getByLabel('Service name', { exact: true })
    .fill('Evening Pilates')
  await service
    .getByLabel('Description', { exact: true })
    .fill('A steady, welcoming practice.')
  await service.getByLabel('Category', { exact: true }).fill('Pilates')
  await service
    .getByRole('button', { name: 'Save service', exact: true })
    .click()
  await page.getByRole('status').filter({ hasText: 'Service saved' }).waitFor()
  const session = page
    .locator('form')
    .filter({
      has: page.getByRole('heading', { name: 'Create session', exact: true }),
    })
  await session
    .getByLabel('Service', { exact: true })
    .selectOption({ label: 'Evening Pilates' })
  await session
    .getByLabel('Trainer', { exact: true })
    .selectOption({ label: 'trainer' })
  await session.getByLabel('Location', { exact: true }).fill('Pilates Studio')
  await session
    .getByLabel('Start (Bangkok time)', { exact: true })
    .fill('2030-01-01T16:00')
  await session
    .getByRole('button', { name: 'Save session', exact: true })
    .click()
  await page.getByRole('status').filter({ hasText: 'Session saved' }).waitFor()
  await page.screenshot({
    path: path.join(output, 'admin-studio.png'),
    fullPage: true,
  })
  await logout()
  await page
    .getByRole('heading', { name: 'Evening Pilates', exact: true })
    .waitFor()
  // Registration is exercised in the browser as well as by HTTP security tests.
  await page.getByRole('button', { name: 'Sign in', exact: true }).click()
  await page
    .getByRole('button', { name: 'New here? Create an account' })
    .click()
  await page.getByLabel('Your name', { exact: true }).fill('New member')
  await page.getByLabel('Email', { exact: true }).fill('browser-new@oasis.test')
  await page
    .getByLabel('Password', { exact: true })
    .fill('Synthetic-Test-Only-2030!')
  await page
    .getByRole('button', { name: 'Create account', exact: true })
    .click()
  await page
    .getByRole('status')
    .filter({ hasText: 'Your account is ready' })
    .waitFor()
  await page.getByRole('button', { name: 'My membership', exact: true }).click()
  await page.getByText('You do not have a membership package yet.').waitFor()
  assert.deepEqual(pageErrors, [], 'Unexpected browser JavaScript errors')
  console.log(
    'BROWSER E2E PASS: signup, login/logout, booking, balance 10→9→10, trainer attendees, admin service/session, mobile layout.',
  )
} catch (error) {
  if (browser)
    for (const context of browser.contexts())
      for (const page of context.pages())
        await page
          .screenshot({
            path: path.join(output, 'failure.png'),
            fullPage: true,
          })
          .catch(() => {})
  throw error
} finally {
  await browser?.close()
  vite.kill()
}
