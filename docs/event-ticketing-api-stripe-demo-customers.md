# Demo Accounts and Their Stripe Customers

The 12 demo accounts from `src/main/resources/db/seed/demo-data.sql` and the Stripe **test mode** customer set up for each one.
**Find an account by pressing Ctrl+F and typing its email.** Everything below the lookup table is the how-to and the details.

## Look up an account by email

All emails end in `@demo.test`. Passwords are not repeated here: they are in the comments next to each user in `demo-data.sql`.

| Email | Name | Role in the app | Stripe customer id | Card on file | Test card number | Test CVC | Balance |
|---|---|---|---|---|---| --- |---|
| `superadmin@demo.test` | Sasha Valdez | Super admin (the only one) | none, not a buyer | none | none | none | none |
| `admin1@demo.test` | Avery Santos | Admin | none, not a buyer | none | none | `123` | none |
| `admin2@demo.test` | Morgan Reyes | Admin | none, not a buyer | none | none | `123` | none |
| `customer1@demo.test` | Jamie Cruz | Customer | `cus_VRHDvdxVIEyfGx` | Visa, exp 10/2027 | `4242 4242 4242 4242` | `123` | 0 |
| `customer2@demo.test` | Taylor Lim | Customer | `cus_VRHDgaP9I9S8qw` | **none yet** (planned: Visa that fails when charged) | planned `4000 0000 0000 0341` | `123` | 0 |
| `customer3@demo.test` | Riley Park | Customer | `cus_VRHDtLnphrXJ9O` | **none yet** (planned: Visa that fails when charged) | planned `4000 0000 0000 0341` | `123` | 0 |
| `customer4@demo.test` | Casey Tan | Customer | `cus_VRHD6LrOKvhJKS` | Visa (asks for 3-D Secure), exp 10/2027 | `4000 0000 0000 3220` | `123` | 0 |
| `customer5@demo.test` | Jordan Dela Cruz | Customer | `cus_VRHD0DqdB0NAq4` | Mastercard, exp 10/2027 | `5555 5555 5555 4444` | `123` | **5,000,000 USD** ⚠ |
| `owner@demo.test` | Owen Dela Mercado | Owner of *Demo Events Co.* | `cus_VRHDEd7YdCM3eU` | Visa, exp 10/2027 | `4242 4242 4242 4242` | `123` | 0 |
| `owner2@demo.test` | Rowan Castillo | Owner of *Demo Productions* | `cus_VRHDnoukJdU96i` | Visa, exp 10/2027 | `4242 4242 4242 4242` | `123` | 0 |
| `organizer@demo.test` | Paula Ramos | Organizer of *Demo Events Co.* | `cus_VRHDJbTdIaPAzS` | Visa, exp 10/2027 | `4242 4242 4242 4242` | `123` | 0 |
| `scanner@demo.test` | Sam Villanueva | Check-in staff of *Demo Events Co.* | `cus_VRHDySLWhrZoSE` | Visa, exp 10/2027 | `4242 4242 4242 4242` | `123` | **5,000,000 USD** ⚠ |

How to read it:

