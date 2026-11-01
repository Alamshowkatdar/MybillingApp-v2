# FactoryBill

FactoryBill is an offline-first GST invoicing and factory operations app for Android. It keeps business records in a local Room database and performs bill OCR on-device.

## Features

- Create, issue, review, share, print, and void invoices; voiding preserves the record and reverses its inventory movements.
- Maintain customers, products, HSN/SAC codes, opening stock, and manual stock adjustments.
- Record supplier purchases, expenses, employee salary, advances, and payments.
- Scan a bill with the camera or choose a photo, review and edit OCR suggestions, then explicitly confirm before issuing.
- Configure company GST and bank details, invoice template, light/dark theme, and accent color.
- Generate invoice PDFs with party details, HSN, place of supply, tax split, balance due, and an optional UPI payment QR.

All amounts are stored as integer paise. Invoice line amounts round to paise using HALF_UP. Tax-exclusive prices calculate GST as `taxable value × rate / 100`; tax-inclusive prices extract GST as `gross value × rate / (100 + rate)`. Each line is rounded before invoice totals are summed. Intra-state GST is split between CGST and SGST (an odd paise is assigned to SGST); inter-state GST is reported as IGST.

These are standard GST billing calculations, implemented independently; this app does not copy Vyapar code. GSTIN checks validate format only, not registration status or checksum. Confirm the applicable tax treatment, HSN/SAC, place of supply, invoice numbering, and rounding with a qualified tax professional before relying on invoices for statutory filings.

## Build and test

Open the project in Android Studio with JDK 17 and Android SDK 34, or run:

```sh
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Install on a connected device with `./gradlew :app:installDebug`. The Room migration instrumentation test requires an emulator or device.

## Data and privacy

The database and confirmed scan images are stored on-device. OCR runs locally using the bundled Latin-script ML Kit model; handwriting and other scripts may be inaccurate. Scan results are editable suggestions, never issued without explicit confirmation. No cloud sync or export/restore feature is currently provided. Uninstalling the app may remove its local business records, so arrange an appropriate backup before using it for ongoing operations.
