# How to Set Environment Variables

Environment variables are key-value pairs used to configure your application without changing the code. Here is how you set them in the three main areas of your project:

---

## 1. On your VPS (Production)
Your `docker-compose.yml` is configured to read from a file named `.env` in the same directory.

### Step-by-Step:
1.  **Login to your VPS** via SSH.
2.  **Navigate to the project folder**:
    ```bash
    cd OldCarBackend
    ```
3.  **Open the `.env` file**:
    ```bash
    nano .env
    ```
4.  **Add or Edit variables**:
    The format is `KEY=VALUE`. For example:
    ```env
    DB_PASSWORD=my_secure_password
    JWT_SECRET=super_secret_token_123
    ```
5.  **Save and Exit**: Press `Ctrl + O`, then `Enter` to save, and `Ctrl + X` to exit.
6.  **Apply Changes**: Docker Compose needs a restart to pick up the new values:
    ```bash
    ./deploy.sh restart
    ```

---

## 2. In GitHub Actions (CI/CD)
The automated deployment script uses **GitHub Secrets** for sensitive data.

### Step-by-Step:
1.  Go to your repository on GitHub.
2.  Settings -> Secrets and variables -> Actions.
3.  Click **New repository secret**.
4.  Name: `DB_PASSWORD` | Value: `your_password`.
5.  These are automatically available to the workflow defined in `.github/workflows/vps-deploy.yml`.

---

## 3. How to Verify (Check if it's working)
If you want to check if a variable is correctly reaching your running Java application:

1.  **List running containers**:
    ```bash
    docker ps
    ```
2.  **Run a command inside the app container**:
    ```bash
    docker exec car-selling-app env | grep JWT_SECRET
    ```
    *This will print the value of `JWT_SECRET` currently active inside the container.*

---

## 4. Local Development (Your PC)
If you are running the app locally without Docker:

*   **Windows (PowerShell)**: `$env:JWT_SECRET="my_secret"`
*   **Mac/Linux (Terminal)**: `export JWT_SECRET="my_secret"`
*   **IntelliJ/Eclipse**: Add them in the "Run/Debug Configurations" under the "Environment Variables" section.
