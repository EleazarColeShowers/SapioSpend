# SapioSpend — User Guide

*Plan and track every budget.*

---

## What SapioSpend is for

Most money apps do one of two things: they track a monthly salary, or they help you plan
a one-off event. SapioSpend does both, because it treats them as the same shape.

A **budget** is any pot of money with a purpose — your October salary, a wedding, a
conference, a savings target, the cost of setting up a new flat. You give it a total, you
break that total into **categories**, and then you log **expenses** against it. From that
point the app can tell you, at any moment:

- what you planned to spend on each thing, and what you actually spent
- what has already left your account, and what you still owe
- who has promised you money and who has actually sent it
- whether, at the pace you're going, you'll finish under or over

Everything lives on your phone. There is no account to create, no sign-in, and your
figures are never uploaded anywhere. The app makes exactly one network request, and only
when you ask it to: fetching public exchange rates. It carries no identifiers and none of
your data.

### Who it's for

- **Salary earners** running a monthly budget who want the month to actually balance.
- **Event planners** juggling several clients, each with a total, vendors and deposits.
- **Anyone saving towards something** — a laptop, school fees, a move — who wants to see
  the target getting closer.

---

## The three ideas behind everything

| Word | What it means |
|---|---|
| **Budget** | The thing you create. A wedding, or a salary month. It has a **total**. |
| **Category** | One line of the breakdown, with a *planned* amount and an *actual* spend. |
| **Expense** | One thing you spent money on, logged against a budget and a category. |

That's it. Every screen in the app is a different view of those three.

---

## First run

The app opens on a four-screen tour: what a budget is, the ready-made breakdowns, how
logging works, and what you get back. It is skippable from the first screen and shown
once. **Settings → Help → Show the tutorial again** replays it any time.

## Getting around

Three tabs across the bottom:

- **Budgets** — your list of budgets, the overall overview, and search.
- **Insights** — charts and analysis across everything you're tracking.
- **Settings** — currency, exchange rates and notifications.

---

## 1. Creating your first budget

Tap **+** on the Budgets tab. You'll be walked through three steps.

### Step 1 — What are you planning?

Pick a type:

- **Personal** — monthly budgets, savings goals, anything for yourself
- **Birthday** — parties, dinners, birthday trips
- **Wedding** — traditional, white wedding, engagement
- **Social Gathering** — house parties, dinners, get-togethers
- **Corporate** — conferences, seminars, company events
- **Other** — anything that doesn't fit

The type matters because it decides which templates you're offered next. Picking
**Personal** also pre-selects the current month for you, since a personal budget without a
month is just a number that never resets.

### Step 2 — Pick a starting point

You get the handful of templates that fit your type, and each one is a ready-made
breakdown expressed in percentages — so the same template works for a ₦500,000 birthday
and a ₦20,000,000 wedding.

For example, **Monthly Budget** (Personal) splits your take-home pay like this:

| Category | Share |
|---|---|
| Rent & Utilities | 25% |
| Food & Groceries | 20% |
| Savings & Investments | 15% |
| Transport & Fuel | 12% |
| Family & Support | 8% |
| Personal & Leisure | 6% |
| Airtime & Data | 5% |
| Health & Insurance | 5% |
| Buffer | 4% |

Other templates include *Savings Goal*, *Moving / Setting Up a Home*, *School Expenses*,
*Buying a Laptop*, *Starting a Business*, *Traditional Wedding*, *White Wedding &
Reception*, *Engagement*, *Birthday Party / Dinner / Trip*, *House Party*, *Naming
Ceremony*, *Conference*, *Seminar*, *Team Building*, *Corporate Event* and *Burial
Ceremony*.

Templates also decide which way money moves. **Savings Goal** is the one that runs
backwards — see *Savings goals* below. Everything else is money going out, and you can
change a budget's direction later from **Edit budget**.

You have two other options on this step:

- **I'll write my own categories** — name your own lines and set an amount for each. The
  app shows you what's allocated and what's left over as you type.
- **No template** — start blank. You can still log expenses and track the total; you just
  won't have planned figures to measure against until you set categories later.

**The percentages are a starting point, not a rule.** You're expected to drag them around.
Their real job is to stop you facing a blank page.

### Step 3 — The details