- **Test card number** is one of Stripe's own published *test* numbers. It is not a real card, it only works in the Stripe
  sandbox, and it is the same number for every account that shares a card type. To type a card by hand in a Stripe test form
  use that number with **any future expiry and any 3-digit CVC** (for example `12 / 34` and `123`) and any postal code. Never
  put a real card number anywhere in this project, and do not send these numbers to this API: the API only takes the Stripe
  token and the last 4 digits (see [Card details](#card-details)).
- The cards on the customers were attached through Stripe's test payment methods, so the expiry shown (10 / 2027) is what
  Stripe generated, not something you type.

- **Stripe customer id** is the `cus_…` id in the Stripe sandbox `event-ticketing-application sandbox` (test mode, nothing real).
  Paste it into the Stripe Dashboard search to open the customer.
- Every Stripe customer is set to country **PH**, locale `en-PH`, currency label **PHP** and time zone **Asia/Manila**.
  The card's billing country is also **PH**; the card's own "origin" stays United States because Stripe fixes it (see [Card details](#card-details)).
- The three admin accounts are not buyers, so they have no Stripe customer. The other nine do.
- Phone numbers, and how each name is sent to Stripe, are in [Which demo accounts get a customer](#which-demo-accounts-get-a-customer).

### Needs attention

- **No card yet:** `customer2@demo.test` and `customer3@demo.test`. Give them `pm_card_chargeCustomerFail` (attaches, then fails when charged); see [section 3](#3-give-each-customer-a-test-card).
- **⚠ Balance:** `customer5@demo.test` and `scanner@demo.test` show 5,000,000 in **USD**, which is $50,000 *owed*, not ₱50,000 credit. See [Status](#what-exists-in-the-sandbox).
- **Not possible:** a card issued in the Philippines. Stripe has no PH test card.

## Setting up Stripe for the demo accounts

The rest of this document is the runbook (how the customers were made and how to redo it) and the reference details.

## Status: nothing in the API calls Stripe yet

Checkout goes through `PaymentGatewayClient`, and the only implementation is `MockPaymentGatewayClient` (a token starting
with `tok_fail` is declined, anything else succeeds). Nothing in the code reads a Stripe key or stores a Stripe id. This
document is a runbook for preparing the Stripe side; it changes no code or data here. See [What the API still needs](#what-the-api-still-needs).

## Ground rules

- **Test mode only.** Use the secret key that starts with `sk_test_` (Stripe Dashboard → Developers → API keys, with the
  *Test mode* switch on). Never use a `sk_live_` key with demo data.
- **Never put the key in a file in this repo, a commit, a chat or a screenshot.** Keep it in an environment variable
  (`STRIPE_SECRET_KEY`) or a secrets manager. Replace the placeholders below with your own value.
- **Never send a full card number or CVC through this API or the database.** Cards are only ever entered in Stripe's own
  fields (Stripe.js / Elements) or, for these demo accounts, attached with Stripe's built-in test payment methods
  (`pm_card_visa` and friends). This is the same rule as BR-PAY-001 and BR-PAY-007.
- The demo addresses end in `@demo.test`. In test mode Stripe sends no emails to customers. If Stripe ever rejects one of
  these addresses as invalid, keep the demo address in `metadata[demo_email]` and leave `email` out.

## Which demo accounts get a customer

A Stripe Customer is a buyer. The admin accounts do not buy tickets in the demo, so they are skipped. The organization
members have the `CUSTOMER` account role and can buy tickets too, so they are included.

| Create? | Email | Name sent to Stripe | Phone | Demo role |
|:-:|---|---|---|---|
| ✅ | `customer1@demo.test` | Jamie Cruz | +639171000011 | customer |
| ✅ | `customer2@demo.test` | Taylor Lim | +639171000012 | customer |
| ✅ | `customer3@demo.test` | Riley Park | +639171000013 | customer |
| ✅ | `customer4@demo.test` | Casey Tan | +639171000014 | customer |
| ✅ | `customer5@demo.test` | Jordan Dela Cruz | +639171000015 | customer |
| ✅ | `owner@demo.test` | Owen Dela Mercado | +639171000021 | owner of Demo Events Co. |
| ✅ | `owner2@demo.test` | Rowan Castillo | +639171000024 | owner of Demo Productions |
| ✅ | `organizer@demo.test` | Paula Ramos | +639171000022 | organizer of Demo Events Co. |
| ✅ | `scanner@demo.test` | Sam Villanueva | +639171000023 | check-in staff of Demo Events Co. |
| ❌ | `superadmin@demo.test` | | | super admin |
| ❌ | `admin1@demo.test` | | | admin |
| ❌ | `admin2@demo.test` | | | admin |

The name is `firstName [middleName] lastName` joined with spaces (Stripe has one `name` field; the database stores the
three parts separately).

## Field mapping

| Stripe Customer field | Comes from | Notes |
|---|---|---|
| `email` | `users.email` | Used to find the customer again. |
| `name` | first + middle + last name | See above. |
| `phone` | `users.phone_number` | Already in `+63…` international form. |
| `description` | fixed text | `Demo account (<role>)`. |
| `metadata[demo]` | `true` | Lets you find and delete every demo customer in one go. |
| `metadata[role]` | demo role from the table | |
| `address[country]` | fixed `PH` | Every demo account is in the Philippines. |
| `preferred_locales` | fixed `en-PH` | |
| `metadata[country]`, `metadata[currency]`, `metadata[timezone]` | fixed `PH`, `PHP`, `Asia/Manila` | Stripe has no customer field for time zone, and a customer's currency is set by Stripe on their first charge or subscription, so both are kept in metadata. Charge demo orders in `php`. |
| `metadata[user_id]` | `users.id` | The ids are generated when the seed runs, so read them from the database (below). |

Birth date is **not** sent: Stripe Customers have no such field and the payment does not need it.

Get the user ids for `metadata[user_id]`:

```sql
SELECT id, email FROM users WHERE email LIKE '%@demo.test' ORDER BY email;
```

## What exists in the sandbox

Created on 2026-10-14 in the Stripe sandbox `event-ticketing-application sandbox` (`acct_1UQMPuDoFhF7zry3`, test mode). Every
customer has country `PH`, locale `en-PH` and `metadata[currency]=PHP`, `metadata[timezone]=Asia/Manila`.
`metadata[user_id]` is the account's id in the dev database at that time (it changes if the seed is rebuilt).

| Demo account | Account name | Stripe customer id | Card on the customer (default) | Test card number | Test CVC | Balance |
|---|---|---|---|---| --- |---|
| `customer1@demo.test` | Jamie Cruz | `cus_VRHDvdxVIEyfGx` | Visa 4242, exp 10/2027 (`pm_1UQPGsDoFhF7zry3fVyzpkkP`) | `4242 4242 4242 4242` | `123` | 0 |
| `customer2@demo.test` | Taylor Lim | `cus_VRHDgaP9I9S8qw` | **none** (the declined test card cannot be attached) | planned `4000 0000 0000 0341` | `123` | 0 |
| `customer3@demo.test` | Riley Park | `cus_VRHDtLnphrXJ9O` | **none** (the declined test card cannot be attached) | planned `4000 0000 0000 0341` | `123` | 0 |
| `customer4@demo.test` | Casey Tan | `cus_VRHD6LrOKvhJKS` | Visa 3220 (3-D Secure), exp 10/2027 (`pm_1UQPGuDoFhF7zry3jEENJByI`) | `4000 0000 0000 3220` | `123` | 0 |
| `customer5@demo.test` | Jordan Dela Cruz | `cus_VRHD0DqdB0NAq4` | Mastercard 4444, exp 10/2027 (`pm_1UQPGvDoFhF7zry3sMV6V4YL`) | `5555 5555 5555 4444` | `123` | **5,000,000 USD** (see below) |
| `owner@demo.test` | Owen Dela Mercado | `cus_VRHDEd7YdCM3eU` | Visa 4242, exp 10/2027 (`pm_1UQPGwDoFhF7zry3F2wBwNx5`) | `4242 4242 4242 4242` | `123` | 0 |
| `owner2@demo.test` | Rowan Castillo | `cus_VRHDnoukJdU96i` | Visa 4242, exp 10/2027 (`pm_1UQPGxDoFhF7zry3Yexvlyyl`) | `4242 4242 4242 4242` | `123` | 0 |
| `organizer@demo.test` | Paula Ramos | `cus_VRHDJbTdIaPAzS` | Visa 4242, exp 10/2027 (`pm_1UQPGyDoFhF7zry3jPhXLHUC`) | `4242 4242 4242 4242` | `123` | 0 |
| `scanner@demo.test` | Sam Villanueva | `cus_VRHDySLWhrZoSE` | Visa 4242, exp 10/2027 (`pm_1UQPGzDoFhF7zry3kSju65kq`) | `4242 4242 4242 4242` | `123` | **5,000,000 USD** (see below) |

The *Test card number* column holds Stripe's published sandbox test numbers (not real cards). If you type a card by hand, use its number, any future expiry and a CVC: the *Test CVC* column shows `123`, but Stripe's test cards accept **any 3 digits** (4 for American Express). The CVC is never stored on the customer or in this API; it is only typed into Stripe's own form.
State read back from Stripe on 2026-10-14. The cards were attached with the script in [section 3](#3-give-each-customer-a-test-card).
All 7 attached cards also carry billing details set with the PowerShell script below: name, email and phone of the customer and
billing country `PH` (verified by reading them back, and visible in the Dashboard under *Payment methods*).

### Card details

What Stripe reports for each test payment method (read from the sandbox on 2026-10-14). The card number is Stripe's published
test number. Only these public test numbers appear in this document; a real card number or any CVC must never be stored in
this project.

| Test payment method | Brand | Last 4 | Expiry | Type | Issuing country | Used by |
|---|---|---|---|---|---|---|
| `pm_card_visa` | visa | 4242 | 10 / 2027 | credit | US | customer1, owner, owner2, organizer, scanner (attached) |
| `pm_card_chargeCustomerFail` | visa | 0341 | 10 / 2027 | credit | US | planned for customer2 and customer3: attaches, then fails when charged |
| `pm_card_visa_chargeDeclined` | visa | 0002 | 10 / 2027 | credit | US | **cannot be attached** to a customer (issuer-decline cards are refused); use it only un-attached at charge time |
| `pm_card_visa_chargeDeclinedInsufficientFunds` | visa | 9995 | 10 / 2027 | credit | US | **cannot be attached** either; use it un-attached at charge time to test insufficient funds |
| `pm_card_threeDSecure2Required` | visa | 3220 | 10 / 2027 | credit | IE | customer4 (attached) |
| `pm_card_mastercard` | mastercard | 4444 | 10 / 2027 | credit | US | customer5 (attached) |

- **Expiry moves.** Stripe creates a fresh payment method every time one of these `pm_card_*` names is used, with the
  expiry set to the current month one year ahead. 10 / 2027 is what it gave on 2026-10-14; a card attached next month will
  show next month of 2027. Read the real value after attaching (command in [section 4](#4-look-up-or-clean-up)).
- **Issuing country cannot be changed to PH.** A card's country comes from its card number and is read-only on the payment
  method. Stripe has no Philippines test card (its international list covers AU, CN, HK, IN, JP, MY, NZ, SG, TW, TH and others),
  so these cards stay US (3220 is IE). What can be set to PH is the *billing* country, which is done on all 7 cards; see [Billing details in PowerShell](#set-the-billing-country-and-holder-details-powershell).
  This API does not store an issuing country on payout cards, so nothing depends on it.
- **Holder name.** The test payment methods carry no name. Use the customer's name from the table above wherever a holder
  name is needed.
- **Each attach creates a new `pm_…` id.** The `pm_card_*` names are only shortcuts; the id you store is the one the attach returns.
- **Mapping to this API's payout cards** (`POST /api/v1/organizations/{orgId}/payout-cards`): `brand` = the brand, `last4` = the
  last 4, `expMonth` / `expYear` = the expiry, `holderName` = the customer's name, `paymentToken` = the attached `pm_…` id.
  A card that has already expired is refused by that endpoint.

Status (checked in Stripe on 2026-10-14):

- **Customers: created and set to PH / PHP / Asia/Manila.** Nine customers, nothing else in the sandbox.
- **Card billing details: set and verified on all 7 cards** (country `PH`, holder name, email, phone).
  The Dashboard's **Origin** and country flag on each card are the card's own `country`: they still read **United States** (Casey
  Tan's 3-D Secure card: Ireland) and cannot be changed, see [Card details](#card-details). Billing country `PH` is the PH
  value Stripe lets you set.
- **Cards: attached to 7 of 9.** customer2 and customer3 have no card because Stripe refuses to attach the issuer-decline test
  cards (`pm_card_visa_chargeDeclined`, `pm_card_visa_chargeDeclinedInsufficientFunds`). Attach `pm_card_chargeCustomerFail`
  (card 4000 0000 0000 0341) to them instead: it attaches fine and fails when charged. Insufficient funds cannot be attached at
  all; test it by passing `pm_card_visa_chargeDeclinedInsufficientFunds` un-attached at charge time.
- **Balance: set on two customers only, and not as intended.** customer5 and scanner show `balance: 5000000` with currency
  **USD**. Two problems: it is USD, not PHP (a customer's currency cannot be changed once set), and a **positive** balance is
  an amount the customer *owes* on their next invoice, not credit (credit is negative). The other seven are at 0. Section 5 shows the
  correct way to add PHP credit. Decide whether to keep these two or replace the customers.
- The unused temporary customer from the balance test is no longer in the sandbox (deleted).

## 1. Create one customer

```bash
export STRIPE_SECRET_KEY=sk_test_REPLACE_WITH_YOUR_TEST_KEY

curl -sS https://api.stripe.com/v1/customers \
  -u "$STRIPE_SECRET_KEY:" \
  -H "Idempotency-Key: demo-customer-customer1@demo.test" \
  -d email="customer1@demo.test" \
  -d name="Jamie Cruz" \
  -d phone="+639171000011" \
  -d description="Demo account (customer)" \
  -d "address[country]=PH" \
  -d "preferred_locales[]=en-PH" \
  -d "metadata[demo]=true" \
  -d "metadata[role]=customer" \
  -d "metadata[country]=PH" \
  -d "metadata[currency]=PHP" \
  -d "metadata[timezone]=Asia/Manila"
```

The response contains `"id": "cus_…"`. That is the Stripe customer id for this account.

`Idempotency-Key` makes a retried request return the same customer instead of creating a second one, but Stripe only
remembers a key for 24 hours. To re-run the script on another day, look the customer up first (next section).

## 2. Create all of them (skipping ones that already exist)

```bash
export STRIPE_SECRET_KEY=sk_test_REPLACE_WITH_YOUR_TEST_KEY

while IFS='|' read -r email name phone role; do
  found=$(curl -sS -G https://api.stripe.com/v1/customers -u "$STRIPE_SECRET_KEY:" \
            -d email="$email" -d limit=1 | grep -c '"id": "cus_')
  if [ "$found" -gt 0 ]; then
    echo "exists   $email"
    continue
  fi
  curl -sS https://api.stripe.com/v1/customers -u "$STRIPE_SECRET_KEY:" \
    -H "Idempotency-Key: demo-customer-$email" \
    -d email="$email" -d name="$name" -d phone="$phone" \
    -d description="Demo account ($role)" \
    -d "address[country]=PH" -d "preferred_locales[]=en-PH" \
    -d "metadata[demo]=true" -d "metadata[role]=$role" \
    -d "metadata[country]=PH" -d "metadata[currency]=PHP" -d "metadata[timezone]=Asia/Manila" | grep -E '"(id|email)"'
done <<'EOF'
customer1@demo.test|Jamie Cruz|+639171000011|customer
customer2@demo.test|Taylor Lim|+639171000012|customer
customer3@demo.test|Riley Park|+639171000013|customer
customer4@demo.test|Casey Tan|+639171000014|customer
customer5@demo.test|Jordan Dela Cruz|+639171000015|customer
owner@demo.test|Owen Dela Mercado|+639171000021|owner
owner2@demo.test|Rowan Castillo|+639171000024|owner
organizer@demo.test|Paula Ramos|+639171000022|organizer
scanner@demo.test|Sam Villanueva|+639171000023|check-in staff
EOF
```

Run it from Git Bash (the same shell the project's other scripts use). Add `-d "metadata[user_id]=<uuid>"` per line
if you want the database id on the Stripe side too.

## 3. Give each customer a test card

Stripe provides ready-made test payment methods, so no card number is typed anywhere. Attach one to a customer, then make
it their default:

```bash
CUS=cus_REPLACE_WITH_THE_CUSTOMER_ID

# attach (pm_card_visa is Stripe's built-in test payment method for 4242 4242 4242 4242)
PM=$(curl -sS https://api.stripe.com/v1/payment_methods/pm_card_visa/attach \
       -u "$STRIPE_SECRET_KEY:" -d customer="$CUS" | grep -m1 '"id": "pm_' | cut -d'"' -f4)

# make it the default for charges
curl -sS https://api.stripe.com/v1/customers/$CUS -u "$STRIPE_SECRET_KEY:" \
  -d "invoice_settings[default_payment_method]=$PM"
```

Different demo buyers can get different cards, so one run of the demo shows success, a decline and 3-D Secure:

| Demo account | Test payment method | Card behind it | Behaviour |
|---|---|---|---|
| `customer1@demo.test` | `pm_card_visa` | 4242 4242 4242 4242 | Always succeeds |
| `customer2@demo.test` | `pm_card_chargeCustomerFail` | 4000 0000 0000 0341 | Attaches, then fails when charged |
| `customer3@demo.test` | `pm_card_chargeCustomerFail` | 4000 0000 0000 0341 | Attaches, then fails when charged |
| `customer4@demo.test` | `pm_card_threeDSecure2Required` | 4000 0000 0000 3220 | Always asks for 3-D Secure authentication |
| `customer5@demo.test` | `pm_card_mastercard` | 5555 5555 5555 4444 | Always succeeds |
| owner, owner2, organizer, scanner | `pm_card_visa` | 4242 4242 4242 4242 | Always succeeds |

For any hand-typed test card in Stripe's own form, use any future expiry, any 3-digit CVC and any postal code. Stripe's
current list of test cards and `pm_card_*` names is at <https://docs.stripe.com/testing>; check it if a name above is
rejected, since Stripe can add or rename them.

### Same thing in PowerShell

`curl` is an alias for a different command in Windows PowerShell, so use `Invoke-RestMethod`:

```powershell
$env:STRIPE_SECRET_KEY = "sk_test_REPLACE_WITH_YOUR_TEST_KEY"
$auth = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($env:STRIPE_SECRET_KEY + ":")) }

$cards = @(
  @{ cus = "cus_VRHDvdxVIEyfGx"; pm = "pm_card_visa";                                 email = "customer1@demo.test" },
  @{ cus = "cus_VRHDgaP9I9S8qw"; pm = "pm_card_chargeCustomerFail";                   email = "customer2@demo.test" },
  @{ cus = "cus_VRHDtLnphrXJ9O"; pm = "pm_card_chargeCustomerFail";                   email = "customer3@demo.test" },
  @{ cus = "cus_VRHD6LrOKvhJKS"; pm = "pm_card_threeDSecure2Required";                email = "customer4@demo.test" },
  @{ cus = "cus_VRHD0DqdB0NAq4"; pm = "pm_card_mastercard";                           email = "customer5@demo.test" },
  @{ cus = "cus_VRHDEd7YdCM3eU"; pm = "pm_card_visa";                                 email = "owner@demo.test" },
  @{ cus = "cus_VRHDnoukJdU96i"; pm = "pm_card_visa";                                 email = "owner2@demo.test" },
  @{ cus = "cus_VRHDJbTdIaPAzS"; pm = "pm_card_visa";                                 email = "organizer@demo.test" },
  @{ cus = "cus_VRHDySLWhrZoSE"; pm = "pm_card_visa";                                 email = "scanner@demo.test" }
)

foreach ($c in $cards) {
  try {
    $attached = Invoke-RestMethod -Method Post -Headers $auth `
      -Uri "https://api.stripe.com/v1/payment_methods/$($c.pm)/attach" `
      -Body @{ customer = $c.cus }
    Invoke-RestMethod -Method Post -Headers $auth `
      -Uri "https://api.stripe.com/v1/customers/$($c.cus)" `
      -Body @{ "invoice_settings[default_payment_method]" = $attached.id } | Out-Null
    "OK      $($c.email) -> $($attached.id) ($($c.pm))"
  } catch {
    "FAILED  $($c.email) ($($c.pm)): $($_.ErrorDetails.Message)"
  }
}
```

Customers 1, 4, 5 and the four org members already have their card, so to finish the job remove those lines and run only the
customer2 and customer3 lines. Running the full list again attaches a second card to every customer. If a declined or 3-D Secure card fails to
attach, use `pm_card_visa` for that customer. The customer ids above belong to this sandbox only.

### Set the billing country and holder details (PowerShell)

Sets `billing_details` (name, email, phone, country `PH`) on the 7 cards attached on 2026-10-14 (already run and verified on 2026-10-14). Card number, expiry and issuing
country are not touched. Use the `pm_…` ids from [What exists in the sandbox](#what-exists-in-the-sandbox).

```powershell
$env:STRIPE_SECRET_KEY = "sk_test_REPLACE_WITH_YOUR_TEST_KEY"
$auth = @{ Authorization = "Basic " + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($env:STRIPE_SECRET_KEY + ":")) }

$cards = @(
  @{ pm = "pm_1UQPGsDoFhF7zry3fVyzpkkP"; name = "Jamie Cruz";         email = "customer1@demo.test"; phone = "+639171000011" },
  @{ pm = "pm_1UQPGuDoFhF7zry3jEENJByI"; name = "Casey Tan";          email = "customer4@demo.test"; phone = "+639171000014" },
  @{ pm = "pm_1UQPGvDoFhF7zry3sMV6V4YL"; name = "Jordan Dela Cruz";   email = "customer5@demo.test"; phone = "+639171000015" },
  @{ pm = "pm_1UQPGwDoFhF7zry3F2wBwNx5"; name = "Owen Dela Mercado";  email = "owner@demo.test";     phone = "+639171000021" },
  @{ pm = "pm_1UQPGxDoFhF7zry3Yexvlyyl"; name = "Rowan Castillo";     email = "owner2@demo.test";    phone = "+639171000024" },
  @{ pm = "pm_1UQPGyDoFhF7zry3jPhXLHUC"; name = "Paula Ramos";        email = "organizer@demo.test"; phone = "+639171000022" },
  @{ pm = "pm_1UQPGzDoFhF7zry3kSju65kq"; name = "Sam Villanueva";     email = "scanner@demo.test";   phone = "+639171000023" }
)

foreach ($c in $cards) {
  try {
    $r = Invoke-RestMethod -Method Post -Headers $auth -Uri "https://api.stripe.com/v1/payment_methods/$($c.pm)" -Body @{
      "billing_details[name]"             = $c.name
      "billing_details[email]"            = $c.email
      "billing_details[phone]"            = $c.phone
      "billing_details[address][country]" = "PH"
    }
    "OK      $($c.email): billing country $($r.billing_details.address.country), card issued in $($r.card.country)"
  } catch {
    "FAILED  $($c.email): $($_.ErrorDetails.Message)"
  }
}
```

## 4. Look up or clean up

```bash
# find a customer by email
curl -sS -G https://api.stripe.com/v1/customers -u "$STRIPE_SECRET_KEY:" -d email="customer1@demo.test"

# list a customer's saved cards, with brand, last 4 and expiry
curl -sS -G https://api.stripe.com/v1/customers/cus_REPLACE_WITH_THE_CUSTOMER_ID/payment_methods \
  -u "$STRIPE_SECRET_KEY:" -d type=card | grep -E '"(id|brand|last4|exp_month|exp_year)"'

# delete one customer (also removes their saved payment methods)
curl -sS -X DELETE https://api.stripe.com/v1/customers/cus_REPLACE_WITH_THE_CUSTOMER_ID -u "$STRIPE_SECRET_KEY:"
```

To wipe everything demo-related in one go, use *Developers → Delete all test data* in the Stripe Dashboard (test mode
only). That removes customers and charges, so run it only when you want a clean slate.

## 5. Optional: starting balance in PHP

A Stripe *customer balance* is a credit against invoices and subscriptions only. It does not pay a normal card charge, so it
will not fund checkout in this API. If you still want it, add it as a balance transaction (negative = credit, amount in
centavos, so -5000000 is ₱50,000). Do **not** set `balance` directly on the customer: that fixes the customer's currency to USD.

```bash
curl -sS https://api.stripe.com/v1/customers/cus_REPLACE_WITH_THE_CUSTOMER_ID/balance_transactions \
  -u "$STRIPE_SECRET_KEY:" -d amount=-5000000 -d currency=php -d description="Demo starting balance"
```

Spendable demo money for the app itself would need a wallet balance in the app's own database, which does not exist.

## What the API still needs

Creating customers in Stripe does nothing for the API on its own. To use them, a later change would need:

1. **A `StripePaymentGatewayClient`** implementing `PaymentGatewayClient` (`charge`, `refund`) and a property such as
   `app.stripe.secret-key` read from the environment, never committed. `MockPaymentGatewayClient` stays for tests.
2. **A place to keep the Stripe customer id**, for example a nullable `users.stripe_customer_id` column in a new Flyway
   migration (the next free number after V40). Customers can then be created lazily on a user's first checkout, which also
   covers real sign-ups, not only the demo accounts.
3. **The checkout request** currently carries `paymentMethodToken`; with Stripe it carries the `pm_…` id from Stripe.js
   (or the saved default for the customer), and the charge passes `customer` + `payment_method`.
4. **Payout cards** (organizer destinations) are a separate Stripe feature: paying out to organizers is Stripe Connect
   (connected accounts), not Customers. Not covered here.
5. **The demo seed** (`demo-data.sql`) does not hold Stripe ids; they live in Stripe, so a database reset does not touch
   them. Re-running the loop above is safe: existing customers are skipped.

## Checklist

- [ ] `sk_test_` key in an environment variable, not in a file
- [ ] Nine customers created (five buyers, two owners, organizer, scanner); none for the admin accounts
- [x] Customers created in the sandbox, set to PH / PHP / Asia/Manila (2026-10-14)
- [x] Test card attached and set as default for 7 of 9 customers
- [x] Billing country PH, holder name, email and phone set on all 7 cards
- [x] Card origin / issuing country PH: **not possible, accepted as a limit** (Stripe fixes it from the card number; no PH test card exists), accepted as US
- [ ] customer2 and customer3 given `pm_card_chargeCustomerFail` (the declined cards cannot be attached)
- [ ] customer5 and scanner balances reviewed (5,000,000 USD, positive = owed): zero them or replace the customers
- [x] Unused temporary customer deleted
- [ ] No full card number, CVC or secret key typed into a file, the database or this API
