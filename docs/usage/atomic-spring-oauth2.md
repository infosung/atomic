# atomic.spring.oauth2 Guide

## Why Use This Module

Use `atomic.spring.oauth2` when your server needs provider-based OAuth integration with unified models and service APIs.

It provides:

- Google/Kakao/Apple provider implementations
- authorization URL building
- code exchange/refresh/revoke (provider capability dependent)
- identity resolution from `id_token` or `userinfo`
- signed `state` management for redirect flow

## Scope of This Guide

- This module is not web-only.
- The redirect section in this guide is for browser-based web flow.
- Mobile/native can use provider SDK, while server uses this module for token/identity verification.

## Prerequisites

- Spring Boot app with component scan/config setup
- Tested with Java `25`, Kotlin `2.3.10`, Spring Boot `4.0.5`
- Provider console configured redirect URI per provider

## Core Concepts You Must Distinguish

- `serverRedirectUri`: provider console callback URI (must point to your server)
- `OauthAuthorizationRequest.redirectUri`: final client URI stored in signed state
- `OauthTokenExchangeRequest.state`: required callback state token

In short:

- provider `redirect_uri` parameter -> `serverRedirectUri`
- signed state claim `redirect_uri` -> final client redirect destination

## Multi-Platform Redirect Model

This module should be treated as a server-side OAuth foundation for:

- web
- iOS
- Android
- macOS
- Windows

The common model is:

- client opens provider login in browser or browser-based tab
- provider returns to your server callback
- your server verifies/exchanges and then hands control back to the client redirect URI you stored in signed state

For native/mobile/desktop flows, PKCE is usually required on the client side. `atomic.spring.oauth2` now exposes this explicitly through:

- `OauthAuthorizationRequest.codeChallenge`
- `OauthAuthorizationRequest.codeChallengeMethod`
- `OauthTokenExchangeRequest.codeVerifier`

If your host app or redirect relay layer generates/stores the verifier, pass those fields through instead of hiding them in provider-specific parameters.

Boundary note:

- `atomic.spring.oauth2` is the low-level seam that accepts caller-managed PKCE material.
- `atomic-app/oauth-redirect` uses a stricter server-managed PKCE model on its web API and does not accept client-supplied `codeVerifier`.
- choose `atomic.spring.oauth2` directly when your host app owns provider SDK/native orchestration or token exchange itself.
- choose `atomic-app/oauth-redirect` when you want Atomic to run the browser/server relay and hand the host only a `relayCode`.

## Normalized Identity Output

`OauthIdentityResult` is intentionally richer than just `userId`.

Prefer these fields in new code:

- `providerSubject`: normalized provider-side subject identifier
- `emailVerified`: provider-level email verification signal when available
- `selectedClientKey`: routed client key stamped by multi-client routing
- `normalizedProfileMetadata`: normalized profile extras that are useful across providers without forcing every host app to parse raw claim maps first

Compatibility note:

- `userId` still exists and defaults to the same value as `providerSubject` in the base model for compatibility.
- For account binding, uniqueness checks, and external identity references, prefer `providerSubject` over `userId`.
- In multi-client routing, prefer an explicit route key or audience whenever possible. The fallback path is deterministic, but it should not be treated as a business contract for account resolution.

## Quick Start (First Working Flow)

1. Register `OauthStateManager` with strong signing secret.
2. Register one provider bean first (usually Google).
3. Register `OauthServiceProvider`.
4. Implement `/oauth/redirect/{provider}` and `/oauth/callback/{provider}`.
5. Verify one end-to-end login in dev.

Property reference:
- Full property index (default / required condition / description): [Property Reference by Module](environment-variables.md) -> `atomic.oauth2`

## Required Components (Typical)

- provider bean (`GoogleOauthProvider`/`KakaoOauthProvider`/`AppleOauthProvider`)
- `OauthStateManager`
- `OauthServiceProvider`
- callback controller endpoints

## Feature-to-Bean Matrix

