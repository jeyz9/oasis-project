import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'

assert.equal(process.env.OASIS_BROWSER_TEST, 'true')
const root = fileURLToPath(new URL('../../', import.meta.url))
const [port, mode] = process.argv.slice(2)
assert.match(port ?? '', /^\d{2,5}$/)
assert.ok(['membership', 'training'].includes(mode))
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
    '5180',
    '--strictPort',
  ],
  {
    cwd: path.join(root, 'oasis-app'),
    stdio: 'pipe',
    env: { ...process.env, OASIS_API_TARGET: `http://127.0.0.1:${port}` },
  },
)
let viteLog = ''
vite.stdout.on('data', (b) => {
  viteLog += b
})
vite.stderr.on('data', (b) => {
  viteLog += b
})
let browser
try {
  let available = false
  for (let i = 0; i < 80; i++) {
    if (vite.exitCode !== null) throw new Error(viteLog)
    try {
      if ((await fetch('http://127.0.0.1:5180')).ok) {
        available = true
        break
      }
    } catch {
      /*startup*/
    }
    await new Promise((resolve) => setTimeout(resolve, 200))
  }
  assert.ok(available, 'Vite did not start')
  browser = await chromium.launch({ channel: 'chrome', headless: true })
  const page = await browser.newPage({
    viewport: { width: 1440, height: 1000 },
  })
  const errors = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.clock.setFixedTime(
    new Date(
      mode === 'membership' ? '2030-01-01T00:00:00Z' : '2030-01-01T04:00:00Z',
    ),
  )
  await page.goto('http://127.0.0.1:5180')
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
  if (mode === 'membership') {
    await login('admin@oasis.test')
    await page
      .getByRole('button', { name: 'Manage studio', exact: true })
      .click()
    const plan = page
      .locator('form')
      .filter({
        has: page.getByRole('heading', {
          name: 'Create membership plan',
          exact: true,
        }),
      })
    await plan
      .getByLabel('Plan name', { exact: true })
      .fill('Browser monthly unlimited')
    await plan
      .getByLabel('Session access', { exact: true })
      .selectOption('UNLIMITED')
    await plan.getByLabel('Listed price (THB)', { exact: true }).fill('1500.50')
    await plan.getByLabel('Morning Yoga', { exact: true }).check()
    await plan
      .getByRole('button', { name: 'Save membership plan', exact: true })
      .click()
    await notice('Membership plan saved')
    const grant = page
      .locator('form')
      .filter({
        has: page.getByRole('heading', {
          name: 'Issue complimentary membership',
          exact: true,
        }),
      })
    await grant
      .getByLabel('Customer', { exact: true })
      .selectOption({ label: 'customer · customer@oasis.test' })
    await grant
      .getByLabel('Membership plan', { exact: true })
      .selectOption({ label: 'Browser monthly unlimited' })
    await grant
      .getByLabel('Activation (Bangkok time)', { exact: true })
      .fill('2030-01-01T07:00')
    await grant
      .getByLabel('Grant reason', { exact: true })
      .fill('Browser complimentary membership')
    await grant
      .getByRole('button', { name: 'Issue free membership', exact: true })
      .click()
    await notice('No payment was collected')
    await logout()
    await login('trainer@oasis.test')
    await page
      .getByRole('button', { name: 'Trainer schedule', exact: true })
      .click()
    await page
      .getByLabel('Available from', { exact: true })
      .fill('2030-01-01T15:00')
    await page
      .getByLabel('Available until', { exact: true })
      .fill('2030-01-01T18:00')
    await page
      .getByRole('button', { name: 'Add availability', exact: true })
      .click()
    await notice('Availability added')
    await logout()
    await login('admin@oasis.test')
    await page
      .getByRole('button', { name: 'Manage studio', exact: true })
      .click()
    const service = page
      .locator('form')
      .filter({
        has: page.getByRole('heading', { name: 'Create service', exact: true }),
      })
    await service
      .getByLabel('Service name', { exact: true })
      .fill('Private practice')
    await service
      .getByLabel('Description', { exact: true })
      .fill('Personal training, one to one.')
    await service.getByLabel('Type').selectOption('PRIVATE')
    await service
      .getByRole('button', { name: 'Save service', exact: true })
      .click()
    await notice('Service saved')
    const session = page
      .locator('form')
      .filter({
        has: page.getByRole('heading', { name: 'Create session', exact: true }),
      })
    await session
      .getByLabel('Service', { exact: true })
      .selectOption({ label: 'Private practice' })
    await session
      .getByLabel('Trainer', { exact: true })
      .selectOption({ label: 'trainer' })
    await session.getByLabel('Location', { exact: true }).fill('Private Studio')
    await session
      .getByLabel('Start (Bangkok time)', { exact: true })
      .fill('2030-01-01T16:00')
    await session
      .getByRole('button', { name: 'Save session', exact: true })
      .click()
    await notice('Session saved')
    await logout()
    await login('customer@oasis.test')
    await page
      .getByLabel('Membership package', { exact: true })
      .selectOption({ label: 'Browser monthly unlimited · Unlimited' })
    const yoga = page
      .locator('article')
      .filter({
        has: page.getByRole('heading', { name: 'Morning Yoga', exact: true }),
      })
    await yoga
      .getByRole('button', { name: 'Book with unlimited access', exact: true })
      .click()
    await notice('Booking confirmed with your unlimited membership')
    await page
      .getByRole('button', { name: 'My membership', exact: true })
      .click()
    await page
      .getByText('Complimentary membership · no payment collected', {
        exact: true,
      })
      .waitFor()
    await page.screenshot({
      path: path.join(output, 'phase2-memberships.png'),
      fullPage: true,
    })
  } else {
    await login('trainer@oasis.test')
    await page
      .getByRole('button', { name: 'Trainer schedule', exact: true })
      .click()
    const yoga = page
      .locator('article')
      .filter({
        has: page.getByRole('heading', { name: 'Morning Yoga', exact: true }),
      })
    await yoga
      .getByRole('button', { name: 'View attendees', exact: true })
      .click()
    await yoga
      .getByLabel('Training outcome for customer', { exact: true })
      .fill('Improved balance and mobility.')
    await yoga
      .getByRole('button', {
        name: 'Save attendance for customer',
        exact: true,
      })
      .click()
    await notice('Attendance and training outcome saved')
    await yoga
      .getByRole('button', { name: 'Complete session', exact: true })
      .click()
    await notice('Session completed')
    await page
      .getByText('Session completed. Attendance is locked.', { exact: true })
      .waitFor()
    await page.screenshot({
      path: path.join(output, 'phase2-training.png'),
      fullPage: true,
    })
    await logout()
    await login('customer@oasis.test')
    await page.getByRole('button', { name: 'My bookings', exact: true }).click()
    await page.getByLabel('Rating', { exact: true }).selectOption('5')
    await page
      .getByLabel('Your review', { exact: true })
      .fill('A welcoming class with thoughtful guidance.')
    await page
      .getByRole('button', { name: 'Submit review', exact: true })
      .click()
    await notice('Your review has been saved')
    await page
      .getByRole('heading', { name: 'Morning Yoga · 5/5', exact: true })
      .waitFor()
    await page.setViewportSize({ width: 390, height: 844 })
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      'Mobile overflow',
    )
    await page.screenshot({
      path: path.join(output, 'phase2-review-mobile.png'),
      fullPage: true,
    })
  }
  assert.deepEqual(errors, [])
  console.log(`PHASE 2 BROWSER PASS: ${mode}`)
} catch (error) {
  if (browser)
    for (const context of browser.contexts())
      for (const page of context.pages())
        await page
          .screenshot({
            path: path.join(output, `phase2-${mode}-failure.png`),
            fullPage: true,
          })
          .catch(() => {})
  throw error
} finally {
  await browser?.close()
  vite.kill()
}
