# Frontend cart integration — 25 October 2026

The changes from `feature/frontend-finalization` commits `176b27c8a2c472afe331a1b776764c5e7f129785` and `95c3157e67db2eacf4a6fb1dd26dddea2fb22838` were integrated on top of migration/verification commit `7613bbd072e705b54d93a0bdd86ec644f2cbe68b`.

Both worktrees were clean before integration. The source commits change only three frontend files; none had been changed by the migration after their common base `a7dee2d`. Both commits applied without conflicts using `git cherry-pick --no-commit 176b27c 95c3157` on a temporary integration branch. They are recorded together in one integration commit after verification because the second commit fixes the first commit's Node test import from JSX.

## Preserved behaviour

- The bundle helper implementations are moved unchanged into `frontend/src/utils/cartCalculations.js`.
- `CartContext.jsx` imports and re-exports both helpers, preserving existing callers and all cart storage, quantities, shipping and tax behaviour.
- The three integrated frontend files exactly match the source worktree's final commit `95c3157`.
- Migration checkout idempotency handling in `frontend/src/pages/Cart.jsx` is unchanged.
- The separate worktree remains clean on `feature/frontend-finalization` at `95c3157`. Main remains at `228475944356dcb36241e902000d9187b357360d`.

## Verification before committing

| Check | Result |
|---|---|
| Frontend `npm test` | 25 passed; 0 failed/skipped |
| Frontend `npm run build` | Passed |
| Existing Order Gateway journey | Passed |
| Two-account group order / bill settlement | Passed |
| New frontend/Gateway total parity suite | Passed |
| Existing Payment Gateway journey | Passed |
| Combined relevant Gateway checks | 61 PASS lines; no failures |
| Six service discovery/health and internal security checks | Passed |
| Port 8087 | Stopped throughout HTTP verification |
| Conflict/whitespace checks | Passed |

The new `tools/tests/cart_totals_gateway.cjs` imports the production frontend helpers and compares them with real Gateway quotes for one/two/three/four distinct products (0/5/10/15% bundle discount), quantity changes, and both sides of the ₹999 free-shipping threshold. It also checks an existing coupon stacks before the bundle tier. No shared coupon is created, changed or redeemed by that quote-only check.

A four-product demo CARD checkout verifies the quoted total and bundle discount match the stored order and payment. Repeating its idempotency key returns the same order. Cancellation refunds the synthetic payment and restores all four product stocks. Only synthetic fixture data is written; its financial history is retained. The parity suite is now included in `node run_integration_tests.js order`.

Reproduce:

```powershell
cd frontend
npm test
npm run build
cd ..
node run_integration_tests.js order
node tools/tests/payment_gateway.cjs
```

## Existing issues outside this integration

The three previously verified migration regressions remain: account deletion retains profile/address data, unpaid COD returns can diverge between Order and Payment, and `/api/health` returns 404. The complete acceptance runner still reports these failures; this frontend integration does not resolve them. See `docs/VERIFICATION_REPORT.md`. Real email/SMS and Docker deployment remain unverified; online payments remain demo flows.

Local logs are retained under ignored `.local/frontend-integration/`. No credentials or tokens are included in the integration commit.
