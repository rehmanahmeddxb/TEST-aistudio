# Childcare App

Monorepo containing:

| Folder        | Purpose                                          |
|---------------|--------------------------------------------------|
| `backend/`    | Flask server (upload / fetch logs)               |
| `ChildApp/`   | .NET MAUI Android app that uploads device logs   |
| `ParentApp/`  | .NET MAUI Android app that displays logs         |
| `.github/`    | GitHub Action that builds both APKs + backend zip|

## How to get the 3 artifacts

1. Push this folder to a GitHub repository.
2. Go to **Actions** tab → run *Build Child, Parent APKs and Backend Bundle* (or push a commit).
3. After the run completes, open the run page and download:
   - `child-apk`      → ChildApp APK
   - `parent-apk`     → ParentApp APK
   - `backend-bundle` → backend.zip (deploy to PythonAnywhere)

## Local backend

```bash
cd backend
pip install -r requirements.txt
python app.py
# Server runs on http://localhost:5000
```
