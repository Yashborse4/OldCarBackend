# Final Setup Checklist

Follow this list to ensure everything is configured correctly for your first deployment.

## 1. GitHub Secrets (Online)
*Go to: GitHub Repo -> Settings -> Secrets and variables -> Actions*

| Secret Name | Description |
| :--- | :--- |
| **`VPS_HOST`** | Your VPS IP Address (e.g., `123.45.67.89`). |
| **`VPS_USERNAME`** | Your SSH login name (usually `root` or `ubuntu`). |
| **`VPS_SSH_KEY`** | Your **Private** SSH Key content. |
| **`DB_PASSWORD`** | A password for the database (used during GitHub tests). |
| **`JWT_SECRET`** | A random string (used during GitHub tests). |

---

## 2. VPS Environment (`.env` file)
*File location: `~/OldCarBackend/.env`*

Login to your VPS and fill in these values in the `.env` file:

### 🔑 Core Security
* [ ] **`DB_PASSWORD`**: Set a strong password for your PostgreSQL database.
* [ ] **`JWT_SECRET`**: Run `openssl rand -hex 32` and paste the result here.

### 🖼️ Storage (Backblaze B2)
* [ ] **`B2_APPLICATION_KEY_ID`**
* [ ] **`B2_APPLICATION_KEY`**
* [ ] **`B2_BUCKET_NAME`**
* [ ] **`B2_BUCKET_ID`**
* [ ] **`B2_CDN_DOMAIN`**

### 🔍 Search & Mail
* [ ] **`OPENSEARCH_PASSWORD`**: Set a password for OpenSearch.
* [ ] **`EMAIL_USERNAME`** / **`EMAIL_PASSWORD`**: For sending emails.
* [ ] **`EMAIL_HOST`**: e.g., `smtp.gmail.com`.

---

## 3. First-Time Commands (On VPS)
Run these commands in order:

1.  **Initialize VPS**:
    ```bash
    bash vps-setup.sh
    ```
2.  **Configure Environment**:
    ```bash
    cd OldCarBackend
    nano .env  # Fill in the values from Step 2 above
    ```
3.  **Start Application**:
    ```bash
    ./deploy.sh start
    ```

---

## 4. Verification
* [ ] Check if the app is healthy: `curl http://localhost:8080/actuator/health`
* [ ] Check if all containers are UP: `docker compose ps`
