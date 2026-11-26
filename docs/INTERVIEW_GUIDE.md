# CloudVault technical interview preparation

## 60-second explanation

CloudVault is a full-stack file storage and sharing application. I use Java and Spring Boot for REST APIs, a web frontend for the workspace, MySQL for metadata, and local disk or S3 for the actual file content. Users have private accounts, can create folders, upload and recover files, and grant read-only access through expiring links or another registered account. Spring Security handles session authentication, BCrypt passwords and CSRF protection, while the service layer checks resource ownership. Docker Compose packages the app with the database and local email testing. I kept it as a monolith because the scope is small and it makes deployment and debugging simpler.

Practice this only after running the project and independently tracing the source.

## Questions and answers

1. **Why separate files and database metadata?** Object storage streams large binary files efficiently; SQL supports metadata queries, ownership relationships and transactions. Only the object's random key is stored in MySQL.
2. **What is REST?** A resource-oriented HTTP API. GET reads, POST creates/acts, PATCH changes selected fields, DELETE removes or soft-deletes. HTTP status codes communicate outcomes.
3. **Why Spring Boot?** It configures a web server, dependency injection, JPA and security from starters and configuration, reducing manual setup. The app starts from `CloudVaultApplication`.
4. **Controller vs service vs repository?** The controller maps HTTP, the service applies business and authorization rules, and the repository handles persistence. This makes the logic testable without a browser.
5. **Why constructor injection?** Dependencies are explicit and can be final. Tests can substitute implementations without hidden field injection.
6. **How is login secured?** Spring Security authenticates an email/password against BCrypt, requires verification, rotates session identity, and sends an HttpOnly session cookie. Mutations require a CSRF token.
7. **BCrypt vs SHA-256?** BCrypt is deliberately slow and salted for passwords. High-entropy random tokens can use fast SHA-256 for server-side lookup because they are not human-chosen passwords.
8. **What is authorization?** After authentication identifies a user, every private resource request checks that user owns the file/folder or has a valid grant. Changing an ID in the URL does not grant access.
9. **How does email verification work?** Generate a random single-use token, store its digest and expiry, send a link; POST the token from the confirmation screen, lock the account and consume the token. Expiry is 24 hours.
10. **How does password reset work?** Send a reset token to a verified account; the token expires in 30 minutes and can be redeemed once. The password hash is replaced and authVersion incremented, invalidating previous sessions.
11. **Why CSRF if already logged in?** Browsers send cookies automatically. An attacker could cause unwanted state changes unless the request also has a token tied to the session.
12. **How does share-link expiry work?** The server looks up SHA-256(token), checks expiry and whether the original file is active. The bucket remains private. Revocation deletes the grant; it cannot recall downloaded copies.
13. **Direct user sharing?** The grant stores the recipient account ID, and each download checks the currently authenticated recipient and expiry. It does not copy or transfer file ownership.
14. **What is a transaction?** A group of database operations committed or rolled back together. This cannot atomically include S3; compensation handles some failures, and durable reconciliation would be needed for production.
15. **How do you prevent quota races?** Per-account pessimistic row locking and a single-instance serialized upload endpoint ensure the checks and metadata write are coordinated. Multiple app instances would need shared locking/quotas.
16. **Why use UUIDs?** File names can collide or contain dangerous characters. UUID object keys separate user-visible names from storage paths and keep stored keys unique.
17. **How is a 50 MB upload handled?** Multipart limit and service size checks, disk-backed multipart buffering, then streaming to object storage. Multiple files are sent as individual requests.
18. **What is soft deletion?** Set `deletedAt`; hide from active list, retain bytes and quota. Restore clears timestamp, purge deletes bytes and metadata. Shares are revoked on soft delete.
19. **How does search work?** Parameterized JPA query retrieves only the user's files and filters names case-insensitively in the service. For larger datasets use a DB query and pagination/full-text indexes.
20. **What are JPA and Hibernate?** JPA defines persistence interfaces/annotations; Hibernate implements them. `EntityManager` maps entities to rows and JPQL queries to SQL.
21. **Why Docker Compose?** Runs application, database and local mail service on a private network; named volumes persist files and metadata. The application waits for DB health.
22. **How is frontend state updated?** JavaScript Fetch calls REST endpoints, then refreshes folders/files/stats. DOM nodes are created using textContent and event handlers rather than unsafe raw HTML.
23. **How are previewed files restricted?** Only PDF and selected raster image MIME types are recognized using header bytes. Other uploaded bytes are served as attachments. This is not antivirus; native viewers and malicious PDFs still present risk.
24. **How do you test it?** MockMvc tests request routing, session, CSRF, ownership, sharing, quotas, nested folders and trash using H2 and temporary local storage. Real MySQL, AWS and email need separate tests. Never claim tests passed without results.
25. **What would you improve?** Flyway migrations and foreign keys, malware scanning, durable deletion/retry jobs, per-recipient/email throttling, pagination, audit logging, backup/restore, stronger distributed limits and dependency upgrades.

## Demonstration sequence (5 minutes)

Sign into read-only demo first → register a test user and verify via Mailpit or real mail → create a folder and upload a PDF/image → rename, move, favorite and search → preview/download → create link, open in incognito → revoke and prove access fails → share directly to a second verified account → delete to trash, restore and permanently delete → show storage gauge updates → show Controller/Service/Repository code and Docker Compose. Do not use real personal documents. If a feature has not passed the real run, state that limitation rather than faking the demo.

## Learning tasks

- Trace an upload from `app.js` through controller to `VaultService` and `StorageService`; explain every parameter.
- Add one validation test, change the folder limit, and implement a small additional sort/filter yourself.
- Read generated SQL and demonstrate the `vault_files` row and corresponding local object.
- Test access denial as a second user and explain why UI button hiding is not a security control.
