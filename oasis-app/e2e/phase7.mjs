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
    '5182',
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
const origin = 'http://127.0.0.1:5182'
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
  await login('admin@oasis.test')
  await page
    .getByRole('button', { name: 'Administration', exact: true })
    .click()
  await page
    .getByRole('heading', { name: 'Studio administration', exact: true })
    .waitFor()
  await page.getByLabel('Room name', { exact: true }).fill('Garden studio')
  await page
    .getByLabel('Room location', { exact: true })
    .fill('Bangkok courtyard')
  await page.getByLabel('Room capacity', { exact: true }).fill('8')
  await page.getByRole('button', { name: 'Save room', exact: true }).click()
  await notice('Room saved')
  await page
    .getByRole('button', { name: 'Edit room Garden studio', exact: true })
    .waitFor()
  await page
    .getByLabel('Cancellation notice (minutes)', { exact: true })
    .fill('180')
  await page
    .getByLabel('Policy change reason', { exact: true })
    .fill('Browser policy check')
  await page
    .getByRole('button', { name: 'Save cancellation policy', exact: true })
    .click()
  await notice('Cancellation policy saved')
  const access = page
    .locator('form')
    .filter({ has: page.getByRole('heading', { name: /other@oasis.test/ }) })
  await access.getByLabel('TRAINER', { exact: true }).check()
  await access
    .getByLabel('Access change reason', { exact: true })
    .fill('Browser role check')
  await access.getByRole('button', { name: /Save access/ }).click()
  await notice('User access updated')
  await page
    .getByRole('button', { name: 'Run notification worker', exact: true })
    .click()
  await notice('Notification worker cycle completed')
  await page.getByRole('button', { name: 'Reports', exact: true }).click()
  await page
    .getByRole('heading', { name: /Package sales.*real data/ })
    .waitFor()
  assert.ok(
    !(await page.locator('tbody').first().innerText()).includes('1,500.50'),
  )
  await page.getByLabel('Sales data', { exact: true }).selectOption('SIMULATED')
  await page
    .getByRole('heading', { name: /Package sales.*SIMULATION ONLY/ })
    .waitFor()
  assert.ok(
    (await page.locator('tbody').first().innerText()).includes('1,500.50'),
  )
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({
    path: path.join(output, 'phase7-reports-mobile.png'),
    fullPage: true,
  })
  assert.ok(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth + 1,
    ),
    'Mobile overflow',
  )
  await page.setViewportSize({ width: 1440, height: 1050 })
  await logout()
  await login('customer@oasis.test')
  assert.equal(
    await page
      .getByRole('button', { name: 'Administration', exact: true })
      .count(),
    0,
  )
  await page.getByRole('button', { name: 'Inbox', exact: true }).click()
  await page
    .getByRole('heading', { name: 'Booking confirmed', exact: true })
    .waitFor()
  await page.getByRole('button', { name: 'Mark as read', exact: true }).click()
  await page.getByText('Read', { exact: true }).waitFor()
  await page.screenshot({
    path: path.join(output, 'phase7-inbox.png'),
    fullPage: true,
  })
  await logout()
  await login('trainer@oasis.test')
  await page
    .getByRole('button', { name: 'My performance', exact: true })
    .click()
  await page
    .getByText('No completed classes in this period.', { exact: true })
    .waitFor()
  assert.deepEqual(errors, [])
  console.log(
    'PHASE 7 BROWSER PASS: admin room/policy/role audit, outbox worker, real/simulated reports, mobile overflow, owned inbox/read, trainer summary.',
  )
} catch (error) {
  if (browser)
    for (const c of browser.contexts())
      for (const p of c.pages())
        await p
          .screenshot({
            path: path.join(output, 'phase7-failure.png'),
            fullPage: true,
          })
          .catch(() => {})
  throw error
} finally {
  await browser?.close()
  vite.kill()
}
