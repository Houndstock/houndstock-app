# houndstock

Track the stocks your favorite investors hold — and discover which investors back the stocks you follow.

> Indian market first. v1 covers mutual fund schemes (stocks held by funds and reverse-lookup). v2 adds marquee individual investors ("Big Bulls").

## Repo layout

```
houndstock/
├── android/      Native Android app (Kotlin + Jetpack Compose)
└── backend/      Kotlin + Ktor + Postgres (mirrors mfdata.in, serves the app)
```

## Status

Pre-alpha. Android scaffold in place; backend not yet started.

## Stack

- **Android**: Kotlin, Jetpack Compose, Material 3, Hilt, Retrofit + kotlinx.serialization, Room, WorkManager, Navigation Compose
- **Backend**: Kotlin, Ktor, Exposed (or SQLDelight), Postgres on Fly.io
- **Data**:
  - Holdings: backend mirror of [mfdata.in](https://mfdata.in/) with AMFI/AMC scrape as fallback
  - NAV: [mfapi.in](https://www.mfapi.in/) (direct from app)