| Goal | Required | Optional |
|---|---|---|
| Browser redirect login | provider bean, `OauthStateManager`, callback controller | state store |
| ID token verification only | provider bean with id token path | userinfo path |
| Multi-provider routing | multiple providers + `OauthServiceProvider` | runtime `supports(...)` checks |
| Single-use state consume | `OauthStateStore` + state manager with store | separate read-only state manager (optional for clearer separation) |

## Minimal Working Bean Config (Google)

```kotlin
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.infosung.atomic.oauth.api.OauthProvider
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.provider.google.GoogleOauthProvider
import com.infosung.atomic.oauth.state.OauthStateManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class AtomicOauth2Config {
  @Bean
  fun restClient(): RestClient = RestClient.create()

  @Bean
  fun googleIdTokenVerifier(): GoogleIdTokenVerifier =
      GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance()).build()

  @Bean
  fun oauthStateManager(): OauthStateManager =
      OauthStateManager(
          signingSecret = "replace-with-at-least-32-bytes-secret",
          issuer = "my-service-oauth-state",
          ttlSeconds = 600,
      )

  @Bean
  fun googleOauthProvider(
      restClient: RestClient,
      googleIdTokenVerifier: GoogleIdTokenVerifier,
      oauthStateManager: OauthStateManager,
  ): GoogleOauthProvider =
      GoogleOauthProvider(
          clientId = "google-client-id",
          clientSecret = "google-client-secret",
          serverRedirectUri = "https://api.example.com/oauth/callback/google",
          client = restClient,
          googleIdTokenVerifier = googleIdTokenVerifier,
          stateManager = oauthStateManager,
      )

  @Bean
  fun oauthServiceProvider(providers: List<OauthProvider>): OauthServiceProvider =
      OauthServiceProvider(providers)
}
```

## Runtime Flow (Browser Redirect)

```text
[Web Browser]
      |
      | 1) GET /oauth/redirect/{provider}?redirectUri=https://web.example.com/login/callback
      v
[Your Server]
      |
      | 2) provider.buildAuthorizationUrl(redirectUri=final client URI)
      v
[OAuth Provider]
      |
      | 3) login/consent
      | 4) redirect -> {serverRedirectUri}?code=...&state=...
      v
[Your Server Callback]
      |
      | 5) provider.exchangeCode(code, state)  // state verify happens here
      | 6) decode verified state claim for final redirect target
      v
[Web Browser]
```

## Callback Controller Example

```kotlin
import com.infosung.atomic.oauth.api.OauthAuthorizationRequest
import com.infosung.atomic.oauth.api.OauthProviderName
import com.infosung.atomic.oauth.api.OauthServiceProvider
import com.infosung.atomic.oauth.api.OauthTokenExchangeRequest
import com.infosung.atomic.oauth.exception.InvalidOauthRequestException
import com.infosung.atomic.oauth.state.OauthStateManager
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@Controller
class OauthRedirectController(
    private val oauthServiceProvider: OauthServiceProvider,
    private val stateReader: OauthStateManager,
) {
  @GetMapping("/oauth/redirect/{provider}")
  fun redirect(
      @PathVariable provider: String,
      @RequestParam redirectUri: String,
  ): String {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw InvalidOauthRequestException("Unsupported provider: $provider")

    val authorizationUrl =
        oauthProvider.buildAuthorizationUrl(
            OauthAuthorizationRequest(redirectUri = redirectUri),
        )
    return "redirect:$authorizationUrl"
  }

  @GetMapping("/oauth/callback/{provider}")
  fun callback(
      @PathVariable provider: String,
      @RequestParam code: String,
      @RequestParam state: String,
  ): String {
    val oauthProvider =
        oauthServiceProvider.getService(provider)
            ?: throw InvalidOauthRequestException("Unsupported provider: $provider")

    // exchangeCode performs provider/state validation.
    val tokenResult = oauthProvider.exchangeCode(OauthTokenExchangeRequest(code = code, state = state))

    // Optional: use a read-only state manager (no store) for controller-side state reading.
    val providerName = OauthProviderName.valueOf(provider.uppercase())
    val stateClaims =
        stateReader.readStateClaims(signedState = state, expectedProvider = providerName)
    val clientRedirectUri =
        stateClaims.redirectUri
            ?: throw InvalidOauthRequestException("redirectUri is missing in state")

    // Store tokenResult server-side and issue one-time relay code.
    // Example: relayCodeStore.save(relayCode, tokenResult, ttl = 300s)
    val relayCode = "generated-relay-code"
    return "redirect:$clientRedirectUri?relayCode=$relayCode"
  }
}
```

