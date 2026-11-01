# FactoryBill

FactoryBill is an offline-first GST invoicing and factory operations app for Android. It keeps business records in a local Room database and performs bill OCR on-device.

## Features

- Create, issue, review, share, print, and void invoices; choose an invoice number or let the app assign one. Voiding preserves the record and reverses its inventory movements.
- Maintain customers, products, HSN/SAC codes, opening stock, and manual stock adjustments.
- Record supplier purchases, expenses, employee start dates, monthly salary accruals, advances, and dated payments with a per-employee ledger.
- Scan a supplier bill into purchases/stock or a sales bill into an invoice. Gemini Nano first extracts bill fields locally through Android AI Core. If it cannot read the bill, an optional Google Gemini Cloud Run service can analyze the image after the user enables cloud scanning; review every suggestion before saving.
- Configure company GST and bank details, an optional company logo for the dashboard and invoices, invoice template, light/dark theme, and accent color.
- Generate Classic, Modern, or Compact A4 invoice PDFs with party details, HSN, place of supply, tax split, balance due, and an optional UPI payment QR.

All amounts are stored as integer paise. Invoice line amounts round to paise using HALF_UP. Tax-exclusive prices calculate GST as `taxable value × rate / 100`; tax-inclusive prices extract GST as `gross value × rate / (100 + rate)`. Each line is rounded before invoice totals are summed. Intra-state GST is split between CGST and SGST (an odd paise is assigned to SGST); inter-state GST is reported as IGST.

These are standard GST billing calculations, implemented independently; this app does not copy Vyapar code. GSTIN checks validate format only, not registration status or checksum. Confirm the applicable tax treatment, HSN/SAC, place of supply, invoice numbering, and rounding with a qualified tax professional before relying on invoices for statutory filings.

## Build and test

Open the project in Android Studio with JDK 17 and Android SDK 34, or run:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Install on a connected device with `./gradlew :app:installDebug`. The Room migration instrumentation test requires an emulator or device.

## Public Firebase Hosting download

The Android APK is distributed as a public download page; Firebase Hosting does not run the Android app itself. Build a locally signed release, authenticate Firebase CLI in your own terminal (`firebase login`), then deploy to your Firebase project:

```sh
export FACTORYBILL_SIGNING_PROPERTIES="$HOME/.config/factorybill/signing.properties"
./gradlew :app:assembleRelease
export PATH="$HOME/.local/bin:$PATH" # if Firebase CLI was installed in the user profile
firebase-hosting/deploy.sh YOUR_FIREBASE_PROJECT_ID
```

The deployment script copies the signed APK into the Hosting public directory and deploys the page. The APK download is public to anyone who has the Hosting URL. Do not put credentials or API keys in the Hosting directory.

## Local release signing

Keep the release keystore and its password properties outside the repository. To sign a release locally, set `FACTORYBILL_SIGNING_PROPERTIES` to a protected properties file containing `storeFile`, `storePassword`, `keyAlias`, and `keyPassword`, then run `./gradlew :app:assembleRelease`. Do not commit the keystore or signing properties, or upload them to the public repository. Back up the keystore securely: future app updates must use the same signing key.

## Optional Gemini cloud scanning

Cloud scanning is off by default and is offered only after on-device AI cannot read a bill. The user must enable the cloud-scan switch before a bill image is sent to Google. The API key stays in Google Secret Manager and is used only by the Cloud Run service. The service requires a valid Firebase App Check token from the registered Android app, limits images to 8 MB, and caps Cloud Run at one instance.

1. Create a Google Cloud/Firebase project, register the Android app (`com.factorybill.app`), enable Firebase App Check with Play Integrity, and register the signing certificate SHA-256 fingerprints used by your APK.
2. Create a Gemini API key in Google AI Studio. Store it in Secret Manager, never in the app or repository:

   ```sh
   gcloud secrets create gemini-api-key --replication-policy=automatic
   read -rsp "Gemini API key: " GEMINI_API_KEY; echo
   printf %s "$GEMINI_API_KEY" | gcloud secrets versions add gemini-api-key --data-file=-
   unset GEMINI_API_KEY
   ```

3. From `cloud-scan/`, deploy the protected service using the same project ID as Firebase:

   ```sh
   export FACTORYBILL_FIREBASE_APP_ID="YOUR_FIREBASE_ANDROID_APP_ID"
   ./deploy.sh YOUR_GCP_FIREBASE_PROJECT_ID us-central1
   ```

   The command prints the Cloud Run service URL.
4. Build the Android app with the public Firebase app configuration and Cloud Run URL. These Firebase values identify the app and are not the Gemini API secret:

   ```sh
   export FACTORYBILL_CLOUD_SCAN_URL="https://YOUR_CLOUD_RUN_SERVICE_URL"
   export FACTORYBILL_FIREBASE_PROJECT_ID="YOUR_GCP_FIREBASE_PROJECT_ID"
   export FACTORYBILL_FIREBASE_APP_ID="YOUR_FIREBASE_ANDROID_APP_ID"
   export FACTORYBILL_FIREBASE_API_KEY="YOUR_FIREBASE_WEB_API_KEY"
   export FACTORYBILL_FIREBASE_SENDER_ID="YOUR_FIREBASE_PROJECT_NUMBER"
   ./gradlew :app:assembleDebug
   ```

The Cloud Run endpoint is publicly reachable so Android can call it, but it rejects requests without a valid App Check token. Keep Gemini API quotas/budget alerts enabled in Google Cloud. To run backend checks, use `python -m unittest discover -s cloud-scan`.

## Data and privacy

The database, company logo, and confirmed scan images are stored on-device. Gemini Nano bill extraction runs through Android AI Core on supported phones; its model may need an initial download. Bill images and OCR text stay on-device unless the user enables the optional cloud-scan switch; an enabled cloud scan sends the bill image to Google Gemini through the configured Cloud Run service. If both AI options are unavailable, the bundled Latin-script ML Kit OCR is used instead. Handwriting and other scripts may be inaccurate. Scan results are editable suggestions, never saved without explicit confirmation. No cloud sync or export/restore feature is currently provided. Uninstalling the app may remove its local business records, so arrange an appropriate backup before using it for ongoing operations.
