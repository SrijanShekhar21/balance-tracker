# Balance Tracker

An Android app that reads the transaction SMS your bank already sends you, and turns them into
a spending report delivered every night.

Built for an SBI account you spend from using PhonePe, Google Pay and BHIM UPI. Every one of those
apps settles through your bank account, and SBI texts you about each settlement — so those
messages are a complete, free, real-time record of your money. This app reads them on the phone,
parses out amount, payee, reference number and balance, categorises the spend, and computes the
report. Nothing is sent anywhere.

---

## Part 1 — Build the APK (about 10 minutes, nothing to install)

You do not need Android Studio or the Android SDK. GitHub compiles the app for you, for free.

### 1. Make the hidden folder visible

This project contains a folder named `.github` that tells GitHub how to build the app. Windows
hides folders whose names start with a dot, and if it is left behind nothing will build.

In File Explorer, open the **View** menu → **Show** → tick **Hidden items**. You should now see
`.github` inside the `Daily Balance Tracker` folder.

### 2. Create a repository

1. Sign in at [github.com](https://github.com) (create a free account if needed).
2. Click **+** in the top right → **New repository**.
3. Name it `balance-tracker`, select **Private**, and click **Create repository**.

### 3. Upload the project

On the empty repository page, click **uploading an existing file**.

Open the `Daily Balance Tracker` folder, select everything inside it (`Ctrl+A`), and drag it into
the browser window. Wait for all files to finish uploading — confirm you can see `.github`,
`app`, `build.gradle.kts`, `gradle.properties` and `settings.gradle.kts` in the list.

Click **Commit changes**.

> If drag-and-drop drops the folder structure, install
> [GitHub Desktop](https://desktop.github.com), clone the empty repository, copy the project files
> into the cloned folder, then commit and push. That method always preserves the structure.

### 4. Let it build

Go to the **Actions** tab. A run named *Build APK* starts on its own. It takes roughly 3–5 minutes
the first time.

- **Green tick** — done.
- **Red cross** — open the run, click the failed step, and send me the error text.

### 5. Download the app

Open the finished run and scroll to **Artifacts** at the bottom. Download
**BalanceTracker-apk** — a `.zip` containing two files:

| File | Use |
|---|---|
| `BalanceTracker-<n>.apk` | The one to install. |
| `BalanceTracker-debug-<n>.apk` | Fallback, only if the first refuses to install. |

Unzip it, then get the `.apk` onto your phone — email it to yourself, put it in Google Drive, or
use a USB cable.

### 6. Install it

Tap the `.apk` on your phone. Android will warn that it came from an unknown source; allow
installation for whichever app you opened it from (Files, Chrome, Gmail), then tap **Install**.

This warning is expected. Google Play does not permit apps that read SMS unless they are your
phone's default messaging app, so a personal tool like this is always installed directly. That
restriction is about Play Store distribution, not about safety.

---

## Part 2 — First run

1. Open the app. It asks for **SMS** and **Notification** access. Both are required: SMS is the
   entire data source, notifications are how the report reaches you.
2. It immediately scans the last 120 days of your inbox and builds your history. Existing
   transactions are recognised and skipped, so you can rescan any time without creating duplicates.
3. Go to **Settings** and fill in:

| Setting | Why it matters |
|---|---|
| **Balance right now** | Only needed until an SBI message quotes your balance; from then on the real figure takes over. |
| **Monthly budget** | Drives the budget bar and the overspend warning. |
| **Low balance alert** | The amount below which you want to be told. |
| **Large payment alert** | Any single payment at or above this gets called out. |
| **Report time** | Defaults to 22:00. |

Then tap **Preview tonight's report now** to see exactly what will arrive each evening.

---

## What the report tells you

**Money**
Spent today, received today, net flow, and current balance — taken from the `Avl Bal` figure SBI
stamps on its alerts rather than estimated, whenever one is available.

**Context**
Today against your own 7-day and 30-day averages, as a percentage and a bar. Zero-spend days are
counted in those averages, so the number answers "what does a typical day cost me" rather than
"what does a typical spending day cost me".

**Where it went**
Every payment sorted into 16 categories using about 380 Indian merchant keywords — Zomato and
Swiggy to Food, Blinkit and DMart to Groceries, Uber and IRCTC to Transport, and so on. Each
category shown with its amount and share. See below for how payees that name nobody useful are
handled.

**The month**
Spent so far, projected month-end at the current pace, budget used, and how many no-spend days
you have managed.

**Runway**
How many days your balance lasts at your recent burn rate. This is usually the single most
useful line in the report.

**Red flags**, only when they apply:

- Balance under your floor, or under two weeks of runway
- A day costing more than twice your average
- A single payment over your large-payment threshold
- Budget exceeded, or on pace to be
- The same amount to the same payee twice within minutes — a probable double charge
- Payments between midnight and 5am
- One category spending more than three times its own norm
- A first-ever payment of ₹2,000 or more to a new payee

---

## Rapido, Blinkit and Zepto

These three are most of your spending, and each breaks naive SMS parsing in its own way.

### Quick commerce bills under a name you would not recognise

On a bank statement Zepto appears as **Kiranakart Technologies**, Blinkit as **Blink Commerce**,
Swiggy as **Bundl**, Zomato as **Eternal**, Ola as **ANI Technologies** and Rapido as **Roppen**.
Matching only the brand name would drop all of these into Uncategorised. Every legal entity name
is in the keyword list alongside its brand, so they land correctly either way.

### Rapido riders and personal QR codes

When you scan a rider's own UPI QR, the bank SMS names *the rider*, not Rapido. Every rider is a
different person, so no amount of learning payees will ever help — each one is seen once and never
again.

The app solves this with a second source of evidence you already have: **Rapido texts you a ride
OTP when the trip starts.** That message carries no money, but it dates the ride precisely. So the
app records "Rapido ride, 9:02am", and when a payment to an unrecognised person lands at 9:31am, it
attributes that payment to Rapido — category, reporting and all.

Three rules keep this honest:

- **It never overrides a payee it recognises.** Inference only runs when the classifier has already
  failed. A payment to Kiranakart is Groceries no matter what else happened that hour.
- **Marketing cannot invent a ride.** "Flat 50% off your next 3 rides" is discarded before it can
  become evidence, so a later unrelated payment can never be attributed to a ride that never
  happened.
- **It shows its reasoning.** Tap the transaction and it tells you the category came from a Rapido
  message rather than from the payee, so you can overrule it.

The time window reaches two hours back but only fifteen minutes forward. A ride is paid for when it
ends, so the OTP always precedes the debit; an order is paid for as it is placed. A payment four
hours after a ride is not attributed to it.

The same mechanism works for Uber, Ola, Namma Yatri, Blinkit, Zepto, Swiggy, Zomato, Dunzo and
BigBasket.

### Whatever is left

Anything still unplaced — a rider who sent no OTP, a shop with a personal QR — collects in a
**"needs a category"** banner on the Today screen. Open it and you get the full list with tick
boxes: tick the ones that belong together, choose a category once, and they all move. A month of
rider payments is one action, not twenty.

This matters more than it sounds. Unplaced spends silently understate every category in your
report, so the banner sits at the top of the screen rather than hidden in settings.

---

## Fixing a wrong category

Tap any transaction. Pick the right category, leave **Remember this payee** on, and save. Every
past and future payment to that payee moves with it. The app learns your corrections, so the
categorisation gets more accurate as you use it.

The same screen shows the original SMS, so you can always see exactly what the app read.

---

## Cash

Cash leaves no SMS trail — nothing can detect it. Use the **+** button to add cash spends by hand.
Everything else is automatic.

---

## Your data

The app declares **no internet permission at all**. That is visible in
`app/src/main/AndroidManifest.xml`: there is no `android.permission.INTERNET` line. Without it,
Android blocks every network call the app could attempt. Your transactions are physically unable
to leave the phone.

Storage is a local SQLite database in the app's private directory, removed when you uninstall.

---

## Troubleshooting

**Too much is landing in "needs a category".** That is the app being honest rather than guessing.
Sort them once with the tick boxes; if a payee recurs under a real name, turn on *Remember this
payee* and it will never ask again.

**A transaction is missing.** Settings → *Scan last 120 days*. If it still does not appear, the
message is in a format the parser does not recognise yet — send me the SMS text and I will add it.

**The balance looks wrong.** It reads "estimated" until an SBI message quotes a real balance, at
which point it self-corrects. If it stays wrong, set the correct figure under Settings → Balance.

**No report arrived.** Android battery optimisation can delay background work. Settings → Apps →
Balance Tracker → Battery → **Unrestricted**.

**Something was counted twice.** Tap it and delete it. Genuine duplicates from repeated SMS
delivery are already filtered by reference number.

---

## Changing the app later

Edit the file on GitHub (or push from GitHub Desktop) and the build runs again automatically —
download the new APK and install it over the old one. Your data is kept.

Useful places to edit:

| What | File |
|---|---|
| Merchant keywords and categories | `app/src/main/java/com/dbt/tracker/data/Categories.kt` |
| SMS formats understood | `app/src/main/java/com/dbt/tracker/sms/SmsParser.kt` |
| Ride/order apps used as evidence | `app/src/main/java/com/dbt/tracker/sms/SignalDetector.kt` |
| Metrics and red flags | `app/src/main/java/com/dbt/tracker/report/ReportEngine.kt` |
| Report wording | `app/src/main/java/com/dbt/tracker/report/Notifications.kt` |
