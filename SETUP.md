# Setup: Azure AD (Entra ID) App Registration

This app signs users in with their work/school (Entra ID) Microsoft account via MSAL, then reads
their Outlook calendar with Microsoft Graph. Before it can sign anyone in for real, you need to
register the app in the Azure Portal and plug the resulting values into a local, gitignored
config file. The project builds and runs without this (it ships a placeholder config), but sign-in
will fail with an MSAL configuration error until you complete these steps.

## 1. Register the app in Azure

1. Go to the [Azure Portal](https://portal.azure.com) -> **Microsoft Entra ID** -> **App
   registrations** -> **New registration**.
2. Name it anything you like (e.g. "Outlook Calendar Sync - Android").
3. Under **Supported account types**, choose **Accounts in any organizational directory**
   (or **this organizational directory only**, if you want to restrict it to a single tenant).
   Do **not** choose an option that includes personal Microsoft accounts
   (outlook.com/live.com) -- this app only supports work/school accounts.
4. Leave **Redirect URI** blank for now; you'll add an Android platform redirect URI in step 3.
5. Click **Register**, then copy the **Application (client) ID** from the Overview page -- you'll
   need it below.

## 2. Add API permissions

1. In your new app registration, go to **API permissions** -> **Add a permission** -> **Microsoft
   Graph** -> **Delegated permissions**.
2. Add `Calendars.Read` and `User.Read` (User.Read is usually pre-added).
3. This app never requests write scopes -- if you see `Calendars.ReadWrite` anywhere, that's not
   this app's config.
4. If your tenant requires admin consent for these scopes, have a tenant admin grant consent
   (**Grant admin consent** button), or consent interactively on first sign-in if your tenant
   allows user consent.

## 3. Compute your redirect URI signature hash and register it

MSAL's Android redirect URI embeds a Base64-encoded hash of your app's signing certificate:

```
msauth://<package-name>/<signature-hash>
```

For this project, `<package-name>` is `com.example.outlook_calendar_integration_android`.

Compute the signature hash for your debug keystore (replace the keystore path if yours differs):

```bash
keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore \
  -storepass android | openssl sha1 -binary | openssl base64
```

This prints something like `4h8dcdgh6i3mba2rrdvfw8fq5ci=`. URL-encode it (mainly `+` -> `%2B`,
`/` -> `%2F`, `=` -> `%3D`) if it contains those characters, then:

1. In `app/build.gradle.kts`, set `manifestPlaceholders["msalRedirectUriHash"]` (in
   `defaultConfig`) to that hash.
2. In your local `app/src/main/res/raw/msal_config.json` (see step 4), set `redirect_uri` to:
   `msauth://com.example.outlook_calendar_integration_android/<signature-hash>`
3. Back in the Azure Portal, go to **Authentication** -> **Add a platform** -> **Android**.
   - Package name: `com.example.outlook_calendar_integration_android`
   - Signature hash: the value you computed above (unencoded form)
   - Save.

Repeat this with your release keystore's signature hash before shipping a release build, and add
a second Android platform entry in Azure for it.

## 4. Fill in your local MSAL config

The checked-in `app/msal_config.json.template` is a placeholder. On every build, a Gradle task
(`ensureMsalConfig`) copies it to `app/src/main/res/raw/msal_config.json` if that file doesn't
already exist -- this file is gitignored and never committed, since it contains your
tenant-specific client ID.

1. After the first build, open `app/src/main/res/raw/msal_config.json`.
2. Replace `PLACEHOLDER_CLIENT_ID` with the Application (client) ID from step 1.
3. Replace `PLACEHOLDER_SIGNATURE_HASH` in `redirect_uri` with your real signature hash from
   step 3.
4. If you restricted the app registration to a single tenant in step 1, change the `authorities`
   entry's `audience.type` to `AzureADMyOrg` and set `tenant_id` to your tenant's ID or verified
   domain (instead of the multi-tenant default `organizations`).
5. Re-run `manifestPlaceholders["msalRedirectUriHash"]` in `app/build.gradle.kts` to match (step
   3.1), then rebuild.

Note that `ensureMsalConfig` never overwrites a `msal_config.json` you've already filled in --
delete the file if you want it regenerated from the template.

## 5. Run it

```bash
./gradlew installDebug
```

Launch the app on a device or emulator with Google Play services, tap **Sign in with Microsoft**,
and complete the Entra ID sign-in / consent flow. You should land on the Agenda screen showing
your next 30 days of Outlook events.

## Troubleshooting

- **`AADSTS...` redirect URI mismatch error**: the signature hash in `msal_config.json`'s
  `redirect_uri`, in `manifestPlaceholders["msalRedirectUriHash"]`, and in the Azure Portal's
  Android platform config must all match exactly (percent-encoding included).
- **Personal account rejected**: expected -- this app only supports work/school (Entra ID)
  accounts, per its design.
- **Consent screen loops or errors**: check that `Calendars.Read` and `User.Read` are added under
  API permissions and, if required by your tenant, that admin consent has been granted.
