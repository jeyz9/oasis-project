import { useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '../shared/api'
import type { Plan, RunAction } from '../shared/api'
import { PaymentNotice } from './Payments'
import type { PaymentCapabilities } from './Payments'
export function PlanCatalogue({
  run,
  busy,
}: {
  run: RunAction
  busy: boolean
}) {
  const [plans, setPlans] = useState<Plan[]>([])
  const [error, setError] = useState('')
  const [capabilities, setCapabilities] = useState<PaymentCapabilities | null>(
    null,
  )
  const keys = useRef(new Map<string, string>())
  useEffect(() => {
    let current = true
    api<Plan[]>('/membership-plans').then((rows) => {
      if (current) setPlans(rows)
    })
    api<PaymentCapabilities>('/payments/capabilities')
      .then((c) => {
        if (current) setCapabilities(c)
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [])
  return (
    <section className="spaced">
      <h2>Find your membership rhythm</h2>
      <p className="muted">
        Monthly, yearly and prepaid options. Create an order at the listed
        price. Access starts only after verified server confirmation.
      </p>
      <PaymentNotice capabilities={capabilities} />
      {error && <p role="alert">{error}</p>}
      <div className="class-grid">
        {plans.map((p) => (
          <article className="panel membership-card" key={p.id}>
            <span className="tag">{p.period.toLowerCase()}</span>
            <h3>{p.name}</h3>
            <p>
              {p.entitlementMode === 'UNLIMITED'
                ? 'Unlimited sessions'
                : `${p.credits} session credits`}
            </p>
            <p>
              {new Intl.NumberFormat('en-TH', {
                style: 'currency',
                currency: p.currency,
              }).format(p.priceMinor / 100)}
            </p>
            {p.validityDays && <p>Valid for {p.validityDays} days</p>}
            <button
              className="button primary"
              disabled={busy || p.priceMinor === 0}
              onClick={() => {
                let key = keys.current.get(p.id)
                if (!key) {
                  key = crypto.randomUUID()
                  keys.current.set(p.id, key)
                }
                void run(async () => {
                  await api('/me/orders', 'POST', { planId: p.id }, key)
                  keys.current.delete(p.id)
                }, 'Order created. Payment confirmation is still required.')
              }}
            >
              {p.priceMinor === 0
                ? 'Contact the studio for free access'
                : capabilities?.simulated
                  ? 'Create test order'
                  : 'Create order'}
            </button>
          </article>
        ))}
      </div>
    </section>
  )
}
