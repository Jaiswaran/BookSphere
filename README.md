# BookSphere 📚

**BookSphere is an Android reading and publishing app that brings readers and independent authors together.** Readers can discover books, preview uploaded manuscripts, and continue reading; authors can publish their work and manage book details from the app.

Repository: [Jaiswaran/BookSphere](https://github.com/Jaiswaran/BookSphere)

## What the app is for

BookSphere aims to make digital reading more approachable while giving authors a direct way to share their work. The project is built as a native Android application, with a local data layer for responsiveness and Supabase services for remote data and file storage.

## Features

Features represented in the current codebase include:

- **Book discovery and catalog UI** for browsing book details and sample content.
- **Author Studio** for book publishing workflows.
- **PDF-based manuscript workflow**, including upload and preview generation from uploaded PDF content.
- **Preview and full-reading access rules** so preview access and full-manuscript access can be handled separately.
- **Reader progress and local persistence** using Room and DataStore.
- **Remote backend integration** with Supabase Postgres APIs, Authentication, and Storage.
- **Google sign-in support components** through Android Credential Manager dependencies.
- **Automated tests** covering repository behavior, publishing, discovery and previews, access entitlements, security policies, and stability.

> This README describes the implemented app structure and intended workflows. Payment processing, author payouts, and production DRM are not documented as implemented features unless they are added to the codebase.

## Tech stack

| Area | Technology |
| --- | --- |
| Platform | Native Android |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Build system | Gradle with Kotlin DSL and version catalog |
| Remote backend | Supabase (Postgres/PostgREST, Auth, Storage) |
| Local persistence | Room, DataStore |
| Networking | Ktor / OkHttp-based dependencies |
| Image loading | Coil |
| Tests | JUnit, Robolectric, Android UI/instrumentation testing, Roborazzi |

## Project structure

```text
BookSphere/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/com/example/
│       │   │   ├── data/
│       │   │   │   ├── local/       # Room database, entities, DAOs, progress storage
│       │   │   │   ├── remote/      # Supabase client and DTOs
│       │   │   │   └── repository/  # Data and publishing workflows
│       │   │   ├── model/           # App models and state types
│       │   │   ├── ui/              # ViewModel, components and screens
│       │   │   └── MainActivity.kt
│       │   └── AndroidManifest.xml
│       ├── test/                    # Unit and integration-style tests
│       └── androidTest/             # Instrumentation tests
├── gradle/
│   └── libs.versions.toml           # Dependency version catalog
├── supabase/
│   └── migrations/                  # SQL migrations and RLS/storage policies
├── .env.example                     # Example local configuration
├── build.gradle.kts
└── settings.gradle.kts
```

## Requirements

- Android Studio with support for the project's Gradle and Android Gradle Plugin versions
- JDK version compatible with the Gradle project (JDK 17 is a good starting point; use the version required by your installed Android Gradle Plugin)
- Android SDK for the project's configured compile SDK
- A Supabase project for backend functionality

## Getting started

### 1. Clone the repository

```bash
git clone https://github.com/Jaiswaran/BookSphere.git
cd BookSphere
```

### 2. Open the project

Open the repository folder in Android Studio and allow Gradle to sync. Use the included Gradle wrapper where available rather than installing a separate Gradle version.

### 3. Configure Supabase

The app reads Supabase settings through the Gradle Secrets plugin. Start by reviewing `.env.example`.

Create a local `.env` file in the repository root (do not commit it) and set your project values:

```properties
SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
SUPABASE_PUBLISHABLE_KEY=YOUR_SUPABASE_PUBLISHABLE_KEY
WEB_CLIENT_ID=YOUR_GOOGLE_OAUTH_WEB_CLIENT_ID
```

- Use your own Supabase project URL and **publishable** key.
- Configure the Google OAuth web client ID if you use Google sign-in.
- Never place a Supabase service-role key or other server-side secret in the Android app.
- Keep real credentials out of Git. The checked-in `.env.example` is a template, not a place for personal secrets.

### 4. Configure the Supabase backend

1. Create or select the Supabase project used by your app.
2. Review the SQL files under `supabase/migrations/`.
3. Apply the required migrations to the target project in order, using the Supabase SQL Editor or your normal migration workflow.
4. Confirm that the relevant tables, Storage buckets, and Row Level Security (RLS) / Storage policies exist before testing publishing or uploads.

In particular, the repository includes `20261023000000_fix_reader_book_publishing_storage_policies.sql`, which updates book-publishing and Storage policies. Apply the migration if it has not already been applied to your Supabase project.

**Important:** The app's database schema and access policies must match the code. A successful Android build does not prove that remote authentication, database access, or PDF uploads are configured correctly.

### 5. Build and run

From the repository root, use the Gradle wrapper:

**Windows (PowerShell):**
```powershell
.\gradlew.bat assembleDebug
```

**macOS / Linux:**
```bash
./gradlew assembleDebug
```

Open the project in Android Studio, select an emulator or connected Android device, and run the `app` configuration. If the wrapper script is not present in your checkout, use Android Studio's Gradle sync/build actions or restore the wrapper files for the project.

## Running tests

Run the local unit-test suite with:

**Windows:**
```powershell
.\gradlew.bat :app:testDebugUnitTest
```

**macOS / Linux:**
```bash
./gradlew :app:testDebugUnitTest
```

For connected-device tests, use:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The connected-device command requires a running emulator or a connected Android device. These commands are instructions; test results depend on your local environment and backend configuration.

## Backend and data notes

- **Supabase** is the remote backend for database, authentication, and object storage operations.
- **Room** stores local app data, and **DataStore** is used for reading-progress-related persistence.
- **RLS and Storage policies matter.** Do not disable them as a shortcut for production. Configure policies to grant only the access each reader or author needs.
- PDF upload and preview flows depend on valid Storage permissions, supported file content, and the matching backend schema/policies.
- Do not commit API secrets, signing keystores, passwords, or release credentials.

## Continuous integration

A GitHub Actions workflow is present at `.github/workflows/build-apk.yml`. Check that workflow for the exact build steps and required repository secrets before relying on it for CI or release builds.

## Contributing

1. Create a branch for your change.
2. Keep changes focused and follow the existing Kotlin/Compose patterns.
3. Add or update tests for changes to publishing, preview access, repository behavior, or security rules.
4. Verify the relevant Gradle build and tests locally.
5. Open a pull request with a clear summary and testing notes.

## License

A license file was not confirmed during this repository inspection. Add a `LICENSE` file and update this section before redistributing the project under a specific license.

---

Built to help readers discover books and give authors a practical way to share their work.
