# Git and Environment Setup Guide

This guide ensures your automated deployment pipeline and application environment are correctly configured.

## 1. GitHub Secrets Setup (For CI/CD)

To allow GitHub Actions to securely deploy to your VPS, follow these steps:

1.  Navigate to your repository on [GitHub](https://github.com/Yashborse4/OldCarBackend).
2.  Go to **Settings** -> **Secrets and variables** -> **Actions**.
3.  Click **New repository secret** for each of the following:

| Secret Name | Description | Value Example |
| :--- | :--- | :--- |
| `VPS_HOST` | The IP address of your VPS | `123.45.67.89` |
| `VPS_USERNAME` | The SSH username for your VPS | `root` or `ubuntu` |
| `VPS_SSH_KEY` | Your **Private** SSH Key | `-----BEGIN OPENSSH PRIVATE KEY----- ...` |
| `DB_PASSWORD` | Database password (for CI tests) | `your_strong_password` |
| `JWT_SECRET` | JWT Signing Secret (for CI tests) | `your_random_secret_string` |

> [!TIP]
> To generate an SSH key pair if you haven't already: `ssh-keygen -t ed25519`. Add the `.pub` content to your VPS `~/.ssh/authorized_keys` and the private key content to GitHub `VPS_SSH_KEY`.

---

## 2. VPS Environment Setup (`.env` file)

When you run `vps-setup.sh`, it creates a `.env` file on your VPS. You **must** edit this file to match your production needs.

### Accessing the file on VPS:
```bash
cd OldCarBackend
nano .env
```

### Key Variables to Configure:

| Section | Variables | Description |
| :--- | :--- | :--- |
| **Database** | `DB_USERNAME`, `DB_PASSWORD` | Credentials for PostgreSQL. |
| **Security** | `JWT_SECRET` | Used for generating authentication tokens. |
| **Backblaze B2** | `B2_APPLICATION_KEY_ID`, `B2_APPLICATION_KEY`, etc. | Credentials for image storage. |
| **Search** | `OPENSEARCH_PASSWORD` | Admin password for OpenSearch. |
| **Email** | `EMAIL_USERNAME`, `EMAIL_PASSWORD` | SMTP configuration for sending emails. |

---

## 3. The Git Workflow (How to Deploy)

Once setup is complete, your daily workflow looks like this:

1.  **Develop**: Make changes locally.
2.  **Commit**: `git commit -m "feat: updated car details logic"`
3.  **Push**: `git push origin main`
4.  **Auto-Deploy**: 
    *   GitHub Actions triggers immediately.
    *   It connectes to your VPS via SSH.
    *   It pulls the latest code.
    *   It rebuilds and restarts the Docker containers automatically.

> [!NOTE]
> You can monitor the progress in the **Actions** tab of your GitHub repository.
