# Deploy CloudVault online

## Cost decision (checked 26 November 2026)

No permanent free hosting is guaranteed. Do not create a paid plan or enable automatic upgrades. The application defaults to a 2 GiB global file cap, 30 registrations (20 in Render config), 500 MiB per account, 50 MiB per upload and 1,000 files per account. These limits do not cap bandwidth, API charges or provider billing. Use non-sensitive samples only.

| Component | Recommended service | Limit / trade-off |
|---|---|---|
| Temporary public demo | Cloudflare Quick Tunnel | No account, random HTTPS URL, computer must stay on; development only |
| Web app | Render free Docker web service | 512 MB RAM, cold starts, ephemeral disk; no SLA |
| Database | TiDB Cloud Starter, MySQL-compatible | Free quota; no spend limit upgrade; not actual MySQL Server |
| Files | AWS S3 | New AWS Free plan credits/time-limited, up to 6 months; not permanent |
| Alternative files | Cloudflare R2 Standard | S3-compatible, 10 GB-month free allowance; billing method / usage charges may apply |
| Email | Brevo transactional HTTP API | Free allowance (300 emails/day when eligible); sender verification and account approval required |

Aiven stopped allowing new free MySQL services on 6 November 2026; retirement of remaining free MySQL services is scheduled for 21 December 2026. Do not follow old Aiven tutorials. If you require genuine MySQL Server and zero hosting expense, use the temporary local tunnel or find a separately verified free VM. TiDB compatibility is not identical to MySQL; run all acceptance checks on it.

## A. Completely free temporary public demo

1. Complete the local setup first. Ensure the app listens at `http://localhost:8080` and only sample data exists. Install `cloudflared` from https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/downloads/ (or use `winget install --id Cloudflare.cloudflared` if available).
2. In another terminal run `cloudflared tunnel --url http://localhost:8080`. Copy the generated `https://...trycloudflare.com` address. This is a temporary link, not a stable resume URL.
3. Set `BASE_URL` to the generated HTTPS URL in `.env`, `COOKIE_SECURE=true`, and recreate app: `docker compose up -d --force-recreate app`. Leave the tunnel running. Use the HTTPS address for sign-in now; the secure cookie may not be available on plain localhost.
4. For verification/reset emails to real recipients, configure Brevo in `.env` with `MAIL_MODE=brevo`, `MAIL_FROM` and `BREVO_API_KEY` as described below; recreate app. If staying with Mailpit, verify your demo accounts locally before showing the public URL. Do not expose Mailpit or MySQL through the tunnel.
5. Test from a separate browser/device. Stop the tunnel with Ctrl+C after the demo; restore local BASE_URL and COOKIE_SECURE=false. A new quick tunnel creates a different URL. A real stable deployment is below.

## B. AWS account and S3 (temporary free-credit option)

