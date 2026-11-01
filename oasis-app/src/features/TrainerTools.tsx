import { useEffect, useState } from 'react'
import { api, errorMessage } from '../shared/api'
import type {
  Attendee,
  Availability,
  Outcome,
  RunAction,
  Session,
} from '../shared/api'
const show = (instant: string) =>
  new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Bangkok',
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(instant))

export function TrainerAvailability({
  from,
  to,
  run,
  busy,
  version,
}: {
  from: string
  to: string
  run: RunAction
  busy: boolean
  version: number
}) {
  const [slots, setSlots] = useState<Availability[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    let current = true
    api<Availability[]>(`/trainer/availability?from=${from}&to=${to}`)
      .then((rows) => {
        if (current) {
          setSlots(rows)
          setError('')
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [from, to, version])
  return (
    <section className="panel training-panel">
      <h3>My personal-training availability</h3>
      <p className="muted">
        Private sessions must fit inside an availability window. All times are
        Bangkok time. Windows with assigned private sessions cannot be removed.
      </p>
      {error && <p role="alert">{error}</p>}
      <form
        onSubmit={(e) => {
          e.preventDefault()
          const form = e.currentTarget
          const fields = new FormData(form)
          void run(async () => {
            await api('/trainer/availability', 'POST', {
              startsAt: new Date(
                `${fields.get('start')}:00+07:00`,
              ).toISOString(),
              endsAt: new Date(`${fields.get('end')}:00+07:00`).toISOString(),
            })
            form.reset()
          }, 'Availability added.')
        }}
      >
        <label>
          Available from
          <input name="start" type="datetime-local" required />
        </label>
        <label>
          Available until
          <input name="end" type="datetime-local" required />
        </label>
        <button disabled={busy}>Add availability</button>
      </form>
      <div className="stack">
        {slots.map((slot) => (
          <div className="compact-row" key={slot.id}>
            <span>
              {show(slot.startsAt)} – {show(slot.endsAt)}
            </span>
            <button
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api(`/trainer/availability/${slot.id}`, 'DELETE')
                }, 'Availability removed.')
              }
            >
              Remove availability
            </button>
          </div>
        ))}
      </div>
    </section>
  )
}

function AttendanceForm({
  attendee,
  outcome,
  session,
  run,
  busy,
  reload,
}: {
  attendee: Attendee
  outcome?: Outcome
  session: Session
  run: RunAction
  busy: boolean
  reload: () => void
}) {
  const [status, setStatus] = useState(outcome?.status ?? 'ATTENDED')
  return (
    <form
      className="attendance-form"
      onSubmit={(e) => {
        e.preventDefault()
        const fields = new FormData(e.currentTarget)
        void run(
          async () => {
            await api(
              `/trainer/bookings/${attendee.bookingId}/attendance`,
              'PUT',
              { status, outcome: String(fields.get('outcome') ?? '') },
            )
            reload()
          },
          'Attendance and training outcome saved.',
          false,
        )
      }}
    >
      <h4>{attendee.displayName}</h4>
      <label>
        Attendance for {attendee.displayName}
        <select
          aria-label={`Attendance for ${attendee.displayName}`}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="ATTENDED">Attended</option>
          <option value="NO_SHOW">No-show</option>
        </select>
      </label>
      <label>
        Training outcome for {attendee.displayName}
        <textarea
          name="outcome"
          maxLength={2000}
          defaultValue={outcome?.outcome ?? ''}
        />
      </label>
      <button disabled={busy || session.state !== 'PUBLISHED'}>
        Save attendance for {attendee.displayName}
      </button>
    </form>
  )
}

export function SessionTraining({
  session,
  attendees,
  run,
  busy,
  now,
}: {
  session: Session
  attendees?: Attendee[]
  run: RunAction
  busy: boolean
  now: number
}) {
  const [outcomes, setOutcomes] = useState<Outcome[]>([])
  const [version, setVersion] = useState(0)
  const [error, setError] = useState('')
  useEffect(() => {
    let current = true
    api<Outcome[]>(`/trainer/sessions/${session.id}/outcomes`)
      .then((rows) => {
        if (current) {
          setOutcomes(rows)
          setError('')
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [session.id, version])
  if (now < Date.parse(session.endsAt))
    return <p className="muted">Attendance opens after this session ends.</p>
  return (
    <section className="training-outcomes">
      <h4>Attendance & training outcomes</h4>
      {error && <p role="alert">{error}</p>}
      {session.state === 'PUBLISHED' && (
        <>
          <p className="muted">
            View attendees, record everyone, then complete the session.
            Completion locks attendance and allows attended customers to review.
          </p>
          {attendees?.map((a) => {
            const outcome = outcomes.find((o) => o.bookingId === a.bookingId)
            return (
              <AttendanceForm
                key={`${a.bookingId}:${outcome?.recordedAt ?? 'new'}`}
                attendee={a}
                outcome={outcome}
                session={session}
                run={run}
                busy={busy}
                reload={() => setVersion((n) => n + 1)}
              />
            )
          })}
          <button
            className="button primary"
            disabled={busy}
            onClick={() =>
              void run(async () => {
                await api(`/trainer/sessions/${session.id}/complete`, 'POST')
              }, 'Session completed. Eligible customers can now review.')
            }
          >
            Complete session
          </button>
        </>
      )}
      {session.state === 'COMPLETED' && (
        <p className="muted">Session completed. Attendance is locked.</p>
      )}
      {outcomes.map((o) => (
        <p className="outcome" key={o.bookingId}>
          {o.displayName}{' '}
          · {o.status.toLowerCase()}: {o.outcome || 'No outcome notes.'}
        </p>
      ))}
    </section>
  )
}
