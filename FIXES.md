# Fixes applied

## Crash / logout causes
1. **`GET /api/chit-groups` (and cycles, bids) returned HTTP 500 once a group existed**  
   `ChitGroup.members` is a lazy collection and `spring.jpa.open-in-view=false`, so Jackson hit
   `LazyInitializationException` while writing the response. Fixed with `@JsonIgnore` on
   `ChitGroup.members` (group members are served by `/api/chit-groups/{id}/members`).
2. **Any business-rule error logged the user out**  
   Services throw plain `RuntimeException`s ("Bidding is not open", "Insufficient wallet balance", ...).
   They had no handler, became a 500, Spring forwarded to `/error`, `/error` required auth, so the browser
   received **401** and `app.js` cleared the session. `GlobalExceptionHandler` now handles them (400) and
   `/error` is permitted in `SecurityConfig`.
3. **Errors were sent as HTTP 200**  
   Handlers returned an `ErrorResponse` without a status, so the frontend treated failures (e.g. 403,
   404, validation) as success. All handlers now return the proper status (400/403/404/409/500).
4. **Registered users had no wallet**  
   `AuthService.register` created a `Member` but never a `Wallet`, so wallet page / recharge / withdraw
   failed with "Wallet not found". Wallet is now created at registration; `WalletService` also creates a
   missing wallet on first use so accounts registered earlier keep working.
5. **App failed to start with a badly formatted `JWT_SECRET`**  
   `JwtService` only accepted strict Base64 >= 32 bytes. It now accepts Base64, Base64URL or plain text
   (>= 32 bytes) and gives a clear message if too short. `render.yaml` now uses `generateValue: true`.
6. **One bad record stopped the schedulers**  
   An exception while processing one group/cycle (e.g. "No eligible members available") escaped the
   `@Scheduled` method and skipped everything after it. Both schedulers now catch and log per group/cycle.

## Follow-up fixes
7. **Wallet "null" popup** - the recharge/withdraw handlers called `e.currentTarget.reset()` after an `await`, when `currentTarget` is already null. The error aborted the wallet refresh. Fixed in `app.js`.
8. **Owner-only buttons shown to everyone** - the backend already enforces ownership; the UI now hides Add member / Add cycle for non-owners and shows the owner name.

9. **Cycle lifecycle reworked (owner driven)**
   - Owner creates a cycle (group must be full; cycles are created one at a time, in order, after the previous one is paid out). Group becomes **UPCOMING**.
   - When the bidding **start time** arrives the cycle opens and the group becomes **ACTIVE** (checked every 10 seconds). Members can bid.
   - While bidding is open a member can **edit their bid** (the lowest bid wins; ties go to whoever reached that amount first).
   - When the **end time** arrives the winner is decided automatically (random eligible member if nobody bid). No money moves yet.
   - The owner presses **Distribute payout** (`POST /api/cycles/{id}/distribute-payout`): winner is paid, dividends go to the other members, the organiser commission is credited to the owner. Requires every member to have paid their contribution. All-or-nothing in one transaction. After the last cycle the group becomes **COMPLETED**.
   - The old automatic first cycle / start-date activation / automatic payout were removed.
10. **Other money/logic fixes**
   - A member who won once (even by random draw) cannot win again.
   - Organiser commission was calculated but never paid; now credited to the owner (shown as PAYOUT in the ledger).
   - Chit value must equal monthly contribution x maximum members.
   - JVM timezone set to Asia/Kolkata in the Dockerfile (browser times were being read as UTC).
   - UI: bid dialog is pre-filled for editing, cycles show bidding window / winner / payout status, owner sees "Distribute payout", open group refreshes itself every 10 seconds.

11. **"Payment not found" when paying** - payment rows only existed for cycles created after the last update (or for members who joined later). Paying now creates any missing row first; the payment summary no longer depends on those rows.

12. **Membership and roles**
   - Members can only be added/removed while the group is **DRAFT**; once the owner schedules the first cycle (UPCOMING) membership is locked.
   - The **owner is not a participant**: the owner cannot be added as a member and cannot bid or pay contributions (enforced in the backend). Pay and Place/edit bid buttons are shown only to members; the owner sees Add member (DRAFT only), Remove (DRAFT only), + Cycle and Distribute payout.

## Not changed (worth knowing)
- Redis is configured but nothing uses it (`@Cacheable` is never applied); Render has no Redis in the blueprint.
- `DividendService` and `PayoutService` are unused duplicates of `CycleSettlementService`.
- The group's "start date" field is now informational only; the owner's cycle controls when the group starts.
- `ClearChitApplicationTests` needs a running PostgreSQL/Redis (the Dockerfile uses `-DskipTests`).