1. Go to https://aws.amazon.com/free/ and select the Free account plan if available and eligible. Verify email/phone, identity and payment method as requested by AWS. Review account-plan expiration and credit balance; do not switch to Paid plan merely to keep a demo online. If IAM or S3 is not available in your Free account plan, use the R2 option or local tunnel; do not upgrade automatically.
2. Enable MFA for root and never create root access keys. Open S3 → Create bucket. Choose a unique name and region (e.g. `ap-south-1`), keep **Block all public access** on, Object Ownership bucket owner enforced, default SSE-S3 encryption on. Keep versioning off for this educational single-version app; no public bucket policy. Do not enable paid extras.
3. Create a dedicated IAM user/role for this app (programmatic access only). For external Render hosting, create a scoped access key only if permitted. Attach an inline policy restricted to your bucket objects, replacing `YOUR_BUCKET`:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::YOUR_BUCKET/*"
  }]
}
```

4. Copy the access key ID and secret once into Render secrets (or private `.env` for local S3 testing). Never commit or screenshot them. The AWS SDK uses its default credential chain. Set `STORAGE_MODE=s3`, `S3_BUCKET`, `AWS_REGION`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`; leave `S3_ENDPOINT` blank. If Render Blueprint demands a value for empty endpoint, configure manually and omit it entirely.
5. Monitor Billing & Cost Management, credits, usage and alerts. A budget alert is not a hard cap. AWS states new Free account plans last up to six months; avoid exceeding eligible usage. Download your sample data and remove the app, bucket objects and access keys before the credits/plan expire. AWS S3 costs may apply if you choose a paid plan.

## C. Alternative S3-compatible storage: Cloudflare R2

If you need a potentially longer-running free-allowance demo, create a private R2 **Standard** bucket. Account activation may require a payment method; the free usage allowance is not a hard cap. If you cannot accept any payment risk, use A instead.

1. Cloudflare Dashboard → R2 Object Storage → create bucket. Disable public access and `r2.dev`.
2. Manage R2 API Tokens → create Object Read & Write token scoped to only that bucket. Copy access key ID, secret and S3 API endpoint from the dashboard.
3. Set `STORAGE_MODE=s3`, `S3_BUCKET`, `AWS_REGION=auto`, `S3_ENDPOINT=https://YOUR_ACCOUNT_ID.r2.cloudflarestorage.com`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`.
4. Verify upload/download/delete; this uses the S3-compatible protocol but is **not Amazon AWS S3**, and must be described accurately on your resume. The code sets path-style access for custom endpoints. If a newer AWS SDK sends a checksum unsupported by an endpoint, use the SDK's documented request checksum setting `AWS_REQUEST_CHECKSUM_CALCULATION=WHEN_REQUIRED` and response checksum validation `AWS_RESPONSE_CHECKSUM_VALIDATION=WHEN_REQUIRED` after verifying compatibility. Track Class A/B request and storage allowances.

## D. MySQL-compatible online database

1. Sign up at https://tidbcloud.com/ and create a Starter instance using **MySQL-compatible** mode. Use a region close to Render if possible. Select free allowances; do not add a payment method or enable paid capacity unless explicitly desired. Check the quota page and set a zero spend limit if available.
2. Create a dedicated `cloudvault` database (SQL editor: `CREATE DATABASE cloudvault;`) and a database user with privileges on that database only; use the console connection settings for hostname, port, username (often includes a cluster prefix) and password. Ensure public endpoint/IP allowlist permits Render's documented outbound IPs if required, and enforce TLS.
3. Set `DB_URL=jdbc:mysql://YOUR_HOST:4000/cloudvault?sslMode=VERIFY_IDENTITY&serverTimezone=UTC`, `DB_USER=YOUR_PREFIX.USER`, `DB_PASSWORD=YOUR_PASSWORD`. Use exact hostname/port from the console; if JDBC TLS fails, install the provider's CA trust chain according to its JDBC docs, not `sslMode=DISABLED`. Test login and schema creation; Hibernate auto-detects the dialect. If it cannot, first diagnose connection and compatibility.
4. Do not put file bytes in the DB. TiDB is compatible with the MySQL wire protocol but requires real integration testing. Export metadata for backups.

## E. Email service (Brevo)

1. Create an account at https://www.brevo.com/. Complete any account review, add and verify your sender under Senders & Domains; authenticate your domain if required. Free provider onboarding can require a domain or additional verification. If you cannot obtain an approved sender, public email delivery is not ready; use local Mailpit for local testing and restrict the public demo to already verified accounts.
2. Generate a scoped/managed API key under SMTP & API. Set `MAIL_MODE=brevo`, `MAIL_FROM=verified-sender@example.com`, `BREVO_API_KEY=...` in the host secrets. Do not use SMTP 587 on Render free: it blocks common SMTP ports. This project uses the HTTPS transactional API.
3. Ensure `BASE_URL` matches the public HTTPS URL. Register and confirm that an actual email arrives. Test reset and resend. If Brevo rejects API calls, inspect the provider's logs, account approval, sender verification and IP security settings. Do not disable account verification to conceal email failure.

## F. Deploy on Render

1. First push to GitHub using `GITHUB_GUIDE.md` and run the CI Maven suite. Make sure every local acceptance check has passed.
2. At https://render.com/ create a Web Service linked to the repo. Choose Docker runtime and **Free** instance type. Set health check path `/actuator/health`. The Dockerfile builds the app. `render.yaml` is provided as a starting Blueprint; manual setup is fine.
3. Enter secret environment variables in the Render dashboard; do not commit them:

| Key | Value |
|---|---|
| DB_URL, DB_USER, DB_PASSWORD | From step D |
| BASE_URL | Actual Render HTTPS URL, update after first URL assigned |
| COOKIE_SECURE | true |
| STORAGE_MODE | s3 |
| S3_BUCKET, AWS_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY | Step B or C |
| S3_ENDPOINT | Omit for AWS; custom HTTPS endpoint for R2 |
| MAIL_MODE, MAIL_FROM, BREVO_API_KEY | Step E |
| DEMO_ENABLED | true |
| MAX_ACCOUNTS | 20 |
| GLOBAL_LIMIT_BYTES | 2147483648 |

Render sets `PORT`; Spring Boot reads it. The Dockerfile limits Java heap percentage for a small container. If memory use exceeds 512 MB, reduce concurrent usage; do not silently switch to paid. First requests can be slow after idle sleep. Local uploads on Render's ephemeral filesystem are unsafe; always use external object storage there.

4. Deploy; inspect build and runtime logs. Open `/actuator/health` and the website. Verify the demo, two private accounts, actual verification/reset messages, 50 MB boundary, owner isolation, sharing/revoke, trash/restore and file persistence across service restart. Replace `BASE_URL` if necessary and redeploy.
5. Copy the HTTPS URL to the README and resume only after tests pass. Set a separate test account, no real personal files. Disable public demo via `DEMO_ENABLED=false` if needed; existing sessions may remain for up to session timeout unless the service restarts. Free instances can suspend, quotas change, and public file sharing can be abused; monitor usage and shut down if needed.

## Sources / official docs

- Render free limitations: https://render.com/docs/free and https://render.com/pricing
- Aiven retirement: https://aiven.io/changelog/507f8b1b-dd8f-4841-ac4a-940ac492b688
- TiDB Starter quota: https://docs.pingcap.com/tidbcloud/select-cluster-tier/ ; JDBC: https://docs.pingcap.com/developer/dev-guide-sample-application-java-jdbc/
- AWS S3 pricing: https://aws.amazon.com/s3/pricing/ ; free plans: https://docs.aws.amazon.com/us_en/awsaccountbilling/latest/aboutv2/free-tier.html
- R2 pricing: https://developers.cloudflare.com/r2/pricing/ ; setup: https://developers.cloudflare.com/r2/get-started/
- Brevo: https://developers.brevo.com/docs/send-a-transactional-email ; https://help.brevo.com/hc/en-us/articles/208580669-FAQs-What-are-the-limits-of-the-Free-plan
- Quick Tunnel: https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/

These instructions are configuration guidance, not proof that external accounts have been provisioned. Free plan terms and dashboard layouts may change.
