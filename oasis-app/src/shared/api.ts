export class ApiError extends Error {
  status: number
  code: string
  constructor(status: number, code: string) {
    super(code)
    this.status = status
    this.code = code
  }
}

export async function api<T>(
  path: string,
  method = 'GET',
  body?: unknown,
  key?: string,
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (method !== 'GET') {
    const csrf = await fetch('/api/v1/auth/csrf', {
      credentials: 'same-origin',
    })
    if (!csrf.ok) throw new ApiError(csrf.status, 'SESSION_UNAVAILABLE')
    const token = (await csrf.json()) as { headerName: string; token: string }
    headers[token.headerName] = token.token
    if (body !== undefined) headers['Content-Type'] = 'application/json'
    if (key) headers['Idempotency-Key'] = key
  }
  const response = await fetch(`/api/v1${path}`, {
    method,
    headers,
    credentials: 'same-origin',
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as {
      code?: string
    }
    throw new ApiError(response.status, problem.code ?? 'REQUEST_FAILED')
  }
  return response.status === 204
    ? (undefined as T)
    : (response.json() as Promise<T>)
}

const messages: Record<string, string> = {
  LAST_ADMIN: 'At least one enabled administrator must remain.',
  TRAINER_HAS_SESSIONS:
    'Reassign or cancel this trainer’s future classes first.',
  CUSTOMER_HAS_BOOKINGS: 'Resolve this customer’s future bookings first.',
  CUSTOMER_HAS_MEMBERSHIPS:
    'Resolve active memberships before removing the customer role.',
  ROOM_HAS_SESSIONS:
    'Resolve future classes before reducing capacity or disabling this room.',
  ROOM_NAME_HAS_HISTORY: 'A room with class history keeps its original name.',
  ROOM_UNAVAILABLE: 'Choose an active room with enough capacity.',
  INVALID_REPORT_RANGE: 'Choose a date range of 1–366 days.',
  JOB_NOT_RETRYABLE:
    'This notification has already changed state. Refresh the queue.',
  INVALID_CREDENTIALS: 'Email or password is incorrect.',
  INVALID_INPUT: 'Check the form fields and try again.',
  CONSTRAINT_CONFLICT:
    'This conflicts with an existing record. Check the email, time or room.',
  SESSION_FULL: 'This class just filled up. Choose another session.',
  ALREADY_BOOKED: 'You already have a booking for this class.',
  CANCELLATION_WINDOW_CLOSED: 'The cancellation deadline has passed.',
  ENTITLEMENT_EXPIRED_OR_INACTIVE:
    'This package does not cover the class date.',
  SERVICE_NOT_ELIGIBLE: 'This package does not include this service.',
  INSUFFICIENT_CREDITS: 'There are no credits left in this package.',
  FORBIDDEN:
    'You do not have access to this action. Refresh your session if your role changed.',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
  AUTH_RATE_LIMIT:
    'Too many attempts. Please wait a minute before trying again.',
  SESSION_HAS_HISTORY:
    'A class with booking history cannot be edited. Cancel it and create a new session.',
  SESSION_NOT_BOOKABLE: 'This session is no longer available.',
  SERVICE_TYPE_IN_USE:
    'The type of a service with session history cannot be changed.',
  PAYMENTS_UNCONFIGURED:
    'Payments are not configured. Your order is still pending and no money was collected.',
  ORDER_NOT_PAYABLE:
    'This order is no longer awaiting payment. Refresh its status.',
  ORDER_NOT_CANCELLABLE:
    'This order cannot be cancelled. Refresh its status or contact the studio.',
  MEMBERSHIP_ALREADY_USED:
    'Only a completely unused membership can receive a full refund in this phase.',
  REFUND_ALREADY_REQUESTED:
    'A full refund has already been requested for this payment.',
  REFUND_IN_PROGRESS:
    'This membership is reserved for a pending refund and cannot be reactivated.',
  REFUND_NOT_PENDING:
    'This refund has already reached a result. Refresh the payment history.',
  REFUND_DISPATCH_UNAVAILABLE:
    'Refund dispatch is not configured. Access remains suspended for review.',
  TRAINER_OUTSIDE_AVAILABILITY:
    'This private session must fit within the trainer’s availability.',
  AVAILABILITY_HAS_SESSIONS:
    'This window has an assigned private session. Cancel or move that session first.',
  INVALID_AVAILABILITY:
    'Choose a future availability window with its end after its start, up to 31 days long.',
  ATTENDANCE_NOT_OPEN:
    'Attendance can be recorded only after the session ends and before it is completed.',
  ATTENDANCE_INCOMPLETE:
    'Record attendance for every confirmed booking before completing the session.',
  SESSION_NOT_ENDED: 'This session has not ended or cannot be completed.',
  REVIEW_NOT_ELIGIBLE:
    'Reviews are available only for attended bookings after session completion.',
  GRANT_REVOKED: 'A revoked membership cannot be reactivated.',
  INVALID_ACTIVATION_DATE:
    'Choose an activation time from now up to one year ahead.',
  INVALID_ENTITLEMENT:
    'Credit plans require 1–10,000 included sessions. Unlimited plans do not have credits.',
  INVALID_VALIDITY:
    'Prepaid packages require 1–3,660 days. Monthly/yearly plans use calendar validity.',
}
export function errorMessage(error: unknown): string {
  return error instanceof ApiError
    ? (messages[error.code] ??
        `Unable to complete this action (${error.code}).`)
    : 'Unable to reach Oasis. Check your connection and try again.'
}

export interface Me {
  id: string
  email: string
  displayName: string
  roles: string[]
}
export interface Service {
  id: string
  name: string
  description: string
  category: string
  kind: string
  durationMinutes: number
  difficulty: string
  capacity: number
  active: boolean
}
export interface Session {
  id: string
  serviceId: string
  name: string
  description: string
  category: string
  kind: string
  difficulty: string
  trainerId: string
  trainerName: string
  location: string
  startsAt: string
  endsAt: string
  capacity: number
  reservedCount: number
  available: number
  state: string
  cancellationMinutes: number
}
export interface Booking {
  id: string
  sessionId: string
  grantId: string
  name: string
  trainerName: string
  location: string
  startsAt: string
  endsAt: string
  status: string
  bookedAt: string
  cancelledAt: string | null
  cancellationMinutes: number
  sessionState: string
}
export interface Grant {
  id: string
  name: string
  source: string
  validFrom: string
  validUntil: string
  status: string
  remainingCredits: number | null
  entitlementMode: 'CREDITS' | 'UNLIMITED'
  usedSessions: number
}
export interface Entry {
  id: string
  grantId: string
  kind: string
  delta: number
  reason: string
  createdAt: string
}
export interface Trainer {
  id: string
  displayName: string
}
export interface Attendee {
  bookingId: string
  displayName: string
  status: string
}
export type RunAction = (
  action: () => Promise<void>,
  success?: string,
  revalidate?: boolean,
) => Promise<void>
export interface Plan {
  id: string
  name: string
  period: 'MONTH' | 'YEAR' | 'PACKAGE'
  entitlementMode: 'CREDITS' | 'UNLIMITED'
  credits: number | null
  validityDays: number | null
  priceMinor: number
  currency: string
  active: boolean
  serviceIds: string[]
}
export interface Availability {
  id: string
  startsAt: string
  endsAt: string
}
export interface Outcome {
  bookingId: string
  displayName: string
  status: string
  outcome: string
  recordedAt: string
}
export interface Review {
  id: string
  bookingId: string
  rating: number
  body: string
  createdAt: string
}
