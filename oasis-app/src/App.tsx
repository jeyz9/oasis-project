import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { api, ApiError, errorMessage } from './shared/api'
import type {
  Attendee,
  Booking,
  Entry,
  Grant,
  Me,
  Service,
  Session,
  Trainer,
} from './shared/api'
import './App.css'
import { MembershipAdmin } from './features/MembershipAdmin'
import { TrainerAvailability, SessionTraining } from './features/TrainerTools'
import { CustomerReviews } from './features/CustomerReviews'
import { PlanCatalogue } from './features/PlanCatalogue'
import { CustomerPayments, PaymentAdmin } from './features/Payments'
import { Inbox } from './features/Notifications'
import { Administration, RoomSuggestions } from './features/Administration'
import { BusinessReports } from './features/Reports'

const zone = 'Asia/Bangkok'
const today = () =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: zone }).format(new Date())
const addDays = (day: string, amount: number) =>
  new Date(Date.parse(`${day}T12:00:00Z`) + amount * 86400000)
    .toISOString()
    .slice(0, 10)
const dateTime = (value: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
const time = (value: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: zone,
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
type Tab =
  | 'schedule'
  | 'bookings'
  | 'membership'
  | 'trainer'
  | 'admin'
  | 'notifications'
  | 'reports'
  | 'administration'
const emptyService = {
  name: '',
  description: '',
  category: 'Yoga',
  kind: 'GROUP',
  durationMinutes: 60,
  difficulty: 'ALL_LEVELS',
  capacity: 10,
  active: true,
}
const emptySession = {
  serviceId: '',
  trainerId: '',
  location: '',
  startsAt: '',
  capacity: 10,
  state: 'PUBLISHED',
}

function App() {
  const [me, setMe] = useState<Me | null>(null)
  const [ready, setReady] = useState(false)
  const [now, setNow] = useState(Date.now)
  const [tab, setTab] = useState<Tab>('schedule')
  const [from, setFrom] = useState(today)
  const [days, setDays] = useState(7)
  const [sessions, setSessions] = useState<Session[]>([])
  const [services, setServices] = useState<Service[]>([])
  const [trainers, setTrainers] = useState<Trainer[]>([])
  const [bookings, setBookings] = useState<Booking[]>([])
  const [grants, setGrants] = useState<Grant[]>([])
  const [entries, setEntries] = useState<Entry[]>([])
  const [selectedGrant, setSelectedGrant] = useState('')
  const [attendees, setAttendees] = useState<Record<string, Attendee[]>>({})
  const [busy, setBusy] = useState(false)
  const [loadedKey, setLoadedKey] = useState('')
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [reload, setReload] = useState(0)
  const [auth, setAuth] = useState<'login' | 'register' | null>(null)
  const [serviceForm, setServiceForm] = useState(emptyService)
  const [serviceId, setServiceId] = useState('')
  const [sessionForm, setSessionForm] = useState(emptySession)
  const [sessionId, setSessionId] = useState('')
  const [cancelTarget, setCancelTarget] = useState('')
  const keys = useRef(new Map<string, string>())
  const customer = me?.roles.includes('CUSTOMER') ?? false
  const admin = me?.roles.includes('ADMIN') ?? false
  const trainer = me?.roles.includes('TRAINER') ?? false
  const dataKey = `${me?.id ?? 'public'}:${me?.roles.join(',') ?? ''}:${tab}:${from}:${days}:${reload}`
  const loading = ready && loadedKey !== dataKey
  const refresh = useCallback(() => setReload((n) => n + 1), [])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  useEffect(() => {
    let current = true
    api<Me>('/me')
      .then((user) => {
        if (current) setMe(user)
      })
      .catch((e) => {
        if (current && !(e instanceof ApiError && e.status === 401))
          setError(errorMessage(e))
      })
      .finally(() => {
        if (current) setReady(true)
      })
    return () => {
      current = false
    }
  }, [])

  useEffect(() => {
    if (!ready) return
    let current = true
    const dateQuery = `?from=${from}&to=${addDays(from, days)}`
    const prefix =
      tab === 'admin' && admin
        ? '/admin'
        : tab === 'trainer' && trainer
          ? '/trainer'
          : ''
    Promise.all([
      api<Session[]>(`${prefix}/sessions${dateQuery}`),
      admin ? api<Service[]>('/admin/services') : api<Service[]>('/services'),
      customer ? api<Booking[]>('/me/bookings?limit=100') : Promise.resolve([]),
      customer
        ? api<Grant[]>('/me/memberships?limit=100')
        : Promise.resolve([]),
      customer && tab === 'membership'
        ? api<Entry[]>('/me/credit-ledger?limit=100')
        : Promise.resolve([]),
      admin ? api<Trainer[]>('/admin/trainers') : Promise.resolve([]),
    ])
      .then(
        ([
          nextSessions,
          nextServices,
          nextBookings,
          nextGrants,
          nextEntries,
          nextTrainers,
        ]) => {
          if (!current) return
          setAttendees({})
          setSessions(nextSessions)
          setServices(nextServices)
          setBookings(nextBookings)
          setGrants(nextGrants)
          setEntries(nextEntries)
          setTrainers(nextTrainers)
          setSelectedGrant((previous) =>
            nextGrants.some((g) => g.id === previous)
              ? previous
              : (nextGrants.find(
                  (g) =>
                    g.status === 'ACTIVE' &&
                    (g.entitlementMode === 'UNLIMITED' ||
                      (g.remainingCredits ?? 0) > 0) &&
                    Date.parse(g.validFrom) <= Date.now() &&
                    Date.parse(g.validUntil) > Date.now(),
                )?.id ?? ''),
          )
        },
      )
      .catch((e) => {
        if (current) {
          setError(errorMessage(e))
          setSessions([])
        }
      })
      .finally(() => {
        if (current) setLoadedKey(dataKey)
      })
    return () => {
      current = false
    }
  }, [
    ready,
    from,
    days,
    tab,
    customer,
    admin,
    trainer,
    reload,
    me?.id,
    dataKey,
  ])

  async function act(
    action: () => Promise<void>,
    success = '',
    revalidate = true,
  ) {
    if (busy) return
    setBusy(true)
    setError('')
    setNotice('')
    try {
      await action()
      setNotice(success)
      if (revalidate) refresh()
    } catch (e) {
      setError(errorMessage(e))
      refresh()
    } finally {
      setBusy(false)
    }
  }
  function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const fields = new FormData(event.currentTarget)
    const payload = {
      email: String(fields.get('email')),
      password: String(fields.get('password')),
    }
    void act(
      async () => {
        if (auth === 'register') {
          await api('/auth/register', 'POST', {
            ...payload,
            displayName: String(fields.get('displayName')),
          })
        }
        setMe(await api<Me>('/auth/login', 'POST', payload))
        setAuth(null)
        setTab('schedule')
      },
      auth === 'register'
        ? 'Your account is ready. Welcome to Oasis.'
        : 'Welcome back.',
    )
  }
  function book(session: Session) {
    const identity = `${session.id}:${selectedGrant}`
    let key = keys.current.get(identity)
    if (!key) {
      key = crypto.randomUUID()
      keys.current.set(identity, key)
    }
    void act(
      async () => {
        await api(
          '/me/bookings',
          'POST',
          { sessionId: session.id, grantId: selectedGrant },
          key,
        )
        keys.current.delete(identity)
      },
      grants.find((g) => g.id === selectedGrant)?.entitlementMode ===
        'UNLIMITED'
        ? 'Booking confirmed with your unlimited membership.'
        : 'Booking confirmed. One credit has been used.',
    )
  }
  function saveService(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void act(async () => {
      await api(
        serviceId ? `/admin/services/${serviceId}` : '/admin/services',
        serviceId ? 'PUT' : 'POST',
        serviceForm,
      )
      setServiceForm(emptyService)
      setServiceId('')
    }, 'Service saved.')
  }
  function saveSession(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void act(async () => {
      await api(
        sessionId ? `/admin/sessions/${sessionId}` : '/admin/sessions',
        sessionId ? 'PUT' : 'POST',
        {
          ...sessionForm,
          startsAt: new Date(`${sessionForm.startsAt}:00+07:00`).toISOString(),
        },
      )
      setSessionForm(emptySession)
      setSessionId('')
    }, 'Session saved.')
  }
  const title = {
    schedule: 'Find your next moment of balance.',
    bookings: 'Your time, thoughtfully planned.',
    membership: 'A little progress, every visit.',
    trainer: 'Make room for your community.',
    admin: 'Thoughtful care starts here.',
    notifications: 'A little care, right on time.',
    reports: 'A clear view of your studio.',
    administration: 'Keep your studio running thoughtfully.',
  }[tab]

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to content
      </a>
      <header className="site-header">
        <a
          className="brand"
          href="#main"
          onClick={() => setTab('schedule')}
          aria-label="Oasis home"
        >
          <span className="brand-mark" aria-hidden="true">
            o
          </span>
          <span>
            oasis<small>WELLNESS HUB</small>
          </span>
        </a>
        <nav aria-label="Main navigation">
          <button
            aria-current={tab === 'schedule' ? 'page' : undefined}
            onClick={() => setTab('schedule')}
          >
            Explore classes
          </button>
          {customer && (
            <>
              <button
                aria-current={tab === 'bookings' ? 'page' : undefined}
                onClick={() => setTab('bookings')}
              >
                My bookings
              </button>
              <button
                aria-current={tab === 'membership' ? 'page' : undefined}
                onClick={() => setTab('membership')}
              >
                My membership
              </button>
            </>
          )}
          {trainer && (
            <button
              aria-current={tab === 'trainer' ? 'page' : undefined}
              onClick={() => setTab('trainer')}
            >
              Trainer schedule
            </button>
          )}
          {admin && (
            <button
              aria-current={tab === 'admin' ? 'page' : undefined}
              onClick={() => setTab('admin')}
            >
              Manage studio
            </button>
          )}
          {me && (
            <button
              aria-current={tab === 'notifications' ? 'page' : undefined}
              onClick={() => setTab('notifications')}
            >
              Inbox
            </button>
          )}
          {(admin || trainer) && (
            <button
              aria-current={tab === 'reports' ? 'page' : undefined}
              onClick={() => setTab('reports')}
            >
              {admin ? 'Reports' : 'My performance'}
            </button>
          )}
          {admin && (
            <button
              aria-current={tab === 'administration' ? 'page' : undefined}
              onClick={() => setTab('administration')}
            >
              Administration
            </button>
          )}
        </nav>
        <div className="account">
          {me ? (
            <>
              <span>{me.displayName}</span>
              <button
                disabled={busy}
                onClick={() =>
                  void act(async () => {
                    await api('/auth/logout', 'POST')
                    setMe(null)
                    setTab('schedule')
                    setBookings([])
                    setGrants([])
                    setEntries([])
                    keys.current.clear()
                  }, 'You have signed out.')
                }
              >
                Sign out
              </button>
            </>
          ) : (
            <button
              className="button primary"
              disabled={!ready}
              onClick={() => setAuth('login')}
            >
              Sign in
            </button>
          )}
        </div>
      </header>
      <main id="main">
        <section className="intro">
          <p className="eyebrow">MOVE WELL. FEEL AT HOME.</p>
          <h1>{title}</h1>
          <p>
            Space to breathe. Strength to grow. Discover a practice that feels
            like you.
          </p>
          <span className="timezone">
            All schedules are shown in Bangkok time · UTC+7
          </span>
        </section>
        {error && (
          <div className="alert error" role="alert">
            {error}
            <button
              onClick={() => {
                setError('')
                refresh()
              }}
            >
              Retry
            </button>
          </div>
        )}
        {notice && (
          <div className="alert success" role="status">
            {notice}
          </div>
        )}
        {!ready && <p role="status">Connecting to Oasis…</p>}
        {auth && (
          <section className="auth-panel panel" aria-labelledby="auth-title">
            <div className="section-heading">
              <h2 id="auth-title">
                {auth === 'login' ? 'Welcome back' : 'Begin your practice'}
              </h2>
              <button onClick={() => setAuth(null)}>Close</button>
            </div>
            <form onSubmit={submitAuth}>
              {auth === 'register' && (
                <label>
                  Your name
                  <input
                    name="displayName"
                    autoComplete="name"
                    required
                    maxLength={100}
                  />
                </label>
              )}
              <label>
                Email
                <input
                  type="email"
                  name="email"
                  autoComplete="email"
                  required
                  maxLength={254}
                />
              </label>
              <label>
                Password
                <input
                  type="password"
                  name="password"
                  autoComplete={
                    auth === 'login' ? 'current-password' : 'new-password'
                  }
                  required
                  minLength={auth === 'register' ? 12 : 1}
                  maxLength={72}
                />
              </label>
              {auth === 'register' && (
                <p className="muted">
                  Use at least 12 characters. New accounts start without
                  membership credits.
                </p>
              )}
              <button className="button primary" disabled={busy} type="submit">
                {busy
                  ? 'Please wait…'
                  : auth === 'login'
                    ? 'Sign in to Oasis'
                    : 'Create account'}
              </button>
              <button
                type="button"
                onClick={() => setAuth(auth === 'login' ? 'register' : 'login')}
              >
                {auth === 'login'
                  ? 'New here? Create an account'
                  : 'Already a member? Sign in'}
              </button>
            </form>
          </section>
        )}

        {(tab === 'schedule' || tab === 'trainer' || tab === 'admin') && (
          <section className="toolbar panel" aria-label="Schedule filters">
            <label>
              Starting date
              <input
                type="date"
                value={from}
                required
                onChange={(e) => {
                  if (e.target.value) setFrom(e.target.value)
                }}
              />
            </label>
            <div className="segmented" aria-label="Schedule duration">
              <button aria-pressed={days === 1} onClick={() => setDays(1)}>
                Day
              </button>
              <button aria-pressed={days === 7} onClick={() => setDays(7)}>
                Week
              </button>
            </div>
            <div className="date-navigation">
              <button
                aria-label="Previous period"
                onClick={() => setFrom(addDays(from, -days))}
              >
                ←
              </button>
              <button onClick={() => setFrom(today())}>Today</button>
              <button
                aria-label="Next period"
                onClick={() => setFrom(addDays(from, days))}
              >
                →
              </button>
            </div>
            <button disabled={busy || loading} onClick={refresh}>
              Refresh availability
            </button>
          </section>
        )}
        {loading && <p role="status">Refreshing your studio…</p>}

        {tab === 'schedule' && (
          <>
            {customer && (
              <div className="credit-picker">
                <label>
                  Book with
                  <select
                    aria-label="Membership package"
                    value={selectedGrant}
                    onChange={(e) => setSelectedGrant(e.target.value)}
                  >
                    <option value="">Choose a package</option>
                    {grants.map((g) => (
                      <option
                        key={g.id}
                        value={g.id}
                        disabled={
                          g.status !== 'ACTIVE' ||
                          (g.entitlementMode !== 'UNLIMITED' &&
                            !g.remainingCredits) ||
                          Date.parse(g.validFrom) > now ||
                          Date.parse(g.validUntil) <= now
                        }
                      >
                        {g.name} ·{' '}
                        {g.entitlementMode === 'UNLIMITED'
                          ? 'Unlimited'
                          : `${g.remainingCredits} credits`}
                      </option>
                    ))}
                  </select>
                </label>
                <p className="muted">
                  Credit packages use one credit per booking. Unlimited access
                  uses no credits. Eligibility is checked at confirmation.
                </p>
              </div>
            )}
            <div className="section-heading">
              <h2>Room for your next practice</h2>
              <span className="muted">
                {sessions.length} sessions in this view
              </span>
            </div>
            {!loading && sessions.length === 0 && (
              <div className="empty panel">
                <h3>A little space in the schedule.</h3>
                <p>No published classes in this period. Try another date.</p>
              </div>
            )}
            <div className="class-grid">
              {sessions.map((s) => {
                const booked = bookings.some(
                  (b) => b.sessionId === s.id && b.status === 'CONFIRMED',
                )
                const started = Date.parse(s.startsAt) <= now
                return (
                  <article className="class-card panel" key={s.id}>
                    <div className="class-art" aria-hidden="true">
                      <span>{s.category.slice(0, 1)}</span>
                      <div className="art-ring" />
                    </div>
                    <div className="card-content">
                      <div className="section-heading">
                        <span className="tag">{s.category}</span>
                        <span className="muted">
                          {s.available} of {s.capacity} spots
                        </span>
                      </div>
                      <h3>{s.name}</h3>
                      <p>{s.description}</p>
                      <dl>
                        <div>
                          <dt>When</dt>
                          <dd>
                            {dateTime(s.startsAt)} – {time(s.endsAt)}
                          </dd>
                        </div>
                        <div>
                          <dt>With</dt>
                          <dd>{s.trainerName}</dd>
                        </div>
                        <div>
                          <dt>Where</dt>
                          <dd>{s.location}</dd>
                        </div>
                        <div>
                          <dt>Level</dt>
                          <dd>
                            {s.difficulty.replaceAll('_', ' ').toLowerCase()}
                          </dd>
                        </div>
                      </dl>
                      <p className="policy">
                        Cancel at least {s.cancellationMinutes / 60} hours
                        before the start.
                      </p>
                      {customer ? (
                        <button
                          className="button primary full"
                          disabled={
                            busy ||
                            loading ||
                            booked ||
                            started ||
                            s.available === 0 ||
                            !selectedGrant
                          }
                          onClick={() => book(s)}
                        >
                          {booked
                            ? 'Booked'
                            : started
                              ? 'Class has started'
                              : s.available === 0
                                ? 'Fully booked'
                                : !selectedGrant
                                  ? 'Choose a package to book'
                                  : grants.find((g) => g.id === selectedGrant)
                                        ?.entitlementMode === 'UNLIMITED'
                                    ? 'Book with unlimited access'
                                    : 'Book class · 1 credit'}
                        </button>
                      ) : !me ? (
                        <button
                          className="button primary full"
                          onClick={() => setAuth('login')}
                        >
                          Sign in to book
                        </button>
                      ) : (
                        <p className="muted">
                          Booking requires a customer account.
                        </p>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          </>
        )}

        {tab === 'notifications' && me && (
          <Inbox run={act} busy={busy} version={reload} />
        )}
        {tab === 'reports' && (admin || trainer) && (
          <BusinessReports admin={admin} version={reload} />
        )}
        {tab === 'administration' && admin && me && (
          <Administration me={me} run={act} busy={busy} version={reload} />
        )}

        {tab === 'bookings' && customer && (
          <section>
            <div className="section-heading">
              <h2>My bookings</h2>
              <button onClick={refresh}>Refresh</button>
            </div>
            <p className="muted">
              Showing up to 100 most recent bookings. Eligible credit bookings
              restore their credit on cancellation; unlimited bookings release
              the seat.
            </p>
            {bookings.length === 0 && !loading && (
              <div className="empty panel">
                Your next practice starts with a booking.
              </div>
            )}
            <div className="stack">
              {bookings.map((b) => (
                <article className="booking-row panel" key={b.id}>
                  <div>
                    <span className="tag">{b.status.toLowerCase()}</span>
                    <h3>{b.name}</h3>
                    <p>
                      {dateTime(b.startsAt)} · {b.trainerName} · {b.location}
                    </p>
                    <p className="muted">
                      Cancellation deadline:{' '}
                      {dateTime(
                        new Date(
                          Date.parse(b.startsAt) -
                            b.cancellationMinutes * 60000,
                        ).toISOString(),
                      )}
                    </p>
                  </div>
                  {b.status === 'CONFIRMED' && (
                    <button
                      disabled={
                        busy ||
                        now >
                          Date.parse(b.startsAt) - b.cancellationMinutes * 60000
                      }
                      onClick={() =>
                        void act(
                          async () => {
                            await api(`/me/bookings/${b.id}/cancel`, 'POST')
                          },
                          grants.find((g) => g.id === b.grantId)
                            ?.entitlementMode === 'UNLIMITED'
                            ? 'Booking cancelled. Your unlimited access is unchanged.'
                            : 'Booking cancelled. One credit has been restored.',
                        )
                      }
                    >
                      {now >
                      Date.parse(b.startsAt) - b.cancellationMinutes * 60000
                        ? 'Cancellation closed'
                        : 'Cancel booking'}
                    </button>
                  )}
                </article>
              ))}
            </div>
            <CustomerReviews
              bookings={bookings}
              run={act}
              busy={busy}
              version={reload}
            />
          </section>
        )}

        {tab === 'membership' && customer && (
          <section>
            <h2>My membership</h2>
            <p className="muted">
              Showing up to 100 packages and recent credit entries. Orders below
              activate only after verified server confirmation.
            </p>
            <div className="class-grid">
              {grants.map((g) => (
                <article className="panel membership-card" key={g.id}>
                  <span className="tag">{g.status.toLowerCase()}</span>
                  <h3>{g.name}</h3>
                  <p className="credit-count">
                    {g.entitlementMode === 'UNLIMITED'
                      ? 'Unlimited'
                      : g.remainingCredits}
                    <small>
                      {g.entitlementMode === 'UNLIMITED'
                        ? 'sessions during validity'
                        : 'credits remaining'}
                    </small>
                  </p>
                  <p>{g.usedSessions} sessions booked or used</p>
                  <p>
                    Valid {dateTime(g.validFrom)} to {dateTime(g.validUntil)}
                  </p>
                  {g.source === 'SYNTHETIC_FIXTURE' && (
                    <p className="muted">
                      Local demonstration package · no payment
                    </p>
                  )}
                  {g.source === 'ADMIN_COMPLIMENTARY' && (
                    <p className="muted">
                      Complimentary membership · no payment collected
                    </p>
                  )}
                  {g.source === 'SIMULATED_PAYMENT' && (
                    <p className="muted">
                      Simulated payment membership · no money collected
                    </p>
                  )}
                </article>
              ))}
            </div>
            {grants.length === 0 && (
              <p className="empty panel">
                You do not have a membership package yet.
              </p>
            )}
            <h2 className="spaced">Credit history</h2>
            {entries.length === 0 ? (
              <p>No credit activity yet.</p>
            ) : (
              <div className="table-wrap panel">
                <table>
                  <thead>
                    <tr>
                      <th>Date</th>
                      <th>Activity</th>
                      <th>Credits</th>
                    </tr>
                  </thead>
                  <tbody>
                    {entries.map((e) => (
                      <tr key={e.id}>
                        <td>{dateTime(e.createdAt)}</td>
                        <td>{e.reason}</td>
                        <td>
                          {e.delta > 0 ? '+' : ''}
                          {e.delta}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <PlanCatalogue run={act} busy={busy} />
            <CustomerPayments run={act} busy={busy} version={reload} />
          </section>
        )}

        {tab === 'trainer' && trainer && (
          <section>
            <h2>Your teaching schedule</h2>
            <p className="muted">Only sessions assigned to you are shown.</p>
            <TrainerAvailability
              from={from}
              to={addDays(from, days)}
              run={act}
              busy={busy}
              version={reload}
            />
            {sessions.length === 0 && !loading && (
              <p className="empty panel">
                No assigned sessions in this period.
              </p>
            )}
            <div className="stack">
              {sessions.map((s) => (
                <article className="panel trainer-card" key={s.id}>
                  <span className="tag">{s.state.toLowerCase()}</span>
                  <h3>{s.name}</h3>
                  <p>
                    {dateTime(s.startsAt)} · {s.location} · {s.reservedCount}{' '}
                    attendees
                  </p>
                  <button
                    disabled={busy}
                    onClick={() =>
                      void act(
                        async () => {
                          const list = await api<Attendee[]>(
                            `/trainer/sessions/${s.id}/attendees`,
                          )
                          setAttendees((previous) => ({
                            ...previous,
                            [s.id]: list,
                          }))
                        },
                        '',
                        false,
                      )
                    }
                  >
                    View attendees
                  </button>
                  {attendees[s.id] && (
                    <div aria-live="polite">
                      {attendees[s.id].length ? (
                        <ul>
                          {attendees[s.id].map((a) => (
                            <li key={a.bookingId}>
                              {a.displayName} · {a.status.toLowerCase()}
                            </li>
                          ))}
                        </ul>
                      ) : (
                        <p>No confirmed attendees yet.</p>
                      )}
                    </div>
                  )}
                  <SessionTraining
                    session={s}
                    attendees={attendees[s.id]}
                    run={act}
                    busy={busy}
                    now={now}
                  />
                </article>
              ))}
            </div>
          </section>
        )}

        {tab === 'admin' && admin && (
          <section>
            <h2>Manage your studio</h2>
            <PaymentAdmin run={act} busy={busy} version={reload} />
            <MembershipAdmin
              services={services}
              run={act}
              busy={busy}
              version={reload}
            />
            <div className="admin-grid">
              <form className="panel" onSubmit={saveService}>
                <h3>{serviceId ? 'Edit service' : 'Create service'}</h3>
                <label>
                  Service name
                  <input
                    required
                    maxLength={120}
                    value={serviceForm.name}
                    onChange={(e) =>
                      setServiceForm({ ...serviceForm, name: e.target.value })
                    }
                  />
                </label>
                <label>
                  Description
                  <textarea
                    required
                    maxLength={2000}
                    value={serviceForm.description}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        description: e.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Category
                  <input
                    required
                    maxLength={60}
                    value={serviceForm.category}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        category: e.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Type
                  <select
                    value={serviceForm.kind}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        kind: e.target.value,
                        capacity: e.target.value === 'PRIVATE' ? 1 : 10,
                      })
                    }
                  >
                    <option value="GROUP">Group</option>
                    <option value="PRIVATE">Personal training</option>
                  </select>
                </label>
                <label>
                  Duration (minutes)
                  <input
                    type="number"
                    required
                    min={5}
                    max={480}
                    value={serviceForm.durationMinutes}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        durationMinutes: Number(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  Maximum capacity
                  <input
                    type="number"
                    required
                    min={1}
                    max={serviceForm.kind === 'PRIVATE' ? 1 : 100}
                    value={serviceForm.capacity}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        capacity: Number(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  Difficulty
                  <select
                    value={serviceForm.difficulty}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        difficulty: e.target.value,
                      })
                    }
                  >
                    {['ALL_LEVELS', 'BEGINNER', 'INTERMEDIATE', 'ADVANCED'].map(
                      (level) => (
                        <option key={level}>{level}</option>
                      ),
                    )}
                  </select>
                </label>
                <label className="checkbox">
                  <input
                    type="checkbox"
                    checked={serviceForm.active}
                    onChange={(e) =>
                      setServiceForm({
                        ...serviceForm,
                        active: e.target.checked,
                      })
                    }
                  />
                  Active service
                </label>
                <button className="button primary" disabled={busy}>
                  Save service
                </button>
                {serviceId && (
                  <button
                    type="button"
                    onClick={() => {
                      setServiceId('')
                      setServiceForm(emptyService)
                    }}
                  >
                    Stop editing
                  </button>
                )}
              </form>
              <form className="panel" onSubmit={saveSession}>
                <h3>{sessionId ? 'Edit session' : 'Create session'}</h3>
                <label>
                  Service
                  <select
                    aria-label="Service"
                    required
                    value={sessionForm.serviceId}
                    onChange={(e) => {
                      const service = services.find(
                        (s) => s.id === e.target.value,
                      )
                      setSessionForm({
                        ...sessionForm,
                        serviceId: e.target.value,
                        capacity: service?.capacity ?? 1,
                      })
                    }}
                  >
                    <option value="">Select a service</option>
                    {services
                      .filter((s) => s.active)
                      .map((s) => (
                        <option key={s.id} value={s.id}>
                          {s.name}
                        </option>
                      ))}
                  </select>
                </label>
                <label>
                  Trainer
                  <select
                    aria-label="Trainer"
                    required
                    value={sessionForm.trainerId}
                    onChange={(e) =>
                      setSessionForm({
                        ...sessionForm,
                        trainerId: e.target.value,
                      })
                    }
                  >
                    <option value="">Select a trainer</option>
                    {trainers.map((t) => (
                      <option key={t.id} value={t.id}>
                        {t.displayName}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Location
                  <input
                    list="studio-room-names"
                    required
                    maxLength={120}
                    value={sessionForm.location}
                    onChange={(e) =>
                      setSessionForm({
                        ...sessionForm,
                        location: e.target.value,
                      })
                    }
                  />
                </label>
                <RoomSuggestions version={reload} />
                <label>
                  Start (Bangkok time)
                  <input
                    type="datetime-local"
                    required
                    value={sessionForm.startsAt}
                    onChange={(e) =>
                      setSessionForm({
                        ...sessionForm,
                        startsAt: e.target.value,
                      })
                    }
                  />
                </label>
                <label>
                  Session capacity
                  <input
                    type="number"
                    required
                    min={1}
                    max={
                      services.find((s) => s.id === sessionForm.serviceId)
                        ?.capacity ?? 100
                    }
                    value={sessionForm.capacity}
                    onChange={(e) =>
                      setSessionForm({
                        ...sessionForm,
                        capacity: Number(e.target.value),
                      })
                    }
                  />
                </label>
                <label>
                  Publication
                  <select
                    value={sessionForm.state}
                    onChange={(e) =>
                      setSessionForm({ ...sessionForm, state: e.target.value })
                    }
                  >
                    <option value="PUBLISHED">Published</option>
                    <option value="DRAFT">Draft</option>
                  </select>
                </label>
                <p className="muted">
                  The current cancellation policy is saved with each new
                  session. Classes with booking history cannot be edited.
                </p>
                <button className="button primary" disabled={busy}>
                  Save session
                </button>
                {sessionId && (
                  <button
                    type="button"
                    onClick={() => {
                      setSessionId('')
                      setSessionForm(emptySession)
                    }}
                  >
                    Stop editing
                  </button>
                )}
              </form>
            </div>
            <h3 className="spaced">Services</h3>
            <div className="stack">
              {services.map((s) => (
                <div className="compact-row panel" key={s.id}>
                  <span>
                    {s.name} · {s.durationMinutes} min ·{' '}
                    {s.active ? 'Active' : 'Inactive'}
                  </span>
                  <button
                    onClick={() => {
                      const { id, ...values } = s
                      setServiceId(id)
                      setServiceForm(values)
                    }}
                  >
                    Edit service {s.name}
                  </button>
                </div>
              ))}
            </div>
            <h3 className="spaced">Sessions in this period</h3>
            <div className="stack">
              {sessions.map((s) => (
                <article className="panel compact-row" key={s.id}>
                  <div>
                    <strong>{s.name}</strong>
                    <p>
                      {dateTime(s.startsAt)} · {s.state} · {s.reservedCount}/
                      {s.capacity}
                    </p>
                  </div>
                  <div className="actions">
                    <button
                      disabled={
                        busy ||
                        s.state === 'CANCELLED' ||
                        s.state === 'COMPLETED'
                      }
                      onClick={() => {
                        const value = new Intl.DateTimeFormat('sv-SE', {
                          timeZone: zone,
                          dateStyle: 'short',
                          timeStyle: 'short',
                        })
                          .format(new Date(s.startsAt))
                          .replace(' ', 'T')
                        setSessionId(s.id)
                        setSessionForm({
                          serviceId: s.serviceId,
                          trainerId: s.trainerId,
                          location: s.location,
                          startsAt: value,
                          capacity: s.capacity,
                          state: s.state,
                        })
                      }}
                    >
                      Edit session
                    </button>
                    <button
                      disabled={
                        busy ||
                        s.state === 'CANCELLED' ||
                        s.state === 'COMPLETED'
                      }
                      onClick={() => setCancelTarget(s.id)}
                    >
                      Cancel session
                    </button>
                  </div>
                </article>
              ))}
            </div>
            {cancelTarget && (
              <form
                className="panel confirmation"
                onSubmit={(e) => {
                  e.preventDefault()
                  const reason = String(
                    new FormData(e.currentTarget).get('reason'),
                  )
                  void act(async () => {
                    await api(
                      `/admin/sessions/${cancelTarget}/cancel`,
                      'POST',
                      { reason },
                    )
                    setCancelTarget('')
                  }, 'Session cancelled and booking credits restored.')
                }}
              >
                <h3>Cancel this session?</h3>
                <p>
                  All confirmed bookings will be cancelled and their credits
                  restored.
                </p>
                <label>
                  Reason
                  <input name="reason" required maxLength={250} />
                </label>
                <div className="actions">
                  <button className="button danger" disabled={busy}>
                    Confirm cancellation
                  </button>
                  <button type="button" onClick={() => setCancelTarget('')}>
                    Keep session
                  </button>
                </div>
              </form>
            )}
          </section>
        )}
      </main>
      <footer>
        <span className="brand-text">oasis</span>
        <p>Movement, mindfulness, and a little room to grow.</p>
        <span>Bangkok · Your wellness, at your pace.</span>
      </footer>
    </>
  )
}
export default App
