import { useEffect, useRef, useState } from 'react'
import { api, errorMessage } from '../shared/api'
import type { RunAction } from '../shared/api'

export interface PaymentCapabilities {
  provider: string
  enabled: boolean
  simulated: boolean
  methods: string[]
}
interface Order {
  id: string
  customerId: string
  customerName: string
  name: string
  amountMinor: number
  currency: string
  status: string
  simulated: boolean
  createdAt: string
  expiresAt: string
  paidAt: string | null
  grantId: string | null
  receiptId: string | null
  paymentId: string | null
  refundStatus: string | null
}
interface Receipt {
  id: string
  receiptNumber: string
  orderId: string
  customerName: string
  planName: string
  amountMinor: number
  currency: string
  simulated: boolean
  issuedAt: string
  paymentReference: string
}
interface Refund {
  id: string
  paymentId: string
  amountMinor: number
  state: string
  reason: string
}
interface PaymentEvent {
  id: string
  orderId: string
  provider: string
  eventType: string
  state: string
  code: string
  receivedAt: string
}
const amount = (value: number, currency = 'THB') =>
  new Intl.NumberFormat('en-TH', { style: 'currency', currency }).format(
    value / 100,
  )
const date = (value: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))

export function PaymentNotice({
  capabilities,
}: {
  capabilities: PaymentCapabilities | null
}) {
  if (!capabilities) return null
  return (
    <p className="alert payment-notice">
      {capabilities.simulated
        ? 'Local payment simulation only. No real card, QR payment or money transfer is available. Test receipts are not proof of payment.'
        : !capabilities.enabled
          ? 'Payments are not configured. Orders remain pending until a configured provider confirms payment on the server.'
          : 'Memberships activate only after verified server confirmation. Never enter payment details directly into Oasis.'}
    </p>
  )
}