- **Budget Name** — what you're planning for.
- **Total** — the money this budget has. It's called **Take-Home Pay** on the Monthly
  Budget template, where the total is a month's income being divided up, and **Target** on
  a savings goal, where it's the figure you're saving towards. If you built your own
  categories, there's a one-tap *"Use ₦X as the total"* shortcut.
- **Currency** — what this budget is planned and recorded in. Defaults to the one you read
  the app in; each budget can use its own (see §9).
- **Budget Period** — *No dates*, *This month*, *Next month*, or a *Custom* date range.
  Dates are optional, but without them there's no daily allowance and no days-left figure.
- **Guests (optional)** — a head count. Adding it unlocks cost-per-head on Insights.

Tap **Create Budget** and you're done.

---

## 2. Logging an expense

From a budget, tap **+**. Or, faster: use the home-screen widget (see below).

| Field | Notes |
|---|---|
| **Expense Title** | What you bought. e.g. "Catering service" |
| **Amount** | **The full cost, whether or not it's all paid yet.** |
| **Category** | The budget's own planned categories come first, so your spending lands against the plan. |
| **Vendor / paid to** | Optional. Searchable later — "what did I pay Chidi?" |
| **Date of expense** | Defaults to today. |
| **Payment** | *Paid*, *Deposit paid*, or *Unpaid*. New expenses default to **Paid**. |
| **Deposit paid** | Only for *Deposit paid* — how much has actually gone out so far. |
| **Payment due** | Optional due date for the balance. Drives the overdue warnings. |
| **Receipt** | Attach a photo. Stored inside the app; nothing else can read it. |
| **Notes** | Optional. Also searchable. |

### Why three payment states, not a paid/unpaid switch

Almost every vendor is booked with a deposit and settled later. An app that can only say
"paid" or "not paid" has to call a half-paid caterer one or the other, and then every
figure downstream is wrong. SapioSpend keeps three apart:

- **Committed** — what the budget has to answer for (the full amounts)
- **Paid** — what has actually left your account
- **Still to pay** — the bill still to come

**Overdue** is deliberately *not* a fourth state. It's a fact about the due date, and it
can be true of an unpaid *and* a part-paid line alike. An expense with no due date never
becomes overdue — nobody agreed to a deadline, so the app doesn't invent one.

---

## 3. Inside a budget

Open any budget and you get, top to bottom:

**Overview** — Total, Spent, Remaining. If anything is unsettled, a second row adds Paid,
Still to pay and Overdue, plus the date of your next payment.

