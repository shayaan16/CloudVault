# Publish CloudVault to GitHub

1. Run the application and tests first; correct failures. Create actual screenshots of your working dashboard and save them under `docs/screenshots/` (no passwords, email tokens, private files or credentials visible). Link those images in README. The included `design-reference.png` is the input reference, not a verified result.
2. Create a new repository at https://github.com/new (suggested name `CloudVault`). Do not initialize it with a README, as the project already contains one. Start private if tests are pending; make it public when verified.
3. In the project's VS Code PowerShell terminal:

```powershell
git init
git branch -M main
git status --short
git check-ignore .env
git add .
git diff --cached --stat
git diff --cached --name-only
git commit -m "Initial CloudVault implementation"
git remote add origin https://github.com/YOUR_USERNAME/CloudVault.git
git push -u origin main
```

4. Confirm `.env`, credentials, local `data/`, build output, logs and real user uploads are absent. `.env.example` is intentionally public. If a key was committed, immediately revoke it and remove it from history; deleting a file in a later commit does not revoke a key.
5. Open the **Actions** tab. `CloudVault CI` installs Java 21 and runs `mvn verify`, followed by JavaScript syntax checks. Fix any failures; do not add a passing badge until a real workflow is green. The H2 test is not a real MySQL or S3 test.
6. Update README with actual screenshots, verified feature status, GitHub repository and actual public URL if deployed. Link the repository in your resume. Use clear, honest incremental commits for improvements you make and understand; do not fabricate past commits or authorship.

For subsequent edits: `git status`, `git add <changed-files>`, `git commit -m "Describe the change"`, `git push`. If Git asks for credentials, use Git Credential Manager or GitHub CLI rather than storing a password in the URL.
