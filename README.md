# Hisaab

*hisaab* — accounts, the reckoning of what was spent.

An Android app that reads your downloaded bank statement and turns it into a spending report.

Everything stays on the phone. The app declares **no internet permission at all**, so your
statement cannot be uploaded anywhere, and it reads only the single file you hand it.

**Install:** https://github.com/SrijanShekhar21/balance-tracker/releases/latest/download/BalanceTracker.apk

That link always serves the newest build: every push republishes it, so the URL never changes.

**Installing over an existing copy now works.** Builds are signed with a fixed key held in
repository secrets, and the version number follows the build, which are the two things Android
requires before it will treat a new APK as an upgrade rather than a different app.

One exception: any copy installed before this change was signed with a throwaway key, so Android
will refuse to upgrade it. Uninstall once, install this build, and every future update installs
straight over the top.

---

## Using it

1. **SBI net banking → Account Statement → pick the date range → download.** CSV or Excel both
   work, and a password on the file is fine — the app asks for it once and remembers it.
2. In the app: **Settings → Import a statement**, and pick the file.
3. That is the whole loop. Re-download and re-import whenever you want the numbers refreshed.

**Re-importing is safe.** Importing a period *replaces* it rather than adding to it, so uploading
the current month again mid-month corrects and extends what is already there instead of
duplicating it. Cash you entered by hand is never touched by an import.

### Supported files

| Format | Works |
|---|---|
| `.csv` | Yes — best option |
| Tab-separated `.txt`, and SBI's `.xls` that is really text | Yes |
| Real `.xlsx` | Yes |
| Password-protected `.xlsx` | Yes — enter the password once and tick *Remember it* |
| Old `.xls`, or a protected file in that format | No — open it and re-save as CSV |
| `.pdf` | No — download the CSV or Excel version instead |

A protected workbook is decrypted in the app. It is not a zip but a Compound File Binary
container holding an AES-encrypted one, and since Android already provides AES and SHA-512 this
needs no library, where Apache POI would have added roughly 15 MB. The scheme carries its own
verifier hash, which is checked before anything is decrypted, so a wrong password is reported as
one rather than producing noise.

The importer does not hard-code SBI's layout. It finds the header row, maps columns by name, and
reads what follows — so it also works on statements from other banks.

---

## What the report shows

**Balance** — read straight from the statement's closing balance column where the statement has
one. SBI's does not, so the app asks for your balance once and works forward; that stays exact,
because a statement is complete for its period and has nothing missing to accumulate error.
Alongside it, runway: how many days the balance lasts at your recent burn rate.

**Cash flow** — spent, received, net, transaction count, largest single payment.

**Today vs your average** — the day against your own 7-day and 30-day averages. Zero-spend days
count toward those averages, so the figure answers "what does a typical day cost me" rather than
"what does a typical spending day cost me".

**Where it went** — 16 categories, matched on ~380 Indian merchant keywords including the legal
entity names that actually appear on statements: Zepto bills as *Kiranakart*, Blinkit as *Blink
Commerce*, Rapido as *Roppen*, Swiggy as *Bundl*, Zomato as *Eternal*.

**The month** — spent so far, projected month-end, budget used, no-spend days.

**Red flags**, only when they apply: balance under your floor · under two weeks of runway · a day
costing more than twice your average · a payment over your threshold · budget exceeded or on pace
to be · the same amount to the same payee twice within minutes · one category running at more
than three times its own norm · a first-ever payment of ₹2,000+ to a new payee.

---

## Payees the app cannot place

A statement names a UPI payee twice — once as a name, once as their handle:

```
TO TRANSFER-UPI/DR/522839472019/RAPIDO/YESB/rapido.qr@ybl/Payment
```

Both are searched, so a payment identifies itself even when only the handle is meaningful.

When neither says anything useful — a Rapido rider's personal QR code is the common case, since
every rider is a different stranger — the payment collects in a **"needs a category"** banner on
the report screen. Open it and you get the list with tick boxes: tick the ones that belong
together, choose a category once, and they all move. A month of rider payments is one action.

This matters because unplaced spending silently understates every category, which is why the
banner sits at the top of the screen rather than hidden in settings.

To correct a single payee, tap any transaction, pick the right category and leave *Remember this
payee* on. Every past and future payment to that payee follows.

---

## Cash

Cash never reaches a bank statement, so nothing can detect it. Use the **+** button to add cash
spending by hand. Imports never overwrite it.

---

## Troubleshooting

**"Could not find a header row."** The file has no recognisable Date / Debit / Credit columns.
Re-download as CSV rather than PDF or MT940.

**Rows were skipped.** The import panel says how many and why. Usually footer or total lines,
which is correct — but if the count is large, send me a few lines of the file with the amounts
changed and I will fix the parser.

**The balance looks wrong.** Settings → *Balance workings* shows the exact closing balance the app
read, its date, its account, and anything added since. If it's tracking the wrong account, switch
it there.

**No report arrived.** Android battery optimisation delays background work. Settings → Apps →
Hisaab → Battery → **Unrestricted**.

---

## The signing key

`signing/` holds the key that every build is signed with, and is deliberately untracked. Keep a
copy somewhere safe: without it, a future build cannot upgrade an installed app, and the only
remedy is uninstalling and losing the data. The same key is stored in the repository secrets
`ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_ALIAS`, which GitHub cannot
show you again once set.

---

## Changing it

Pushing to this repo rebuilds the APK automatically; download the new one and install over the
top, and your data is kept.

| What | File |
|---|---|
| Merchant keywords and categories | `app/src/main/java/com/dbt/tracker/data/Categories.kt` |
| Statement layouts understood | `app/src/main/java/com/dbt/tracker/statement/StatementParser.kt` |
| Payee extraction from narration | `app/src/main/java/com/dbt/tracker/statement/Narration.kt` |
| Metrics and red flags | `app/src/main/java/com/dbt/tracker/report/ReportEngine.kt` |
| Report wording | `app/src/main/java/com/dbt/tracker/report/Notifications.kt` |
