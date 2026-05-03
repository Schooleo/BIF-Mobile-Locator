# Privacy Policy for Bring In Friends

**Last updated:** May 3, 2026

This Privacy Policy describes how the Bring In Friends team ("we", "us", or "our") collects, uses, stores, and discloses information when you use the Bring In Friends mobile application and related backend services (the "Service").

By using the Service, you agree to the collection and use of information in accordance with this policy.

## 1. Information We Collect

### A. Account and profile data

When you register for or use an account, we may collect and store:

- name or display name
- email address
- avatar metadata such as avatar letter, avatar color, and avatar image URL
- authentication-related records required to operate login, refresh, logout, password change, email OTP verification, and password reset flows

### B. Social, trip, and content data

To provide the app's core features, we may collect and store data that you create or share, including:

- friend relationships and friend requests
- groups and group membership data
- chat messages and chat acknowledgements
- favorites, notes, ratings, and reviews
- trip plans, trip stops, collaborators, and trip cover image URLs
- sync metadata required to reconcile offline and online changes

### C. Location and map-related data

The app may request access to your device's location while you are actively using the app.

We may process or transmit coordinates that you choose to use for features such as:

- showing your current location on the map
- route calculation
- place resolution or place search refinement
- city map bundle download
- optional AI place suggestion biasing
- explicit location sharing inside supported social/chat features

We do **not** request background location access in the current Android app.

### D. Media upload data

If you upload profile or trip-related media, the Service may process:

- avatar images
- trip cover images
- other supported image URLs or file metadata tied to app entities

### E. Usage and diagnostics data

The Android app includes Firebase Analytics, which may collect aggregate app-usage and device metadata such as:

- app version
- device model
- operating system information
- screen/session interaction events processed by Google

For more information, please review [Google Privacy & Terms](https://policies.google.com/privacy).

## 2. How We Use Information

We use collected information to:

- authenticate users and secure accounts
- deliver map, social, favorites, chat, trip, review, and sync features
- send registration and password-reset OTP emails
- upload and resolve media references
- calculate routes and improve place-related workflows
- support optional AI-assisted place suggestions and trip drafting
- monitor reliability, diagnose issues, and improve the Service

## 3. Permissions

The current Android app may request these permissions:

- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for foreground map and location features
- `ACCESS_NETWORK_STATE` to adapt to connectivity conditions
- `INTERNET` to communicate with backend, sync, media, analytics, and map-related services

## 4. Storage and Processing

Depending on deployment and enabled features, data may be processed or stored through:

- **local on-device storage** including app preferences and Room-based offline data
- **MongoDB** for backend persistence
- **Typesense** for optional place-search indexing and queries
- **Cloudinary** for signed media upload flows when configured
- **Brevo** for OTP email delivery when configured
- **OSRM** for route computation
- **Ollama** for optional self-hosted AI features
- **Nominatim/OpenStreetMap-related services** for enabled geocoding or map data flows
- **Firebase Analytics (Google)** for analytics telemetry

Some of these services are self-hosted by the project operator, while others are third-party services governed by their own terms and privacy policies.

## 5. Data Retention

We retain data for as long as reasonably necessary to operate the Service, including:

- account/profile records
- favorites and reviews
- social graph and chat records
- trip planning records
- sync/change metadata
- media references

Analytics data is retained according to the policies of the analytics provider. Operators of a deployed instance may apply different retention periods based on their infrastructure and legal obligations.

## 6. Data Sharing and Disclosure

We do not sell personal information.

We may disclose or transmit data only as needed to:

- operate the Service and its configured processors
- fulfill user-requested features such as routing, media upload, AI assistance, sync, or email OTP delivery
- comply with applicable law, legal process, or enforceable governmental request
- protect the rights, safety, and security of users, operators, or the Service

## 7. Security

We use commercially reasonable measures to protect your information, including authenticated APIs, token-based auth flows, and controlled service integrations. However, no method of transmission over the internet or method of electronic storage is completely secure.

## 8. Children's Privacy

Our Service is not directed to children under the age of 13, and we do not knowingly collect personal information from children under 13.

## 9. Changes to This Privacy Policy

We may update this Privacy Policy from time to time. When we do, we will post the updated version in the repository or application materials and revise the "Last updated" date.

## 10. Open-source and Data Credits

This project uses open-source software and open geospatial data, including:

- OpenStreetMap / Geofabrik extracts
- Overture Maps place datasets
- MapLibre
- OSRM
- BRouter
- Spring Boot
- MongoDB
- Typesense
- Ollama

Applicable attribution and license terms from those projects and datasets remain in effect.

## 11. Contact Us

If you have questions or suggestions about this Privacy Policy, contact:

**Email**: schooleoinbox@gmail.com

**GitHub**: [https://github.com/Schooleo/BIF-Mobile-App](https://github.com/Schooleo/BIF-Mobile-App)