export function CustomerPayments({
  run,
  busy,
  version,
}: {
  run: RunAction
  busy: boolean
  version: number
}) {
  const [orders, setOrders] = useState<Order[]>([])
  const [caps, setCaps] = useState<PaymentCapabilities | null>(null)
  const [error, setError] = useState('')
  const [receipt, setReceipt] = useState<Receipt | null>(null)
  const [checkout, setCheckout] = useState<
    Record<
      string,
      { simulated: boolean; checkoutUrl: string | null; methods: string[] }
    >
  >({})
  const [poll, setPoll] = useState(0)
  useEffect(() => {
    let current = true
    Promise.all([
      api<Order[]>('/me/orders?limit=100'),
      api<PaymentCapabilities>('/payments/capabilities'),
    ])
      .then(([list, c]) => {
        if (current) {
          setOrders(list)
          setCaps(c)
          setError('')
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [version, poll])
  const awaiting = orders.some(
    (o) => o.status === 'PENDING' || o.refundStatus === 'PENDING',
  )
  useEffect(() => {
    if (!awaiting) return
    const timer = window.setInterval(() => setPoll((n) => n + 1), 5000)
    return () => window.clearInterval(timer)
  }, [awaiting])
  return (
    <section className="spaced" aria-labelledby="orders-title">
      <div className="section-heading">
        <h2 id="orders-title">Orders & payment history</h2>
        <button disabled={busy} onClick={() => setPoll((n) => n + 1)}>
          Refresh payment status
        </button>
      </div>
      <PaymentNotice capabilities={caps} />
      {error && (
        <p role="alert" className="alert error">
          {error}
        </p>
      )}
      {!orders.length && (
        <p className="empty panel">
          No orders yet. Choose a membership plan to create one.
        </p>
      )}
      <div className="stack">
        {orders.map((o) => (
          <article className="panel order-card" key={o.id} data-order-id={o.id}>
            <div className="section-heading">
              <div>
                <span className="tag">{o.status.toLowerCase()}</span>
                {o.simulated && <span className="tag">simulation</span>}
                <h3>{o.name}</h3>
              </div>
              <strong>{amount(o.amountMinor, o.currency)}</strong>
            </div>
            <p className="muted">
              Order {o.id} · {date(o.createdAt)}
            </p>
            {o.status === 'PENDING' && (
              <p className="muted">
                Awaiting verified confirmation. This order expires{' '}
                {date(o.expiresAt)}.
              </p>
            )}
            {o.status === 'REVIEW' && (
              <p className="alert error">
                Payment requires staff reconciliation. No membership was
                activated for this order.
              </p>
            )}
            {o.refundStatus && <p>Refund: {o.refundStatus.toLowerCase()}</p>}
            <div className="actions">
              {o.status === 'PENDING' && (
                <>
                  <button
                    disabled={busy || !caps?.enabled}
                    onClick={() =>
                      void run(
                        async () => {
                          const result = await api<{
                            simulated: boolean
                            checkoutUrl: string | null
                            methods: string[]
                          }>(`/me/orders/${o.id}/checkout`, 'POST')
                          setCheckout((previous) => ({
                            ...previous,
                            [o.id]: result,
                          }))
                        },
                        o.simulated
                          ? 'Test checkout ready. No money will be collected.'
                          : 'Checkout prepared.',
                        false,
                      )
                    }
                  >
                    Prepare checkout
                  </button>
                  <button
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await api(`/me/orders/${o.id}/cancel`, 'POST')
                      }, 'Order cancelled.')
                    }
                  >
                    Cancel order
                  </button>
                </>
              )}
              {o.receiptId && (
                <button
                  disabled={busy}
                  onClick={() =>
                    void run(
                      async () => {
                        setReceipt(
                          await api<Receipt>(`/me/receipts/${o.receiptId}`),
                        )
                      },
                      '',
                      false,
                    )
                  }
                >
                  View receipt
                </button>
              )}
            </div>
            {o.status === 'PENDING' && checkout[o.id]?.simulated && (
              <div className="simulation-controls">
                <p>
                  SIMULATION · No card details or real PromptPay QR are used.
                </p>
                {checkout[o.id].methods.map((method) => (
                  <button
                    key={method}
                    disabled={busy}
                    onClick={() =>
                      void run(async () => {
                        await api(
                          `/local-payment-simulator/orders/${o.id}/confirm`,
                          'POST',
                          { method },
                        )
                      }, 'Simulated confirmation accepted. No money was collected.')
                    }
                  >
                    Simulate {method === 'CARD' ? 'card' : 'PromptPay'}{' '}
                    confirmation
                  </button>
                ))}
              </div>
            )}
            {o.status === 'PENDING' &&
              checkout[o.id]?.checkoutUrl &&
              checkout[o.id].checkoutUrl?.startsWith('https://') && (
                <a
                  href={checkout[o.id].checkoutUrl ?? undefined}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Continue to secure provider checkout
                </a>
              )}
          </article>
        ))}
      </div>
      {receipt && (
        <section className="panel receipt" aria-labelledby="receipt-title">
          <div className="section-heading">
            <h3 id="receipt-title">
              {receipt.simulated
                ? 'SIMULATED RECEIPT — NO MONEY COLLECTED'
                : 'Payment receipt'}
            </h3>
            <button onClick={() => setReceipt(null)}>Close receipt</button>
          </div>
          <p>Oasis Wellness Hub</p>
          <dl>
            <div>
              <dt>Number</dt>
              <dd>{receipt.receiptNumber}</dd>
            </div>
            <div>
              <dt>Customer</dt>
              <dd>{receipt.customerName}</dd>
            </div>
            <div>
              <dt>Membership</dt>
              <dd>{receipt.planName}</dd>
            </div>
            <div>
              <dt>Amount</dt>
              <dd>{amount(receipt.amountMinor, receipt.currency)}</dd>
            </div>
            <div>
              <dt>Issued</dt>
              <dd>{date(receipt.issuedAt)}</dd>
            </div>
            <div>
              <dt>Payment reference</dt>
              <dd>{receipt.paymentReference}</dd>
            </div>
          </dl>
          <p className="muted">
            {receipt.simulated
              ? 'Synthetic test transaction. This document is not evidence of a real payment.'
              : 'Issued from verified server transaction data.'}{' '}
            This is not a tax invoice. Refunds are shown separately in payment
            history.
          </p>
          <button onClick={() => window.print()}>Print receipt</button>
        </section>
      )}
    </section>
  )
}

export function PaymentAdmin({
  run,
  busy,
  version,
}: {
  run: RunAction
  busy: boolean
  version: number
}) {
  const [orders, setOrders] = useState<Order[]>([])
  const [events, setEvents] = useState<PaymentEvent[]>([])
  const [refunds, setRefunds] = useState<Refund[]>([])
  const [caps, setCaps] = useState<PaymentCapabilities | null>(null)
  const [error, setError] = useState('')
  const keys = useRef(new Map<string, string>())
  useEffect(() => {
    let current = true
    Promise.all([
      api<Order[]>('/admin/orders'),
      api<PaymentEvent[]>('/admin/payment-events'),
      api<Refund[]>('/admin/refunds'),
      api<PaymentCapabilities>('/payments/capabilities'),
    ])
      .then(([o, e, r, c]) => {
        if (current) {
          setOrders(o)
          setEvents(e)
          setRefunds(r)
          setCaps(c)
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
  return (
    <section className="spaced payment-admin">
      <h3>Payments & reconciliation</h3>
      <PaymentNotice capabilities={caps} />
      {error && <p role="alert">{error}</p>}
      <p className="muted">
        Latest 200 records. Full refunds are supported only for unused
        memberships. A refund request suspends access until verified
        confirmation; failed refunds stay suspended for review. Used/partly
        consumed packages require a separate policy.
      </p>
      <div className="stack">
        {orders.map((o) => (
          <article
            className="panel order-card"
            key={o.id}
            data-admin-order-id={o.id}
          >
            <div className="section-heading">
              <div>
                <h4>
                  {o.customerName} · {o.name}
                </h4>
                <p className="muted">Order {o.id}</p>
              </div>
              <span>
                {amount(o.amountMinor, o.currency)} · {o.status}{' '}
                {o.simulated ? '· simulation' : ''}
              </span>
            </div>
            {o.status === 'PAID' && o.paymentId && !o.refundStatus && (
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  const reason = String(
                    new FormData(e.currentTarget).get('reason'),
                  )
                  const fingerprint = `${o.paymentId}:${reason}`
                  let key = keys.current.get(fingerprint)
                  if (!key) {
                    key = crypto.randomUUID()
                    keys.current.set(fingerprint, key)
                  }
                  void run(async () => {
                    await api(
                      '/admin/refunds',
                      'POST',
                      { paymentId: o.paymentId, reason },
                      key,
                    )
                    keys.current.delete(fingerprint)
                  }, 'Refund requested. Membership access is suspended pending confirmation.')
                }}
              >
                <label>
                  Refund reason for {o.customerName}
                  <input name="reason" required maxLength={250} />
                </label>
                <button disabled={busy}>
                  Request full unused-membership refund
                </button>
              </form>
            )}
            {o.status === 'REVIEW' && (
              <p className="alert error">
                Requires reconciliation. There is no manual mark-paid action.
              </p>
            )}
            {o.refundStatus && (
              <p>Refund status: {o.refundStatus.toLowerCase()}</p>
            )}
          </article>
        ))}
      </div>
      <div className="stack">
        {refunds.map((r) => (
          <article className="panel order-card" key={r.id}>
            <h4>Refund {r.id}</h4>
            <p>
              {amount(r.amountMinor)} · {r.state} · {r.reason}
            </p>
            {r.state === 'PENDING' && (
              <button
                disabled={busy || !caps?.enabled}
                onClick={() =>
                  void run(
                    async () => {
                      await api(`/admin/refunds/${r.id}/dispatch`, 'POST')
                    },
                    caps?.simulated
                      ? 'Refund dispatch prepared in the simulator. No money transferred.'
                      : 'Refund dispatch submitted. Awaiting verified confirmation.',
                  )
                }
              >
                Prepare refund dispatch
              </button>
            )}
            {r.state === 'PENDING' && caps?.simulated && (
              <div className="simulation-controls">
                <p>SIMULATION · No funds are transferred.</p>
                <button
                  disabled={busy}
                  onClick={() =>
                    void run(async () => {
                      await api(
                        `/local-payment-simulator/refunds/${r.id}/confirm`,
                        'POST',
                        { succeeded: true },
                      )
                    }, 'Simulated refund confirmed. No real money was transferred.')
                  }
                >
                  Simulate refund success
                </button>
                <button
                  disabled={busy}
                  onClick={() =>
                    void run(async () => {
                      await api(
                        `/local-payment-simulator/refunds/${r.id}/confirm`,
                        'POST',
                        { succeeded: false },
                      )
                    }, 'Simulated refund failed. Access remains suspended for review.')
                  }
                >
                  Simulate refund failure
                </button>
              </div>
            )}
          </article>
        ))}
      </div>
      <h4 className="spaced">Verified event results</h4>
      <div className="table-wrap panel">
        <table>
          <thead>
            <tr>
              <th>Received (Bangkok)</th>
              <th>Order</th>
              <th>Event</th>
              <th>Result</th>
            </tr>
          </thead>
          <tbody>
            {events.map((e) => (
              <tr key={e.id}>
                <td>{date(e.receivedAt)}</td>
                <td>{e.orderId}</td>
                <td>{e.eventType}</td>
                <td>
                  {e.state} · {e.code}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
