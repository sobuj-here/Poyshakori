# 💰 Poyshakori (পয়সাকড়ি) — Private Personal Finance & Accounting App

**Poyshakori** is a 100% offline-first, private personal finance and double-entry accounting application for Android. Built with Kotlin, Material Design 3, Room SQLite, and MVVM architecture.

---

## 🌟 Key Features

### 📊 1. Dashboard
- **Total Net Worth**: Aggregate balance calculated across all active accounts.
- **Income vs. Expense Summaries**: Real-time monthly calculations with percentage and trend indicators.
- **Quick Actions**: One-tap buttons for `+ Income`, `- Expense`, and `⇄ Account Transfer`.
- **Recent Transactions Feed**: Live stream of latest activity with drill-down capability.

### 💳 2. Multi-Account Management
- Track **Cash**, **Bank**, and **Mobile Wallets** (e.g. bKash, Nagad, Rocket, Upay).
- Real-time balance calculations derived directly from transactions.
- **Inter-Account Transfers**: Move money between accounts (e.g., Bank ➔ bKash).
  - *Accounting Invariant*: Transfers adjust source and destination balances equally without creating or destroying net worth, and are never counted as income or expenses in financial reports.

### 🧾 3. Transactions Management
- Full CRUD (Create, Read, Update, Delete) for Income, Expense, and Transfer entries.
- Search by category, note, reference number, or amount.
- Filter by transaction type: All, Expense, Income, Transfer.
- Automatic balance adjustments upon creation, editing, or deletion.

### 🎯 4. Budgets & Spending Limits
- Set monthly budget limits per expense category (e.g., Food & Dining, Transportation).
- Visual progress bar comparing actual expenses against monthly targets.
- Dynamic alerts when spending approaches or exceeds budget limits.

### 🤝 5. Debts & Loans (ধার ও দেনা-পাওনা)
- Track money you owe (**Debts / আমার দেনা**) and money owed to you (**Receivables / আমার পাওনা**).
- Record person name, total amount, due dates, and notes.
- One-click **"Mark as Paid"** with automated status tracking.

### 📈 6. Visual Analytics & Reports
- **Donut/Ring Category Breakdown**: Hardware-accelerated custom Canvas chart displaying spending proportion per category.
- **Export to CSV**: Export transaction records into standard CSV format and share via Android's native share sheet.

### 🔒 7. Privacy & Security
- **100% Local-First**: Zero external servers, zero advertising SDKs, zero analytics tracking.
- **Biometric App Lock**: Optional fingerprint/device lock on app launch with graceful hardware fallback.
- **AES-256 Encrypted Backups**: Export and import complete encrypted database backups protected by PBKDF2 key derivation and AES-CBC encryption.
- Verification of database integrity before restoring backup to prevent data corruption.

---

## 🛠️ Architecture & Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 1.9.0 |
| **UI Framework** | Android Views + Material Components 1.9.0 (DayNight Dark Theme) |
| **Architecture** | MVVM (Model-View-ViewModel) + Single Source of Truth Repository |
| **Local Database** | Android Room 2.6.1 + SQLite |
| **Async & Streams** | Kotlin Coroutines + Flow / StateFlow |
| **Biometrics** | AndroidX Biometric 1.2.0 |
| **Testing** | JUnit 4.13.2 + Kotlinx Coroutines Test |

---

## 🏗️ Project Structure

```
Poyshakori/
├── app/
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/poyshakori/app/
│       │   │   ├── MainActivity.kt               # Central 5-tab controller
│       │   │   ├── PoyshakoriApp.kt              # Application class & seed data
│       │   │   ├── backup/
│       │   │   │   ├── BackupManager.kt          # AES-256 encrypted backup/restore
│       │   │   │   └── ExportManager.kt          # CSV generator & Android share sheet
│       │   │   ├── data/
│       │   │   │   ├── dao/                      # Room DAOs (Transaction, Account, Category, Budget, Debt, Savings)
│       │   │   │   ├── database/AppDatabase.kt   # SQLite Room Database (v7)
│       │   │   │   ├── entity/                   # Database Entities & models
│       │   │   │   └── repository/               # Repositories enforcing accounting invariants
│       │   │   ├── security/SecurityManager.kt   # BiometricPrompt & App Lock settings
│       │   │   └── ui/
│       │   │       ├── accounts/                 # AccountActivity & AccountAdapter
│       │   │       ├── budget/                   # BudgetActivity & BudgetAdapter
│       │   │       ├── dashboard/MainViewModel.kt# Central StateFlow ViewModel
│       │   │       ├── debt/                     # DebtActivity & DebtAdapter
│       │   │       ├── reports/                  # CategoryChartView (Canvas Donut)
│       │   │       └── transactions/             # TransactionAdapter
│       │   └── res/
│       │       ├── layout/                       # Clean Material XML layouts & dialogs
│       │       ├── values/                       # colors.xml, strings.xml, themes.xml
│       │       ├── drawable/                     # Vector drawables & rounded card shapes
│       │       └── menu/bottom_nav_menu.xml      # Bottom navigation specification
│       └── test/                                 # Unit tests for accounting logic & crypto
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🚀 Building & Testing

### Prerequisites
- JDK 17
- Android SDK (minSdk 24, targetSdk 34)
- Gradle 8.1.1 (included via `./gradlew`)

### Compile Sources
```bash
./gradlew compileDebugSources
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

### Build Debug APK
```bash
./gradlew assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## ⚖️ Accounting Rules Enforced

1. **Income**: Adds the specified amount to the target account's `currentBalance`.
2. **Expense**: Deducts the specified amount from the source account's `currentBalance`.
3. **Transfer**: Deducts from the source account and adds to the destination account. The overall system balance remains unchanged ($Net\ Worth_{before} = Net\ Worth_{after}$).
4. **Transaction Reversal**: Deleting or updating a transaction automatically rolls back its historical balance impact before applying the new state.
5. **Exact Integer Math**: All monetary amounts are handled as `Long` integers (BDT) to eliminate floating-point precision errors.

---

## 🔒 Security & Privacy Guarantee

- **No Remote Telemetry**: Poyshakori contains no third-party tracking, analytics, or advertising libraries.
- **Zero Internet Permissions**: The app does not request or require network access (`android.permission.INTERNET`).
- **Encrypted Storage**: Backups are encrypted using AES-256 with key stretching via PBKDF2 (10,000 iterations).

---

## 📜 License
Licensed under the Apache License, Version 2.0.
