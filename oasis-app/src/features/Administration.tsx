import { useEffect, useState } from 'react'
import { api, errorMessage } from '../shared/api'
import type { Me, RunAction, Trainer } from '../shared/api'
import { NotificationOperations } from './Notifications'
interface User {
  id: string
  email: string
  displayName: string
  enabled: boolean
  roles: string[]
}
interface Room {
  id: string
  name: string
  location: string
  capacity: number
  active: boolean
}
const emptyRoom = { name: '', location: '', capacity: 10, active: true }
export function Administration({
  me,
  run,
  busy,
  version,
}: {
  me: Me
  run: RunAction
  busy: boolean
  version: number
}) {
  const [users, setUsers] = useState<User[]>([]),
    [rooms, setRooms] = useState<Room[]>([]),
    [trainers, setTrainers] = useState<Trainer[]>([]),
    [error, setError] = useState(''),
    [policy, setPolicy] = useState(120),
    [room, setRoom] = useState(emptyRoom),
    [roomId, setRoomId] = useState(''),
    [offset, setOffset] = useState(0)
  const [operations, setOperations] = useState<Record<string, number>>({}),
    [audit, setAudit] = useState<
      { id: string; actor: string; action: string; reason: string }[]
    >([])
  useEffect(() => {
    let current = true
    Promise.all([
      api<User[]>(`/admin/users?offset=${offset}&limit=25`),
      api<Room[]>('/admin/rooms'),
      api<Trainer[]>('/admin/trainers'),
      api<{ cancellationMinutes: number }>('/admin/business-policy'),
      api<Record<string, number>>('/admin/operations'),
      api<{ id: string; actor: string; action: string; reason: string }[]>(
        '/admin/audit-events?limit=25',
      ),
    ])
      .then(([u, r, t, p, o, a]) => {
        if (current) {
          setUsers(u)
          setRooms(r)
          setTrainers(t)
          setPolicy(p.cancellationMinutes)
          setOperations(o)
          setAudit(a)
          setError('')
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e))
      })
    return () => {
      current = false
    }
  }, [version, offset])
  return (
    <section>
      <h2>Studio administration</h2>
      {error && (
        <p role="alert" className="alert error">
          {error}
        </p>
      )}
      <div className="class-grid">
        {Object.entries(operations).map(([label, value]) => (
          <div className="panel settings-form" key={label}>
            <span className="muted">{label.replace(/([A-Z])/g, ' $1')}</span>
            <strong className="metric">{value}</strong>
          </div>
        ))}
      </div>
      <div className="admin-grid spaced">
        <form
          className="panel"
          onSubmit={(e) => {
            e.preventDefault()
            const reason = String(new FormData(e.currentTarget).get('reason'))
            void run(async () => {
              await api('/admin/business-policy', 'PUT', {
                cancellationMinutes: policy,
                reason,
              })
            }, 'Cancellation policy saved for new sessions. Existing sessions keep their original policy.')
          }}
        >
          <h3>Cancellation policy</h3>
          <label>
            Cancellation notice (minutes)
            <input
              type="number"
              min={0}
              max={10080}
              required
              value={policy}
              onChange={(e) => setPolicy(Number(e.target.value))}
            />
          </label>
          <label>
            Policy change reason
            <input name="reason" maxLength={250} required />
          </label>
          <button disabled={busy}>Save cancellation policy</button>
        </form>
        <form
          className="panel"
          onSubmit={(e) => {
            e.preventDefault()
            const fields = new FormData(e.currentTarget)
            void run(async () => {
              await api(
                `/admin/trainers/${fields.get('trainer')}/commission`,
                'PUT',
                {
                  commissionMinor: Number(fields.get('fee')) * 100,
                  reason: String(fields.get('reason')),
                },
              )
            }, 'Fixed trainer fee saved for new session assignments.')
          }}
        >
          <h3>Trainer teaching fee</h3>
          <p className="muted">
            Fixed THB per completed class, snapshotted when assigned. Existing
            classes keep their fee. Percentage commissions need a separate
            revenue-allocation policy.
          </p>
          <label>
            Fee trainer
            <select aria-label="Fee trainer" name="trainer" required>
              <option value="">Choose trainer</option>
              {trainers.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.displayName}
                </option>
              ))}
            </select>
          </label>
          <label>
            Teaching fee (whole THB)
            <input
              name="fee"
              type="number"
              min={0}
              max={10000000}
              step={1}
              required
            />
          </label>
          <label>
            Fee change reason
            <input name="reason" required maxLength={250} />
          </label>
          <button disabled={busy}>Save trainer fee</button>
        </form>
      </div>
      <h3 className="spaced">Rooms</h3>
      <form
        className="panel settings-form"
        onSubmit={(e) => {
          e.preventDefault()
          void run(async () => {
            await api(
              roomId ? `/admin/rooms/${roomId}` : '/admin/rooms',
              roomId ? 'PUT' : 'POST',
              room,
            )
            setRoom(emptyRoom)
            setRoomId('')
          }, 'Room saved.')
        }}
      >
        <h4>{roomId ? 'Edit room' : 'Create room'}</h4>
        <label>
          Room name
          <input
            required
            maxLength={120}
            value={room.name}
            onChange={(e) => setRoom({ ...room, name: e.target.value })}
          />
        </label>
        <label>
          Room location
          <input
            required
            maxLength={120}
            value={room.location}
            onChange={(e) => setRoom({ ...room, location: e.target.value })}
          />
        </label>
        <label>
          Room capacity
          <input
            type="number"
            min={1}
            max={100}
            required
            value={room.capacity}
            onChange={(e) =>
              setRoom({ ...room, capacity: Number(e.target.value) })
            }
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={room.active}
            onChange={(e) => setRoom({ ...room, active: e.target.checked })}
          />
          Room active
        </label>
        <button disabled={busy}>Save room</button>
        {roomId && (
          <button
            type="button"
            onClick={() => {
              setRoomId('')
              setRoom(emptyRoom)
            }}
          >
            Stop editing room
          </button>
        )}
      </form>
      <div className="stack">
        {rooms.map((r) => (
          <div className="panel compact-row" key={r.id}>
            <span>
              {r.name} · {r.location} · capacity {r.capacity} ·{' '}
              {r.active ? 'Active' : 'Inactive'}
            </span>
            <button
              onClick={() => {
                const { id, ...input } = r
                setRoomId(id)
                setRoom(input)
              }}
            >
              Edit room {r.name}
            </button>
          </div>
        ))}
      </div>
      <h3 className="spaced">User access</h3>
      <p className="muted">
        Access changes take effect on the next request. The last enabled admin
        cannot be removed. Resolve future classes/bookings before disabling a
        trainer/customer.
      </p>
      <div className="stack">
        {users.map((u) => (
          <UserAccess
            key={`${u.id}:${u.enabled}:${u.roles.join(',')}`}
            user={u}
            self={u.id === me.id}
            run={run}
            busy={busy}
          />
        ))}
      </div>
      <div className="actions spaced">
        <button
          disabled={offset === 0}
          onClick={() => setOffset((n) => Math.max(0, n - 25))}
        >
          Previous users
        </button>
        <button
          disabled={users.length < 25}
          onClick={() => setOffset((n) => n + 25)}
        >
          Next users
        </button>
      </div>
      <NotificationOperations run={run} busy={busy} version={version} />
      <h3 className="spaced">Recent audit events</h3>
      <div className="table-wrap panel">
        <table>
          <thead>
            <tr>
              <th>Actor</th>
              <th>Action</th>
              <th>Reason</th>
            </tr>
          </thead>
          <tbody>
            {audit.map((a) => (
              <tr key={a.id}>
                <td>{a.actor}</td>
                <td>{a.action}</td>
                <td>{a.reason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}
function UserAccess({
  user,
  self,
  run,
  busy,
}: {
  user: User
  self: boolean
  run: RunAction
  busy: boolean
}) {
  const [roles, setRoles] = useState(user.roles),
    [enabled, setEnabled] = useState(user.enabled)
  return (
    <form
      className="panel settings-form"
      onSubmit={(e) => {
        e.preventDefault()
        const reason = String(new FormData(e.currentTarget).get('reason'))
        void run(async () => {
          await api(`/admin/users/${user.id}/roles`, 'PUT', {
            enabled,
            roles,
            reason,
          })
          if (self) window.location.reload()
        }, 'User access updated.')
      }}
    >
      <h4>
        {user.displayName} · {user.email}
      </h4>
      <fieldset>
        <legend>Roles for {user.displayName}</legend>
        {['CUSTOMER', 'TRAINER', 'ADMIN'].map((role) => (
          <label className="checkbox" key={role}>
            <input
              type="checkbox"
              checked={roles.includes(role)}
              onChange={(e) =>
                setRoles(
                  e.target.checked
                    ? [...roles, role]
                    : roles.filter((r) => r !== role),
                )
              }
            />
            {role}
          </label>
        ))}
      </fieldset>
      <label className="checkbox">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(e) => setEnabled(e.target.checked)}
        />
        Account enabled
      </label>
      <label>
        Access change reason
        <input name="reason" required maxLength={250} />
      </label>
      <button disabled={busy || roles.length === 0}>
        Save access for {user.displayName}
      </button>
    </form>
  )
}
export function RoomSuggestions({ version }: { version: number }) {
  const [rooms, setRooms] = useState<Room[]>([])
  useEffect(() => {
    let current = true
    api<Room[]>('/admin/rooms')
      .then((rows) => {
        if (current) setRooms(rows)
      })
      .catch(() => {})
    return () => {
      current = false
    }
  }, [version])
  return (
    <datalist id="studio-room-names">
      {rooms
        .filter((r) => r.active)
        .map((r) => (
          <option key={r.id} value={r.name}>
            {r.location} · {r.capacity} spots
          </option>
        ))}
    </datalist>
  )
}
