# Test and release status

Date: 26 November 2026. This report distinguishes actual checks from planned tests. **The application is not yet verified end-to-end.** Do not present it as fully tested or production-ready until the real acceptance checks pass.

## Executed in delivery environment

| Check | Result | Evidence |
|---|---|---|
| Java syntax (Java 21 compiler parse phase, no dependency resolution or type checking) | PASS | `java scripts/JavaSyntaxCheck.java src` parsed 20 Java source files |
| JavaScript syntax | PASS | `node --check` on all four JS files |
| Maven POM XML | PASS | Parsed as XML |
| Compose, Render, CI, application YAML | PASS | Parsed as YAML; provider/Docker validation not performed |
| Browser smoke test | BLOCKED | Managed Chromium returned `net::ERR_BLOCKED_BY_ADMINISTRATOR` for the local fixture server |
| Maven compile and automated integration test suite | NOT RUN | No Maven installed, no cached Spring dependencies; Maven Central DNS unavailable |
| Docker image build, MySQL and Mailpit integration | NOT RUN | Docker not installed |
| AWS S3/R2, TiDB, Render and Brevo | NOT RUN | No provisioned external accounts or credentials |

The included reference screenshot is the original design input. There is no verified screenshot from the rebuilt app yet. `scripts/ui_smoke.py` uses a test-only fixture API and can capture layout screenshots on a machine with Python and Playwright. Even passing that script would not demonstrate actual Spring API integration. It is not part of the running application.

## Automated test cases included (not executed here)

`src/test/java/com/cloudvault/VaultIntegrationTest.java` uses Spring Boot, MockMvc, H2, mocked mail and temporary disk storage. Cases cover registration, verification, reset token use, session invalidation, unauthenticated access, owner isolation, CSRF, favorites, file download, link revocation, direct sharing, soft deletion, restoration, quota enforcement, permanent deletion, demo write denial, folder hierarchy, and invalid names.

Run on Java 21/Maven: `mvn clean verify`. Run JS syntax: `node --check src/main/resources/static/app.js` (also common.js, auth.js and share.js). CI runs Maven and JS checks on every push. For optional frontend fixture checks: `pip install playwright`, `playwright install chromium`, `python scripts/ui_smoke.py`. Use `CHROMIUM_PATH` only to point to an installed browser permitted to run local tests.

## Real acceptance checklist (all pending)

- [ ] Build Maven and Docker, start MySQL, app and Mailpit, and check `/actuator/health` is UP.
- [ ] Register a new account, inspect Mailpit, click verification, confirm unverified login fails, verify and sign in. Reusing a verify token fails. Resend after cooldown. Forgot/reset works and old password/session no longer works.
- [ ] Login with demo button; file download works; upload, rename, share, delete and profile/password changes are denied server-side. Register a second real test account.
- [ ] Create folder and subfolder, rename, pin, open, delete empty. Reject duplicate names and nonempty deletion. Move a file to another folder and root; refresh to confirm persistence.
- [ ] Upload several real files, including PDF, PNG, JPG, text. Drag/drop and file chooser; compare downloaded bytes to originals. Reject empty and over-50 MiB files. Check 500 MiB quota including trash, and global cap in a test environment. Check invalid names and cross-user folder IDs.
- [ ] Check PDF and image previews in Chrome/Edge; other files download only. Try an HTML/SVG file and confirm it is not executable in the app. This is not malware scanning.
- [ ] Search, sort, favorite, navigate every sidebar page, check recent files and actual storage numbers. Mobile at 390px, tablet and desktop; keyboard focus and labels.
- [ ] Generate link, open in incognito, compare download, revoke and verify 404. Test expiry (1 hour or adjust expiry only in a disposable DB). Share with a verified second user and check recipient can read but not modify. Unrelated account cannot access.
- [ ] Soft-delete file, shares revoked, quota still used. Restore, confirm bytes. Permanently delete, confirm bytes gone and usage decreases. Empty trash.
- [ ] Restart local containers without deleting volumes; verify records and files persist.
- [ ] Configure a private AWS S3 bucket or R2 test bucket; test upload, download, delete, invalid credentials, no public object access. Check failure behavior.
- [ ] Deploy a test app to selected online host; verify TLS, secure cookies, cloud DB schema, S3 persistence across redeploy, real Brevo verification/reset email, free-tier limits, and logs. Check cold-start behavior and maximum upload size in hosting platform.
- [ ] Review dependency vulnerabilities and update to a supported Spring Boot generation before wide public exposure; confirm license and hosting terms. Run security and abuse checks.
- [ ] Capture real screenshots, update README and resume claims with verified behavior and actual URL.

## Known limitations and design decisions

- This is a single-instance educational application. In-memory throttling resets after restart and can be affected by reverse proxy setup; registration and global quota caps are not distributed controls. No CAPTCHA, malware scanner, audit trail, backups, automated orphan cleanup or per-download bandwidth limit. Public links are bearer tokens; avoid logging URLs and sensitive documents.
- No end-to-end encryption, file version history, 2FA, email change or account deletion. PDF preview depends on the browser's native viewer. Header detection is limited; files can still be malicious. Avoid sensitive uploads.
- Account email existence can be inferred through signup/direct-share validation. Reset delivery failures may be distinguishable. Production deployment needs abuse and enumeration review. Demo credentials are intentionally public and read-only, not real secrets.
- File storage and DB are separate systems. Upload rollback attempts object cleanup; storage/DB outages or a process crash can still leave orphan objects or missing bytes. Permanent delete is not atomic across systems. Folder deletion requires it be empty including trash and subfolders.
- Schema is auto-generated by Hibernate, scalar reference IDs are service-validated without database foreign keys. Add migrations and explicit FKs before maintaining a long-lived production database. H2 compatibility does not prove real MySQL/TiDB behavior.
- Spring Boot 3.5.16 is the last open-source 3.5.x patch (June 2026). It preserves the familiar Spring Boot 3 stack but has ended OSS maintenance; a supported generation and dependency scan are recommended before broad public exposure. No vulnerability scan was executed here.
- Free Render service may sleep or suspend, AWS credits expire, R2 billing risk remains, and TiDB is MySQL-compatible rather than identical MySQL. Brevo may require account/sender/domain approval. No public URL has been created. See DEPLOYMENT.md.
- Layout is recreated from the screenshot using custom CSS and Bootstrap assets; it is not pixel-identical and browser visual verification was blocked.
