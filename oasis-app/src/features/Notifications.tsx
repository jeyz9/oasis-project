import { useEffect, useState } from 'react'
import { api, errorMessage } from '../shared/api'
import type { RunAction } from '../shared/api'
interface Notice {
  id: string
  title: string
  body: string
  createdAt: string
  readAt: string | null
}
interface Preferences {
  emailEnabled: boolean
  lineEnabled: boolean
  lineLinked: boolean
}
interface Job {
  id: string
  kind: string
  channel: string
  state: string
  attempts: number
  last_code: string | null
  simulated: boolean
  recipient: string
}
const date = (value: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
export function Inbox({
  run,
  busy,
  version,
}: {
  run: RunAction
  busy: boolean
  version: number
}) {
  const [rows, setRows] = useState<Notice[]>([]),
    [prefs, setPrefs] = useState<Preferences>({
      emailEnabled: false,
      lineEnabled: false,
      lineLinked: false,
    }),
    [error, setError] = useState(''),
    [poll, setPoll] = useState(0),
    [offset, setOffset] = useState(0)
  useEffect(() => {
    let current = true
    api<Notice[]>(`/me/notifications?offset=${offset}&limit=25`)
      .then((n) => {
        if (current) {
          setRows(n)
          setError('')
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [version, poll, offset])
  useEffect(() => {
    let current = true
    api<Preferences>('/me/notification-preferences')
      .then((p) => {
        if (current) setPrefs(p)
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [version])
  useEffect(() => {
    const timer = window.setInterval(() => setPoll((n) => n + 1), 10000)
    return () => window.clearInterval(timer)
  }, [])
  return (
    <section>
      <div className="section-heading">
        <h2>Your inbox</h2>
        <button onClick={() => setPoll((n) => n + 1)}>Refresh inbox</button>
      </div>
      {error && (
        <p role="alert" className="alert error">
          {error}
        </p>
      )}
      <form
        className="panel settings-form"
        onSubmit={(e) => {
          e.preventDefault()
          void run(async () => {
            await api('/me/notification-preferences', 'PUT', {
              emailEnabled: prefs.emailEnabled,
              lineEnabled: prefs.lineEnabled,
            })
          }, 'Notification preferences saved.')
        }}
      >
        <h3>Class notification preferences</h3>
        <p className="muted">
          In-app confirmations and reminders are available here. Email/LINE
          require a configured provider and verified destination; opting in does
          not configure or send a real message. Local demo deliveries may be
          simulated.
        </p>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={prefs.emailEnabled}
            onChange={(e) =>
              setPrefs({ ...prefs, emailEnabled: e.target.checked })
            }
          />
          Email class reminders
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={prefs.lineEnabled}
            onChange={(e) =>
              setPrefs({ ...prefs, lineEnabled: e.target.checked })
            }
          />
          LINE class reminders
        </label>
        <p className="muted">
          LINE:{' '}
          {prefs.lineLinked
            ? 'Linked'
            : 'Not linked — delivery remains blocked until a trusted link is configured.'}
        </p>
        <button disabled={busy}>Save notification preferences</button>
      </form>
      {!rows.length && (
        <p className="empty panel">No notifications in this view yet.</p>
      )}
      <div className="stack">
        {rows.map((n) => (
          <article className="panel settings-form" key={n.id}>
            <span className="tag">{n.readAt ? 'Read' : 'Unread'}</span>
            <h3>{n.title}</h3>
            <p>{n.body}</p>
            <p className="muted">{date(n.createdAt)} Bangkok</p>
            {!n.readAt && (
              <button
                disabled={busy}
                onClick={() =>
                  void run(async () => {
                    await api(`/me/notifications/${n.id}/read`, 'POST')
                  })
                }
              >
                Mark as read
              </button>
            )}
          </article>
        ))}
      </div>
      <div className="actions spaced">
        <button
          disabled={offset === 0}
          onClick={() => setOffset((n) => Math.max(0, n - 25))}
        >
          Newer notifications
        </button>
        <button
          disabled={rows.length < 25}
          onClick={() => setOffset((n) => n + 25)}
        >
          Older notifications
        </button>
      </div>
    </section>
  )
}
export function NotificationOperations({
  run,
  busy,
  version,
}: {
  run: RunAction
  busy: boolean
  version: number
}) {
  const [jobs, setJobs] = useState<Job[]>([]),
    [error, setError] = useState('')
  useEffect(() => {
    let current = true
    api<Job[]>('/admin/notification-jobs')
      .then((j) => {
        if (current) {
          setJobs(j)
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
    <section className="spaced">
      <div className="section-heading">
        <h3>Notification delivery queue</h3>
        <button
          disabled={busy}
          onClick={() =>
            void run(async () => {
              await api('/admin/notification-jobs/run', 'POST')
            }, 'Notification worker cycle completed.')
          }
        >
          Run notification worker
        </button>
      </div>
      <p className="muted">
        Latest 200 jobs. Retryable failures back off automatically, then move to
        a dead letter after five attempts. Blocked providers require
        configuration before retrying. INAPP is local inbox delivery; external
        simulation is labelled separately.
      </p>
      {error && <p role="alert">{error}</p>}
      <div className="stack">
        {jobs.map((j) => (
          <article className="panel settings-form" key={j.id}>
            <h4>
              {j.recipient} · {j.kind}
            </h4>
            <p>
              {j.channel} · {j.state} · {j.attempts} attempts{' '}
              {j.simulated ? '· SIMULATED EXTERNAL DELIVERY' : ''}
            </p>
            <p className="muted">{j.last_code}</p>
            {['BLOCKED', 'DEAD', 'RETRY'].includes(j.state) && (
              <form
                onSubmit={(e) => {
                  e.preventDefault()
                  const reason = String(
                    new FormData(e.currentTarget).get('reason'),
                  )
                  void run(async () => {
                    await api(
                      `/admin/notification-jobs/${j.id}/retry`,
                      'POST',
                      { reason },
                    )
                  }, 'Notification retry scheduled.')
                }}
              >
                <label>
                  Retry reason
                  <input name="reason" required maxLength={250} />
                </label>
                <button disabled={busy}>Retry notification</button>
              </form>
            )}
          </article>
        ))}
      </div>
    </section>
  )
}
