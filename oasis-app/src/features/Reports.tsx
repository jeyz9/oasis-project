import { useEffect, useState } from 'react'
import { api, errorMessage } from '../shared/api'
interface Sale {
  period: string
  packages: number
  grossMinor: number
  refundMinor: number
  netMinor: number
}
interface Sales {
  mode: string
  grouping: string
  currency: string
  periods: Sale[]
  unreconciledMinor: number
}
interface Trainer {
  trainerId: string
  trainerName: string
  classesTaught: number
  capacity: number
  attended: number
  noShows: number
  utilization: number | null
  commissionMinor: number
  currency: string
}
const day = () =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Bangkok' }).format(
    new Date(),
  )
const after = (value: string) =>
  new Date(Date.parse(`${value}T12:00:00Z`) + 86400000)
    .toISOString()
    .slice(0, 10)
const money = (value: number) =>
  new Intl.NumberFormat('en-TH', { style: 'currency', currency: 'THB' }).format(
    value / 100,
  )
export function BusinessReports({
  admin,
  version,
}: {
  admin: boolean
  version: number
}) {
  const [from, setFrom] = useState(() => day().slice(0, 8) + '01'),
    [to, setTo] = useState(() => after(day())),
    [mode, setMode] = useState('REAL'),
    [grouping, setGrouping] = useState('DAY'),
    [sales, setSales] = useState<Sales | null>(null),
    [trainers, setTrainers] = useState<Trainer[]>([]),
    [error, setError] = useState(''),
    [loadedKey, setLoadedKey] = useState('')
  const requestKey = `${from}:${to}:${mode}:${grouping}:${admin}:${version}`
  const loading = loadedKey !== requestKey
  useEffect(() => {
    let current = true
    const dates = `from=${from}&to=${to}`
    Promise.all([
      admin
        ? api<Sales>(
            `/admin/reports/sales?${dates}&mode=${mode}&grouping=${grouping}`,
          )
        : Promise.resolve(null),
      api<Trainer[]>(
        `${admin ? '/admin/reports/trainers' : '/trainer/commissions'}?${dates}`,
      ),
    ])
      .then(([s, t]) => {
        if (current) {
          setSales(s)
          setTrainers(t)
          setError('')
        }
      })
      .catch((e) => {
        if (current) {
          setSales(null)
          setTrainers([])
          setError(errorMessage(e))
        }
      })
      .finally(() => {
        if (current) setLoadedKey(requestKey)
      })
    return () => {
      current = false
    }
  }, [from, to, mode, grouping, admin, version, requestKey])
  return (
    <section>
      <h2>
        {admin ? 'Business reports' : 'Your teaching & commission summary'}
      </h2>
      <p className="muted">
        Bangkok date periods include the start and exclude the end. Maximum
        range: 366 days. Cash sales use accepted captures minus confirmed
        refunds in their respective periods. Operational metrics count completed
        classes only.
      </p>
      <div className="toolbar panel">
        <label>
          Report from
          <input
            type="date"
            value={from}
            onChange={(e) => {
              if (e.target.value) setFrom(e.target.value)
            }}
          />
        </label>
        <label>
          Report until (exclusive)
          <input
            type="date"
            value={to}
            onChange={(e) => {
              if (e.target.value) setTo(e.target.value)
            }}
          />
        </label>
        {admin && (
          <>
            <label>
              Sales data
              <select
                aria-label="Sales data"
                value={mode}
                onChange={(e) => setMode(e.target.value)}
              >
                <option value="REAL">Real verified payments only</option>
                <option value="SIMULATED">Simulation data only</option>
              </select>
            </label>
            <label>
              Sales grouping
              <select
                aria-label="Sales grouping"
                value={grouping}
                onChange={(e) => setGrouping(e.target.value)}
              >
                <option value="DAY">Daily</option>
                <option value="MONTH">Monthly</option>
              </select>
            </label>
          </>
        )}
      </div>
      {loading && <p role="status">Loading report…</p>}
      {!loading && error && (
        <p role="alert" className="alert error">
          {error}
        </p>
      )}
      {!loading && sales && (
        <section>
          <h3>
            Package sales ·{' '}
            {sales.mode === 'REAL' ? 'real data' : 'SIMULATION ONLY'}
          </h3>
          <p className="muted">
            Unreconciled captures excluded from sales:{' '}
            {money(sales.unreconciledMinor)}. Complimentary access is not a
            sale. This is cash movement, not tax/accounting revenue recognition.
          </p>
          <div className="table-wrap panel">
            <table>
              <thead>
                <tr>
                  <th>Period start</th>
                  <th>Packages</th>
                  <th>Gross</th>
                  <th>Refunds</th>
                  <th>Net</th>
                </tr>
              </thead>
              <tbody>
                {sales.periods.map((s) => (
                  <tr key={s.period}>
                    <td>{s.period}</td>
                    <td>{s.packages}</td>
                    <td>{money(s.grossMinor)}</td>
                    <td>{money(s.refundMinor)}</td>
                    <td>{money(s.netMinor)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
      <h3 className="spaced">Completed classes & fixed teaching fees</h3>
      <p className="muted">
        Attendance utilization = attended bookings ÷ completed-session capacity.
        Cancelled sessions are excluded. Commission = each completed class’s
        snapshotted fixed fee; this is a summary, not a payout confirmation.
      </p>
      {!loading && !error && !trainers.length && (
        <p className="empty panel">No completed classes in this period.</p>
      )}
      <div className="table-wrap panel">
        <table>
          <thead>
            <tr>
              <th>Trainer</th>
              <th>Classes taught</th>
              <th>Attended / capacity</th>
              <th>No-shows</th>
              <th>Utilization</th>
              <th>Commission</th>
            </tr>
          </thead>
          <tbody>
            {(loading ? [] : trainers).map((t) => (
              <tr key={t.trainerId}>
                <td>{t.trainerName}</td>
                <td>{t.classesTaught}</td>
                <td>
                  {t.attended} / {t.capacity}
                </td>
                <td>{t.noShows}</td>
                <td>
                  {t.utilization === null
                    ? '—'
                    : `${(t.utilization * 100).toFixed(1)}%`}
                </td>
                <td>{money(t.commissionMinor)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