**Categories (Planned vs Actual)** — every category with its planned amount, what's
actually been spent, and a bar. Overspent lines are called out ("Decoration is ₦40,000
over its planned amount"). Tap **Edit categories** to change the plan at any time: set
what you intend to spend on each, clear a row to remove that category, add rows as you go.
The app tells you what percentage of the total is assigned and what's still unallocated,
and warns you (but still lets you save) if your categories add up to more than the total.

**Funding** — for budgets where money comes *in*. Tap **Add Funding** to record a
contribution with a source and amount, and mark whether it's **Received** (cash in hand)
or **Promised — not yet in**. The two are kept apart everywhere, on purpose: a planner who
counts a pledge as funding and books a venue against it is exactly the person this feature
exists to protect. You get Received, Promised and **Cash in hand** (received minus what you've
actually paid out), plus a line telling you how much of the budget is still unfunded.

**Recurring** — a cost that comes round again. Set an amount, a category, how often
(*Every week*, *Every 2 weeks*, *Every month*) and the first charge date. The app creates
the expense on schedule until you stop it, or until the budget period ends. Stopping one
leaves the expenses it has already created on the budget.

**Expenses** — the full list, with search and filters (by category, by payment status).
Tap any expense to edit it, mark it paid, or delete it. You can also **move** an expense
to a different budget, which takes its amount off the old budget's total and onto the new
one.

---

## 4. Savings goals

A savings goal is the same arithmetic run backwards, and the app says so in its own
words rather than making you translate.

Start one from **Personal → Savings Goal**, or switch any budget over from **Edit budget
→ Money direction → Money coming in**.

What changes:

| On a normal budget | On a savings goal |
|---|---|
| Total | **Target** |
| Spent | **Saved** |
| Remaining | **Still to go** |
| Categories | **Sources** — where the money will come from |
| Expenses | **Contributions** |
| Add Expense | **Add Contribution** |
| Payment status, due dates, funding | *hidden — money arriving is not half-paid* |

So the categories are Monthly Contribution, Side Income, Bonus & Windfalls and the like,
and each time money goes towards the goal you log a contribution against one. **Still to
go** counts down as you get closer, and passing the target is a success rather than an
over-budget warning.

Savings goals are deliberately left out of the portfolio overview on the Budgets tab, out
of Insights' spending totals and out of the home-screen widget. Those all answer "how
much can I still spend", and the gap between a target and what you have put away is the
opposite of that — it is money still to be found. Each goal keeps its own card in the
list, reading in its own words.

Recurring still works, and is worth using here: *₦50,000 every month* is exactly how a
savings goal gets fed.

## 5. Search

The search bar on the Budgets tab searches **everything at once** — budget names, expense
titles, vendors, categories and notes, across every budget you have. That's the point: if
you're asking "what did I pay Chidi", you don't already know which budget to look in.

There's a second, narrower search inside each budget for when you do.

---

## 6. Insights

The Insights tab analyses every budget together.

- **Planned vs Actual** — where you're over and under, by category.
- **Where the money goes** / **Share of spend** — your biggest categories.
- **Spend over time** — a monthly trend. Tap a month to read its total.
- **By budget** — each budget's own figures side by side.
- **Pace** — *On track*, *Ahead*, or a projection: *"At this pace you finish ₦120,000 over
  the total."*
- **Per day** and **Safe per day** — your current burn rate against what you can still
  afford daily for the rest of the period.
- **Per guest** — cost per head, if you set a guest count.

---

## 7. Sharing and exporting

Open the export menu — from a single budget for that budget's report, or from the
Budgets tab for everything at once — and pick a format:

- **PDF** — a client-ready report.
- **Excel (.xlsx)** — take the numbers into your own spreadsheet.
- **CSV** — for anything else.

The file goes straight to the share sheet, so you can send it to WhatsApp, Gmail, Drive or
anywhere else in one step.

---

## 8. Notifications

Settings → Notifications. Nothing fires unless you switch it on, and the app asks for
permission at the moment you do — never before.

- **Budget alerts** — when a budget passes **80%** of its total, and again when it goes
  **over**. Two alerts, not five: the fastest way to make an alert worthless is to send
  four of them before the money is actually gone. If you delete the expense that pushed you
  over, the alert can fire again later when it's news again.
- **Period reminders** — before a budget period ends, and on its closing day. Choose the
  lead time: **on the day**, **1 day**, **3 days** or **7 days** before.
- **Spending check-in** — a nudge to log what you've spent. **Off**, **every day**, or
  **every Monday**. Off by default.
- **Send these at** — 7am, 9am, 12pm, 6pm or 9pm. Applies to all of the above.

---

## 9. Currency and exchange rates

Settings → Currency. Eight currencies: **Nigerian Naira (₦)**, **US Dollar ($)**, **British
Pound (£)**, **Euro (€)**, **Ghanaian Cedi (₵)**, **Kenyan Shilling (KSh)**, **South African
Rand (R)** and **Canadian Dollar (CA$)**.

### Every budget has its own currency

You pick it on step 3 of the create wizard, under the total, and it is what that budget is
planned and recorded in from then on. A naira salary month, a dollar laptop fund and a
pound savings goal can all sit on the same Home screen.

A budget's own screens always read in its own currency, exactly as you typed it. A $5,000
target is $5,000 next month whatever the naira has done — nothing on it is ever put
through an exchange rate. Where you're reading the rest of the app in something else, the
overview carries a quieter converted line underneath each figure, and the currency picker
shows the rate it used and the date that rate was taken.

The one place budgets are added together is the Home overview and the Insights portfolio.
Those pool budgets kept in different currencies, so they convert — and say so, in a line
under the totals, naming what they converted to.

### Changing a budget's currency

Edit budget → Currency. Because the figures are really stored in the budget's currency,
changing it rewrites them: the total, every entry, the plan, the funding and any recurring
rules are converted at today's rate, in one go. The app tells you what it is about to do,
quotes the rate, and waits for you to say yes. It is the only thing in SapioSpend that
rewrites what you recorded, and converting back later will not land on exactly the same
figures.

### The Settings currency

Settings → Currency is a separate thing from any one budget. It sets:

- what new budgets start in;
- what the app reads in — the currency the Home and Insights totals are added up in, and
  the one the converted lines under a budget's figures are written in.

Figures recorded before budgets had a currency of their own are in **what you record in**,
which is fixed from the moment you saved your first budget and never rewritten. Set it and
the display currency the same and there's no conversion at all.

The app ships with a rate table built in, so conversion works with no connection at all.
**Update exchange rates** refreshes it. If the rate service can't be reached, the app says
so and keeps using the rates it has rather than pretending.

---

## 10. The home-screen widget

Add **SapioSpend** to your home screen and you get the money left across all your budgets,
plus a **+ Log an expense** button.

The button is the whole point. Recording an expense normally costs a launch and four taps —
and the expenses that go unrecorded are exactly the small ones nobody is willing to spend
four taps on. Which is how a budget quietly stops matching reality.

---

## 11. Free and Pro

The plan structure is **Free** (3 budgets) and **Pro** (₦5,000/month) for unlimited
budgets, templates, insights and PDF/Excel export.

**In this version, every Pro feature is unlocked for everyone, at no cost.** Nothing is
behind a paywall today.

---

## Three ways people use it

### A salary month

Create a **Personal** budget → **Monthly Budget** template → enter your take-home pay →
period **This month**. The template splits your pay across rent, food, savings, transport
and the rest. Log spending as it happens (the widget makes this a two-tap job). Turn on
**budget alerts** so you hear about 80% before it's 100%, and a **daily check-in** if you
need the nudge. At month end, look at *Planned vs Actual* and adjust next month's
percentages to match how you actually live.

### A client's wedding

Create a **Wedding** budget → **White Wedding & Reception** template → enter the total and
the guest count → set the period to the date range you're working to. Log every vendor as
an expense with the **full** cost, mark deposits as **Deposit paid**, and set a **payment
due** date for each balance. Record what the couple has sent under **Funding**, keeping
*received* and *pledged* apart. Now the overview tells you the truth: what's committed,
what's actually gone out, what's still owed, what's overdue, and whether the cash in hand
covers it. Export a **PDF** to send them after each milestone.

### Saving for something

Create a **Personal** budget → **Savings Goal** template → set the target. The app
switches vocabulary: the breakdown becomes **Sources** (monthly contribution, side
income, bonuses, returns), the + button becomes **Add Contribution**, and the figures
read **Target / Saved / Still to go**. Payment status and funding disappear, because
money arriving is not half-paid.

Each time money goes towards the goal, log a contribution against the source it came
from. **Still to go** counts down. Set a recurring contribution for the monthly part and
it records itself.

---

## Common questions

**Do I need an account or internet?**
No. There's no sign-in, and the app works fully offline. The only request it ever makes is
an optional fetch for public exchange rates.

**Where is my data?**
On your phone, in the app's own storage. It isn't uploaded anywhere. Receipt photos are
copied into the app's private files, so deleting the photo from your gallery won't blank a
receipt attached to a ₦2m payment.

**Can I change a budget's total or period after creating it?**
Yes. Open the budget and tap the edit icon — name, total, period and type are all editable.

**What if my categories add up to more than the total?**
The app tells you, but still lets you save it. Sometimes that's the accurate picture, and
the app's job is to show it, not argue.

**What happens to an expense in a category I never planned for?**
Nothing breaks. The category appears in Planned vs Actual marked *"no planned amount"*, and
the categories editor groups them under **Already spent on, not planned** so you can give
them a planned figure in one place. Nothing disappears, and your planned-vs-actual
comparison stays honest.

**Can I move an expense between budgets?**
Yes, from the expense's own form. The amount comes off the old budget's total and goes onto
the new one.

**Will deleting a budget delete its expenses?**
Yes — the confirmation says so, and it can't be undone.

**Can I turn an ordinary budget into a savings goal, or back?**
Yes. **Edit budget → Money direction**. Nothing you have logged is lost; the same entries
are simply described in the other set of words.

**Why isn't my savings goal in the total on the Budgets tab?**
Because that card answers "how much can I still spend", and a savings target is money you
still have to find rather than money you have. Goals keep their own cards, and their own
figures, in the list below it.

**Can I see the tutorial again?**
**Settings → Help → Show the tutorial again.**
