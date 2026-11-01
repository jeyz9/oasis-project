import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, errorMessage } from '../shared/api'
import type { Plan, RunAction, Service } from '../shared/api'

interface Customer {
  id: string
  displayName: string
  email: string
}
interface AdminGrant {
  id: string
  customerName: string
  name: string
  status: string
  source: string
  entitlementMode: string
  remainingCredits: number | null
  validUntil: string
}
const empty = {
  name: '',
  period: 'MONTH',
  entitlementMode: 'CREDITS',
  credits: 10,
  validityDays: 90,
  price: '1500.00',
  active: true,
  serviceIds: [] as string[],
}
const localNow = () =>
  new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'Asia/Bangkok',
    dateStyle: 'short',
    timeStyle: 'short',
  })
    .format(new Date())
    .replace(' ', 'T')

export function MembershipAdmin({
  services,
  run,
  busy,
  version,
}: {
  services: Service[]
  run: RunAction
  busy: boolean
  version: number
}) {
  const [plans, setPlans] = useState<Plan[]>([])
  const [customers, setCustomers] = useState<Customer[]>([])
  const [grants, setGrants] = useState<AdminGrant[]>([])
  const [error, setError] = useState('')
  const [form, setForm] = useState(empty)
  const [editing, setEditing] = useState('')
  const [start, setStart] = useState(localNow)
  const [issueForm, setIssueForm] = useState({
    customerId: '',
    planId: '',
    reason: '',
  })
  const [stateForm, setStateForm] = useState({
    grantId: '',
    status: 'SUSPENDED',
    reason: '',
  })
  const keys = useRef(new Map<string, string>())
  useEffect(() => {
    let current = true
    Promise.all([
      api<Plan[]>('/admin/membership-plans'),
      api<Customer[]>('/admin/customers'),
      api<AdminGrant[]>('/admin/memberships'),
    ])
      .then(([p, c, g]) => {
        if (current) {
          setPlans(p)
          setCustomers(c)
          setGrants(g)
          setError('')
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [version])
  function save(e: FormEvent) {
    e.preventDefault()
    const [whole, fraction = ''] = form.price.split('.')
    const priceMinor = Number(whole) * 100 + Number(fraction.padEnd(2, '0'))
    void run(async () => {
      await api(
        editing
          ? `/admin/membership-plans/${editing}`
          : '/admin/membership-plans',
        editing ? 'PUT' : 'POST',
        {
          name: form.name,
          period: form.period,
          entitlementMode: form.entitlementMode,
          credits: form.entitlementMode === 'CREDITS' ? form.credits : null,
          validityDays: form.period === 'PACKAGE' ? form.validityDays : null,
          priceMinor,
          active: form.active,
          serviceIds: form.serviceIds,
        },
      )
      setForm(empty)
      setEditing('')
    }, 'Membership plan saved. Existing memberships keep their original terms.')
  }
  function issue(e: FormEvent) {
    e.preventDefault()
    const payload = {
      ...issueForm,
      startsAt: new Date(`${start}:00+07:00`).toISOString(),
    }
    const fingerprint = JSON.stringify(payload)
    let key = keys.current.get(fingerprint)
    if (!key) {
      key = crypto.randomUUID()
      keys.current.set(fingerprint, key)
    }
    void run(async () => {
      await api('/admin/memberships/complimentary', 'POST', payload, key)
      keys.current.delete(fingerprint)
      setIssueForm({ customerId: '', planId: '', reason: '' })
    }, 'Complimentary membership issued. No payment was collected.')
  }
  return (
    <section className="spaced" aria-labelledby="membership-admin-title">
      <h3 id="membership-admin-title">Membership plans & access</h3>
      {error && (
        <p role="alert" className="alert error">
          {error}
        </p>
      )}
      <div className="admin-grid">
        <form className="panel" onSubmit={save}>
          <h3>{editing ? 'Edit membership plan' : 'Create membership plan'}</h3>
          <label>
            Plan name
            <input
              required
              maxLength={120}
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
            />
          </label>
          <label>
            Membership period
            <select
              aria-label="Membership period"
              value={form.period}
              onChange={(e) => setForm({ ...form, period: e.target.value })}
            >
              <option value="MONTH">One calendar month</option>
              <option value="YEAR">One calendar year</option>
              <option value="PACKAGE">Prepaid package</option>
            </select>
          </label>
          <label>
            Session access
            <select
              aria-label="Session access"
              value={form.entitlementMode}
              onChange={(e) =>
                setForm({ ...form, entitlementMode: e.target.value })
              }
            >
              <option value="CREDITS">Session credits</option>
              <option value="UNLIMITED">Unlimited sessions</option>
            </select>
          </label>
          {form.entitlementMode === 'CREDITS' && (
            <label>
              Included sessions
              <input
                type="number"
                required
                min={1}
                max={10000}
                value={form.credits}
                onChange={(e) =>
                  setForm({ ...form, credits: Number(e.target.value) })
                }
              />
            </label>
          )}
          {form.period === 'PACKAGE' && (
            <label>
              Validity (days)
              <input
                type="number"
                required
                min={1}
                max={3660}
                value={form.validityDays}
                onChange={(e) =>
                  setForm({ ...form, validityDays: Number(e.target.value) })
                }
              />
            </label>
          )}
          <label>
            Listed price (THB)
            <input
              required
              inputMode="decimal"
              pattern="[0-9]+([.][0-9]{1,2})?"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })}
            />
          </label>
          <fieldset>
            <legend>Included services</legend>
            {services.map((s) => (
              <label className="checkbox" key={s.id}>
                <input
                  type="checkbox"
                  checked={form.serviceIds.includes(s.id)}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      serviceIds: e.target.checked
                        ? [...form.serviceIds, s.id]
                        : form.serviceIds.filter((id) => id !== s.id),
                    })
                  }
                />
                {s.name}
              </label>
            ))}
          </fieldset>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={form.active}
              onChange={(e) => setForm({ ...form, active: e.target.checked })}
            />
            Available plan
          </label>
          <button
            className="button primary"
            disabled={busy || !form.serviceIds.length}
          >
            Save membership plan
          </button>
          {editing && (
            <button
              type="button"
              onClick={() => {
                setEditing('')
                setForm(empty)
              }}
            >
              Stop editing plan
            </button>
          )}
        </form>
        <div className="stack">
          <form className="panel" onSubmit={issue}>
            <h3>Issue complimentary membership</h3>
            <p className="muted">
              Grant free access with an audit reason. This does not record a
              sale or payment.
            </p>
            <label>
              Customer
              <select
                aria-label="Customer"
                required
                value={issueForm.customerId}
                onChange={(e) =>
                  setIssueForm({ ...issueForm, customerId: e.target.value })
                }
              >
                <option value="">Choose a customer</option>
                {customers.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.displayName} · {c.email}
                  </option>
                ))}
              </select>
            </label>
            <label>
              Membership plan
              <select
                aria-label="Membership plan"
                required
                value={issueForm.planId}
                onChange={(e) =>
                  setIssueForm({ ...issueForm, planId: e.target.value })
                }
              >
                <option value="">Choose a plan</option>
                {plans
                  .filter((p) => p.active)
                  .map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
              </select>
            </label>
            <label>
              Activation (Bangkok time)
              <input
                type="datetime-local"
                required
                value={start}
                onChange={(e) => setStart(e.target.value)}
              />
            </label>
            <label>
              Grant reason
              <input
                required
                maxLength={250}
                value={issueForm.reason}
                onChange={(e) =>
                  setIssueForm({ ...issueForm, reason: e.target.value })
                }
              />
            </label>
            <button className="button primary" disabled={busy}>
              Issue free membership
            </button>
          </form>
          <form
            className="panel"
            onSubmit={(e) => {
              e.preventDefault()
              void run(async () => {
                await api(
                  `/admin/memberships/${stateForm.grantId}/status`,
                  'PUT',
                  { status: stateForm.status, reason: stateForm.reason },
                )
                setStateForm({ grantId: '', status: 'SUSPENDED', reason: '' })
              }, 'Membership status updated. Existing bookings are unchanged.')
            }}
          >
            <h3>Manage membership status</h3>
            <p className="muted">
              Suspension blocks new bookings without extending expiry.
              Revocation is permanent. Existing bookings remain eligible for
              normal cancellation.
            </p>
            <label>
              Member package
              <select
                aria-label="Member package"
                required
                value={stateForm.grantId}
                onChange={(e) =>
                  setStateForm({ ...stateForm, grantId: e.target.value })
                }
              >
                <option value="">Choose a membership</option>
                {grants.map((g) => (
                  <option key={g.id} value={g.id}>
                    {g.customerName} · {g.name} · {g.status}
                  </option>
                ))}
              </select>
            </label>
            <label>
              New status
              <select
                aria-label="New status"
                value={stateForm.status}
                onChange={(e) =>
                  setStateForm({ ...stateForm, status: e.target.value })
                }
              >
                <option value="SUSPENDED">Suspend</option>
                <option value="ACTIVE">Reactivate</option>
                <option value="REVOKED">Revoke permanently</option>
              </select>
            </label>
            <label>
              Status change reason
              <input
                required
                maxLength={250}
                value={stateForm.reason}
                onChange={(e) =>
                  setStateForm({ ...stateForm, reason: e.target.value })
                }
              />
            </label>
            <button disabled={busy}>Change membership status</button>
          </form>
        </div>
      </div>
      <div className="stack">
        {plans.map((p) => (
          <article className="panel compact-row" key={p.id}>
            <div>
              <strong>{p.name}</strong>
              <p>
                {p.period} ·{' '}
                {p.entitlementMode === 'UNLIMITED'
                  ? 'Unlimited'
                  : `${p.credits} sessions`}{' '}
                ·{' '}
                {new Intl.NumberFormat('en-TH', {
                  style: 'currency',
                  currency: p.currency,
                }).format(p.priceMinor / 100)}{' '}
                · {p.active ? 'Available' : 'Inactive'}
              </p>
            </div>
            <button
              onClick={() => {
                setEditing(p.id)
                setForm({
                  name: p.name,
                  period: p.period,
                  entitlementMode: p.entitlementMode,
                  credits: p.credits ?? 10,
                  validityDays: p.validityDays ?? 90,
                  price: (p.priceMinor / 100).toFixed(2),
                  active: p.active,
                  serviceIds: p.serviceIds,
                })
              }}
            >
              Edit plan {p.name}
            </button>
          </article>
        ))}
      </div>
    </section>
  )
}
