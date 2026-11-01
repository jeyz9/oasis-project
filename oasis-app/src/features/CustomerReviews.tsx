import { useEffect, useState } from 'react'
import { api, errorMessage } from '../shared/api'
import type { Booking, Review, RunAction } from '../shared/api'

export function CustomerReviews({
  bookings,
  run,
  busy,
  version,
}: {
  bookings: Booking[]
  run: RunAction
  busy: boolean
  version: number
}) {
  const [reviews, setReviews] = useState<Review[]>([])
  const [error, setError] = useState('')
  useEffect(() => {
    let current = true
    api<Review[]>('/me/reviews')
      .then((rows) => {
        if (current) {
          setReviews(rows)
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
  const eligible = bookings.filter(
    (b) =>
      b.status === 'ATTENDED' &&
      b.sessionState === 'COMPLETED' &&
      !reviews.some((r) => r.bookingId === b.id),
  )
  return (
    <section className="spaced">
      <h2>Your class reviews</h2>
      <p className="muted">
        Share one review after attending a completed class. Reviews for future,
        cancelled or no-show bookings are unavailable.
      </p>
      {error && <p role="alert">{error}</p>}
      {eligible.map((b) => (
        <form
          key={b.id}
          className="panel review-form"
          onSubmit={(e) => {
            e.preventDefault()
            const fields = new FormData(e.currentTarget)
            void run(async () => {
              await api(`/me/bookings/${b.id}/review`, 'POST', {
                rating: Number(fields.get('rating')),
                body: String(fields.get('body')),
              })
            }, 'Thank you. Your review has been saved.')
          }}
        >
          <h3>Review {b.name}</h3>
          <p>With {b.trainerName}</p>
          <label>
            Rating
            <select aria-label="Rating" name="rating" defaultValue="5">
              {[5, 4, 3, 2, 1].map((n) => (
                <option key={n} value={n}>
                  {n} of 5
                </option>
              ))}
            </select>
          </label>
          <label>
            Your review
            <textarea name="body" required maxLength={2000} />
          </label>
          <button className="button primary" disabled={busy}>
            Submit review
          </button>
        </form>
      ))}
      {!eligible.length && !reviews.length && (
        <p className="empty panel">No classes eligible for review yet.</p>
      )}
      <div className="stack">
        {reviews.map((r) => (
          <article key={r.id} className="panel review-form">
            <h3>
              {bookings.find((b) => b.id === r.bookingId)?.name ??
                'Completed class'}{' '}
              · {r.rating}/5
            </h3>
            <p>{r.body}</p>
          </article>
        ))}
      </div>
    </section>
  )
}
