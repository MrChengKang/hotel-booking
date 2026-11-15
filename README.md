# STAY Spring Boot backend

For Render Free + Vercel Hobby, see [the hosting guide](docs/DEPLOYMENT_RENDER_VERCEL.md). The root `render.yaml` defines a separate Free Docker service; secrets are supplied through Render.


Java 21+, Spring Boot 4.1.2, Spring Security, Spring Data JPA and Aiven MySQL. Frontend: [MrChengKang/hotel-booking-system](https://github.com/MrChengKang/hotel-booking-system).

## Run locally

Open a terminal in this backend directory and configure `JAVA_HOME` for Java 21 or later. Copy `local.env.example` to `.env.local` only if the private file does not already exist, then fill in your MySQL connection settings. Start with `./mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on Linux/macOS. The working directory must be the backend folder so local environment-file imports resolve correctly. See [Cloudinary](docs/CLOUDINARY.md) and [Brevo](docs/BREVO.md) for optional local media and email setup. Use [production settings](docs/DEPLOYMENT.md) on a public host.

The API listens on port 8080. React uses port 3000 and `http://localhost:8080/api` by default. Use the same hostname for both services (localhost with localhost, or 127.0.0.1 with 127.0.0.1). Set `APP_CORS_ALLOWED_ORIGINS` to a comma-separated list of explicit allowed frontend origins when necessary.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | /api/auth/csrf | Creates a session CSRF token; returns `headerName` and `token` |
| POST | /api/auth/register | `{name,email,password,role}`; returns user DTO and signs in (201) |
| POST | /api/auth/login | `{email,password}`; returns user DTO and signs in |
| GET | /api/auth/me | Returns signed-in user or 401 |
| POST | /api/auth/logout | Invalidates the server session; returns 204 |

Send cookies with every request (`credentials: include`). Before a POST, fetch `/csrf` and attach the returned token under its returned header name. Fetch a fresh token after login or logout. Session cookies are HttpOnly; no authentication token or password is saved in browser storage. A session ID is rotated on authentication and idle sessions expire after 30 minutes. Sessions are currently held in server memory, so restarting the API logs users out while database accounts remain.

User responses contain only `id`, `name`, `email`, and lowercase `role`. Registration accepts only `user` or `host`. `ADMIN` exists in the role enum but cannot be selected through public registration. The security chain restricts `/api/admin/**` and `/api/host/**` routes by role. Host ownership checks and the platform Admin review workspace are implemented.

Passwords use BCrypt (cost 12), require at least 8 characters on registration, and must not exceed 72 UTF-8 bytes. Emails are normalized to lowercase and have a database uniqueness constraint. Duplicate registration returns 409, invalid input 400, invalid credentials 401, and CSRF/authorization failures 403.

## Database and tests

In development, Hibernate `ddl-auto=update` creates the `stay_users` table if it is absent. Columns: `id`, `name`, `email`, `password_hash`, `role`, `created_at`. Credentials remain in your existing Aiven environment file; its JDBC URL must enable TLS.

Run `./mvnw.cmd test`. Tests explicitly select an in-memory H2 database and never connect to Aiven. They exercise real HTTP requests for registration, session restore, login/logout, password hashing, duplicate emails, CSRF, Admin registration rejection, role checks and CORS.

Before public deployment: use HTTPS and `SESSION_COOKIE_SECURE=true`, restrict CORS to the deployed frontend, configure Aiven CA verification, use schema migrations with `ddl-auto=validate`, and add login throttling, email verification and operational rate limits. For multiple backend instances, use a shared server session store. No production Admin account is automatically created.

STAY uses its own `stay_users` table so existing applications using a generic `users` table do not share accounts. Existing `users` records are not copied, deleted, or treated as STAY accounts.


## Forgot password — Brevo HTTPS or Gmail SMTP

For free hosting that blocks SMTP, set `STAY_MAIL_PROVIDER=brevo` and follow [docs/BREVO.md](docs/BREVO.md). Backend-only `.env.brevo.local` holds local Brevo credentials; `prod` uses runtime secrets. The default remains `smtp` for existing Gmail development settings.


The login page links to `/forgot-password`. Enter the account email, follow the emailed `/reset-password#token=...` link, and choose a new password. `POST /api/auth/forgot-password` accepts `{email}`; `POST /api/auth/reset-password` accepts `{token,password}`. Both retain CSRF protection. Reset responses never include a token or disclose whether an email is registered.

Append the entries in `gmail-smtp.example.properties` to the existing backend `.env.local` and replace the placeholders. Keep your Aiven settings. Enable Google 2-Step Verification and create a Google App Password; use that App Password for `SPRING_MAIL_PASSWORD`, not your regular Gmail password. Google may restrict App Password availability for some managed accounts. Reference: https://support.google.com/accounts/answer/185833

Gmail defaults are `smtp.gmail.com:587` with authenticated, required STARTTLS and certificate hostname verification. `APP_FRONTEND_URL` must be your trusted frontend origin (HTTPS in production); reset links never use the incoming Host header. Restart Spring Boot after changing the configuration. If mail credentials are absent, the endpoint returns 503 with a configuration message. Mail delivery failures produce a generic operator log without addresses, passwords or tokens; check the mail provider and logs if no email arrives. The public result remains generic to protect account privacy.

Tokens are 32 random bytes encoded as URL-safe Base64, stored only as SHA-256 digests in `stay_password_reset_tokens`. They expire in 15 minutes, are single-use, and are replaced by subsequent requests outside a 60-second per-account cooldown. Sending a new link invalidates the prior link. Token issuance and consumption are serialized with user-row database locks. Successful resets update BCrypt hashes, increment `password_version`, and invalidate previous sessions on their next authenticated request. Users sign in again rather than being automatically signed in after a reset.

Old session cookies without the new password version are treated as expired. No real emails are sent by automated tests. Tests cover private responses, token hashing/expiry/reuse, cooldown, CSRF, changed password login and old-session invalidation. Add public endpoint rate limits at your gateway before deployment. Expired tokens remain as at most one row per user until replaced; there is no token/URL logging or developer bypass.


## Hotel, room types and daily inventory

STAY-specific relational entities and repositories are in `hotel/`: `stay_hotels` → `stay_room_types` → `stay_room_inventory`, linked to `stay_users` by hotel ownership. See [the model and inventory rules](docs/HOTEL_INVENTORY.md) and `src/main/resources/db/manual/001_hotel_inventory.sql` for the MySQL schema. Hotel management, public search/availability, and User booking/cancellation APIs are now available; see [the API guide](docs/HOTEL_BOOKING_API.md). The React hotel list, availability quote, reservation confirmation and My Bookings screens now use these live APIs.


## Hotel and booking APIs

See [HOTEL_BOOKING_API.md](docs/HOTEL_BOOKING_API.md) for endpoints, request bodies, role/ownership rules, pricing, cancellation policy, idempotency, locks and deployment scope. New bookings use the Stripe sandbox lifecycle below; historical CONFIRMED/UNPAID bookings remain unchanged. The two order tables are `stay_bookings` and `stay_booking_nights`; manual SQL is in `src/main/resources/db/manual/002_bookings.sql`. No seed records or real test orders are created.


## Host Dashboard

React now provides `/host` with overview, hotels, room types, daily inventory and hotel orders. `GET /api/host/overview` returns `{hotels,publishedHotels,bookings,confirmedBookings}` scoped to the authenticated Host, including orders from all of that Host's hotels. No revenue or upcoming-arrival count is inferred from these totals. Host role and hotel ownership remain enforced on the backend. No new database schema or seed data is required for the dashboard.


## Hotel and room photos

Host Dashboard → Photos selects either the hotel collection or a room-type collection.
Each collection supports up to 20 JPG/PNG photos (5 MiB each, up to 6000 pixels per axis and 25 megapixels).
The server inspects and re-encodes actual image bytes; filenames and declared MIME types are not trusted.
The first photo in the collection is the cover. Reordering submits the complete current ID list;
concurrent/stale changes return 409. Archived hotels are read-only.

Image metadata is stored in `stay_hotel_images` (manual create-only schema: `003_hotel_images.sql`).
Image bytes are stored in `STAY_MEDIA_DIRECTORY`, default `./uploads/hotel-images` relative to the backend working directory.
This directory is ignored by Git. Set an absolute persistent directory in production, grant write access to the application,
and back up this directory together with MySQL. Ephemeral deployment disks will lose photos on redeployment.
Multiple backend replicas need shared storage; this local-disk implementation is intended for a single backend instance.

Photo endpoints:
- `GET/POST /api/host/hotels/{id}/images` (optional `roomTypeId`; POST multipart `file`, optional `altText`)
- `PUT /api/host/hotels/{id}/images/order` with `{ "roomTypeId": null, "imageIds": [2, 1] }`
- `PATCH /api/host/hotels/{id}/images/{imageId}` with `{ "altText": "Sunny lobby" }`
- `DELETE /api/host/hotels/{id}/images/{imageId}`
- `GET /api/hotels/{id}/images` (published hotel; optional active `roomTypeId`)
- `GET /api/media/{imageId}` (public only for a published hotel and active room; otherwise owning Host or authenticated Admin only)

Host mutations require the existing session and CSRF header. Media is served through authorization checks,
with no-store and nosniff headers; the upload directory is never a public static directory.
Metadata deletion commits before file cleanup; uploads are cleaned up if their database transaction rolls back.
A filesystem cleanup failure is logged and can leave an inaccessible orphan file requiring storage maintenance.


## Personal profiles and saved hotels

All authenticated roles can use the account endpoints. Identity always comes from the session; no user ID is accepted from the client.
- `GET /api/account/profile` returns name, sign-in email, role, private phone/city/bio and join date.
- `PUT /api/account/profile` accepts `name` (required, 80 characters), optional `phone` (30), `city` (100) and `bio` (500). Email, role and credentials cannot be modified by this endpoint.
- `GET /api/account/favorites` returns this account's saved hotels, newest first (maximum 200).
- `PUT /api/account/favorites/{hotelId}` saves a published hotel; retries are idempotent.
- `DELETE /api/account/favorites/{hotelId}` removes only the current account's favorite; repeated removal is idempotent.

Mutations require the existing session/CSRF header. Account row locks and a unique user/hotel constraint prevent duplicate favorites under concurrent retries.
Unpublished/archived hotels remain removable in the saved list with `hotel: null`; no private hotel metadata is returned.
Profile extensions and favorites use the create-only manual schema `004_account_favorites.sql`. Existing account credentials are unchanged.
Frontend routes are `/account` and `/wishlist`; anonymous access redirects to sign-in and returns to the requested account page.


## Private account avatars

My Account supports selecting a JPG/PNG profile photo, previewing it, uploading a replacement and confirming removal.
Uploads use the same validated/re-encoded 5 MiB image pipeline as hotel photos (maximum 6000 pixels per axis and 25 megapixels).
The photo appears in the account summary, header and account menu, and is restored by login and `/api/auth/me`.
- `POST /api/account/avatar`: multipart `file`, session and CSRF required; returns `{ "avatarUrl": "/api/account/avatar/<revision>" }`.
- `DELETE /api/account/avatar`: idempotent removal, session and CSRF required.
- `GET /api/account/avatar/{revision}`: private image read; only the current avatar of the authenticated owner can be read. Other accounts and old revisions receive 404.

The create-only `005_account_avatars.sql` stores one metadata row per account. Image bytes share `STAY_MEDIA_DIRECTORY`;
include this persistent directory in backups. New files are cleaned on transaction rollback; replaced/removed files are cleaned after commit.
No avatar URL or file path can be supplied through profile-edit JSON. Images use no-store/nosniff headers and have UUID filenames.
No profile photos are published to other accounts by this feature.


## Hotel reviews and in-app notifications

`006_reviews_notifications.sql` creates only review/notification tables. It does not create historical or sample events.
Guests can review their own CONFIRMED reservation from its local check-out date (using the booking's time-zone snapshot).
Cancelled/future/other-account bookings are rejected. Each booking has at most one review; a PUT edits that review.
This is reservation eligibility, not physical check-in verification. New reservations become CONFIRMED after sandbox payment; historical CONFIRMED/UNPAID reservations retain their prior eligibility.
Public reviews expose guest display name, rating, comment and host response, without email, avatar URL, booking ID or reference.
Only published hotels expose public reviews. Hosts can read their own hotel's reviews and publish/edit responses; archived hotels are read-only.

Endpoints:
- `GET /api/hotels/{hotelId}/reviews?page=0&size=5` returns a page plus average/count summary.
- `GET /api/bookings/{bookingId}/review` returns eligibility/reason/current review.
- `PUT /api/bookings/{bookingId}/review` accepts `{ "rating": 5, "comment": "Great stay" }` (1–5; comment 1–2000 characters).
- `GET /api/host/hotels/{hotelId}/reviews` and `PUT /api/host/hotels/{hotelId}/reviews/{reviewId}/reply` (reply 1–1000 characters).
- `GET /api/account/notifications?page=0&size=10`, `GET /api/account/notifications/unread-count`.
- `POST /api/account/notifications/{id}/read` and `POST /api/account/notifications/read-all` persist read state for the session owner.

New booking/cancellation events notify guest and host. First review notifies host; first host response notifies guest.
Edits/retries do not create duplicate notifications. Events are saved in the booking/review transaction, so failed work emits none.
A unique recipient/event key protects event identity. Notification recipient IDs are server-owned scalar IDs, with no physical users FK:
notification insertion must not acquire another user's row lock after hotel/booking locks. A future account-deletion flow must remove these rows explicitly.
Review mutations serialize through account/hotel/booking locks and current-row review locks; host reply shares the hotel/review lock order.

The header inbox and `/notifications` load genuine server events. Unread counts refresh after local mutations, on focus and every 60 seconds while visible.
This feature provides in-app notifications only; it does not send email, SMS or browser push notifications.


## Stripe sandbox payments

See `payments.env.example` for backend-only keys and the frontend repository's `docs/PAYMENTS.md` for the complete setup, state machine, test scenarios and deployment instructions.
New reservations require configured sandbox keys unless their total is zero. Existing unpaid bookings keep their existing status.
`007_payments.sql` adds `payment_status`/`expires_at`, expands booking states, and creates durable payment attempts/webhook event records.
Use `tools/InstallPaymentSchema.java` from this directory with the MySQL JDBC driver on the Java classpath to apply the guarded, repeatable migration.
No sample payments, automatic historical charges, live keys or host payouts are included. Full pre-check-in sandbox refunds are available through the dedicated guest cancellation workflow.

New endpoints: `GET /api/payments/config`; `POST /api/bookings/{id}/checkout`; `GET /api/bookings/{id}/payment`; `POST /api/payments/webhook`.
The unpaid cancel endpoint coordinates Checkout closure before inventory release. Paid bookings use GET /api/bookings/{id}/cancellation and POST /api/bookings/{id}/refund; verified refund success cancels the booking and releases stock. See docs/REFUNDS.md.

`app.payments.enabled=false` is reserved for legacy fixture/testing workflows; the normal application default is true and unconfigured sandbox checkout fails closed.
Payment integration tests enable sandbox mode with a deterministic fake gateway. They verify raw webhook signatures, deduplication, ownership, canonical amounts,
concurrent clicks/events, expiry, ambiguous provider results and cancellation/payment races without contacting Stripe or Aiven.


## Platform Admin and hotel review

See [docs/ADMIN.md](docs/ADMIN.md) for the `/admin` workspace, opt-in administrator bootstrap, API contracts, schema migration and review workflow. Host self-publication is no longer accepted: submit for review and wait for an administrator decision. Existing published hotels are retained. Never put administrator passwords in React or public registration.

Admins can query all bookings at `GET /api/admin/bookings`, inspect saved nightly,
payment and refund records at `GET /api/admin/bookings/{id}`, and track refund
attempts at `GET /api/admin/refunds`. These endpoints check the current database
ADMIN role and only read saved records. Filters, status groups, DTO privacy and
the responsive frontend workflow are documented in [docs/ADMIN.md](docs/ADMIN.md).
No schema or environment changes are needed for these query endpoints.


## Remember me

JSON login supports an optional `rememberMe` boolean for a 30-day HttpOnly signed cookie. Set a stable backend-only `STAY_REMEMBER_ME_KEY`; see [docs/REMEMBER_ME.md](docs/REMEMBER_ME.md) for session, logout, password-reset and deployment behavior. No database migration is required.

## Deployment

See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for Docker, the `prod` profile, HTTPS/session settings, persistent images and deployment verification. Production uses schema validation and runtime secrets; local development continues to load `.env.local` through the `local` profile. GitHub Actions runs tests and packaging only; it does not deploy.

## Cloudinary

Hotel photos, room photos and avatars support backend-only Cloudinary storage. See [docs/CLOUDINARY.md](docs/CLOUDINARY.md) for local settings, authenticated delivery, safe migration of existing files and free-host deployment. No frontend credentials or database schema change is required.