Use relayCode in your login API to consume stored OAuth payload.
Avoid redirecting raw `id_token` or `access_token` via query parameters.

Compatibility note:

- `readState(...)` / `verifyState(...)` still return Spring Security `Jwt` for existing integrations.
- New code should prefer `readStateClaims(...)` / `verifyStateClaims(...)` to avoid raw claim-key coupling.
- `IdTokenParser.verifyIdToken(...)` still returns Spring Security `Jwt` for existing integrations.
- New provider code should prefer `IdTokenParser.verifyIdTokenClaims(...)` to read typed issuer/subject/audience/nonce before falling back to provider-specific claims.
- `OauthServiceProvider` now fails fast when duplicate `OauthProviderName` registrations are supplied, instead of silently shadowing one provider with another.
- `HttpIOException` / `HttpJwtVerifyException` still exist as named oauth failures, but they no longer inherit `HttpStatusException`.
- map these oauth failures to HTTP status only in your adapter/web layer; do not rely on foundation oauth exceptions to carry transport status directly.

Upgrade note:

- if you are upgrading from the previous line and you previously caught `HttpStatusException` to detect oauth provider I/O or JWT verification failures, migrate that code to catch the oauth exceptions directly.
- for this release line, also review [Release Migration Guide: `v0.0.3` -> `v0.0.4`](../migration/v0.0.3-to-v0.0.4.md).

## State Verification Patterns

### Pattern A: Simpler (No Store)

- Configure `OauthStateManager` without `OauthStateStore`.
- `exchangeCode` and `stateReader.readStateClaims` both validate signature/expiry/provider.
- Best when you need quick integration and can tolerate non-single-use state semantics.

### Pattern B: Single-Use State (Recommended for Production)

- Use one `OauthStateManager` with `OauthStateStore` in provider path.
- Use another read-only `OauthStateManager` (same secret/issuer/ttl, `store = null`) in controller for reading `redirect_uri` after exchange.
- `readStateClaims(...)` itself does not consume store entries, so dual-manager is optional but can make responsibility separation explicit.

## Provider Capability Notes

- Google: full browser/server flow, exchange, refresh, revoke, identity (`id_token` / `userinfo`), PKCE `S256` and `PLAIN`
- Kakao: exchange, refresh, identity (`id_token` / `userinfo`), PKCE `S256`, revoke unsupported
- Apple: authorization + `id_token` identity-centric flow; exchange/refresh/revoke unsupported in current implementation, PKCE not exposed in the current runtime capability set

Use `provider.supports(...)` if your service needs runtime capability checks.

Practical service guidance:

- Google: best fit when you need the broadest provider feature surface, including refresh/revoke and browser/native PKCE lanes.
- Kakao: similar to Google for login and identity, but do not assume revoke support.
- Apple: treat it as an authorization + `id_token` identity path. Do not assume token exchange, refresh, revoke, or PKCE support unless the runtime capability set changes in a future release.

## Operational Checklist

- Use signing secret length >= 32 bytes.
- Keep provider console redirect URI and `serverRedirectUri` exactly matched.
- Always require `state` in callback exchange path.
- Define scope policy per provider before production.
- Validate nonce/audience policy for ID token verification.
- Avoid duplicate consume paths (for example `consumeState(...)` and then `exchangeCode(...)`) for the same callback.

## Troubleshooting

- Callback fails with invalid state: check signing secret, issuer, ttl, and state transport.
- Redirect target wrong: confirm `redirectUri` is stored in state and mapped back correctly.
- State already used/expired: check one-time consume flow and ensure callback is processed once.
- Unsupported operation errors: check provider capability before calling refresh/revoke/exchange.

## Notes

- Validate provider registration settings (`clientId`, redirect URIs, secrets) in each provider console.
- Enforce project security policy for `nonce`, `aud`, and allowed scopes.
