# CloudVault REST API

Base URL: `http://localhost:8080` (or actual deployed HTTPS URL). All paths start with `/api` except health. JSON request/response unless otherwise specified. All workspace endpoints require a session cookie. `GET /api/auth/csrf` returns `{ "token": "...", "header": "X-CSRF-TOKEN" }`; keep the returned session cookie and use the header/token for POST/PATCH/DELETE. Refresh the CSRF token after login/logout. Spring Security processes form login (`application/x-www-form-urlencoded`), not JSON. All IDs are numeric.

## Account

| Method | Endpoint | Body / response |
|---|---|---|
| GET | /api/auth/csrf | Token and header; public |
| POST | /api/auth/register | name, email, password; sends verify email |
| POST | /api/auth/login | Form email, password; session cookie |
| POST | /api/auth/logout | Invalidates session |
| POST | /api/auth/resend | email; verification message |
| POST | /api/auth/verify | token (from verification link) |
| POST | /api/auth/forgot | email; generic response |
| POST | /api/auth/reset | token, password |
| GET | /api/auth/me | id, name, email, demo |
| PATCH | /api/auth/me | name |
| POST | /api/auth/password | currentPassword, newPassword; invalidates sessions |

## Folders

| Method | Endpoint | Body / response |
|---|---|---|
| GET | /api/folders | Array of id, parentId, name, quickAccess, openedAt, fileCount |
| POST | /api/folders | name, parentId (nullable); 201 |
| PATCH | /api/folders/{id} | name and/or quickAccess |
| POST | /api/folders/{id}/open | Updates last-opened timestamp |
| DELETE | /api/folders/{id} | Deletes only empty folder |

## Files and storage

| Method | Endpoint | Body / response |
|---|---|---|
| GET | /api/files | scope=all/folder/recent/favorites/trash/root; optional folderId and q |
| POST | /api/files | Multipart `file`, optional folderId; 201, file metadata |
| PATCH | /api/files/{id} | name, favorite and/or move=true with folderId (null for root) |
| GET | /api/files/{id}/download | Attachment bytes; preview=true allows PDF/safe raster MIME types |
| DELETE | /api/files/{id} | Soft delete; revokes shares |
| POST | /api/files/{id}/restore | Restore from trash |
| DELETE | /api/files/{id}/permanent | Delete bytes and metadata; trash only |
| DELETE | /api/trash | Purge all trash, one file per transaction; partial progress possible on failure |
| GET | /api/stats | used, limit, documents, media, other, count, trash |

Example file DTO:

```json
{"id":7,"folderId":1,"name":"notes.pdf","mimeType":"application/pdf","sizeBytes":2048,"favorite":false,"uploadedAt":"2026-11-26T10:00:00Z","deletedAt":null}
```

## Sharing

| Method | Endpoint | Body / response |
|---|---|---|
| POST | /api/files/{id}/links | hours (1–168), returns id, token, expiresAt once |
| POST | /api/files/{id}/shares | email of verified recipient, hours (1–168) |
| GET | /api/shares/outgoing | Owner's links/direct grants; no raw link tokens |
| GET | /api/shares/incoming | Active direct grants for current user |
| GET | /api/shares/{id}/download | Authorized recipient download, optional preview=true |
| DELETE | /api/shares/{id} | Owner revokes share |
| GET | /api/public/{token} | Public file name, size, MIME only |
| GET | /api/public/{token}/download | Public attachment for valid unexpired link |

Link format: `https://your-host/share.html#TOKEN` (frontend submits token to public API). Revocation or deletion blocks future downloads, not copies already downloaded. Direct sharing does not transfer ownership.

## Common statuses

200 success, 201 created, 400 validation, 401 sign-in required, 403 demo/CSRF restriction, 404 absent or unauthorized item, 409 quota/duplicate/non-empty folder, 413 too large, 429 throttled, 503 storage or mail unavailable. Expected errors normally use `{ "message": "..." }`. Unexpected errors use Spring Boot's default error response without internal message.

## Postman workflow

Import `CloudVault.postman_collection.json`. Keep cookies enabled. Execute CSRF → Login → CSRF again → files, stats and folder requests. Demo can only read. For a writable account, register in the web UI or collection, use Mailpit to get the verification token, and log in with that account. Replace variables `fileId`, `folderId`, `shareId`, `publicToken` as appropriate. The upload request requires selecting a real file in Postman. For email reset/verification copy the fragment token after `=` from the email link. Do not save real credentials in exported environments.
