# PROERP Netbanking Application

An Eclipse Dynamic Web Project using Java 17, JSP, Jakarta Servlets, Apache Tomcat 10.1, JDBC, and MySQL. Spring Boot is not used.

For agent handoff, see [the project rules](../AGENTS.md) and [the setup and troubleshooting guide](../AGENT_GUIDE.md).

For a presentation machine, use the standalone launcher for [macOS](#standalone-demo-on-macos) or [Windows](#standalone-demo-on-windows).

## Current Admin Features

- Bank account directory with search by customer username or account number and filters for active and frozen accounts.
- Open simulated savings and current accounts from an active customer's record. Each customer can have one account of each type, and new balances start at ₹0.00.
- Use **Edit balance** on an active customer's active bank account to set its new total. The transaction and audit histories record the adjustment, and the form asks you to review again if the balance changed while you were editing. Apply `07_balance_adjustments.sql` before using this action on an existing database.
- View account details and freeze or unfreeze accounts. Account decisions appear in customer history and the shared audit log.
- Add a customer by full name, username, email, and phone number. Choose Active for an approved customer or Pending review to queue them.
- Record simulated deposits, loan payouts, and repayments in Balances and loans. The account balance, transaction, and audit entry are written together.
- The overview shows customer account balances, deposits recorded, loan payouts, and outstanding principal separately. Customer balances include loan proceeds still in accounts. They are not a measure of bank cash reserves.

- Admin sign-in with salted PBKDF2 password hashes, CSRF checks, session renewal, and inactivity timeout.
- Admin dashboard with customer status counts.
- Customer search and filtering, approval of pending customers, suspension, and reactivation.
- Audit history for customer status changes.
- Database writes for a status change and its audit record happen in one transaction.
- Responsive admin sidebar, pending-review queue, and recent decisions on the dashboard.
- Customer detail pages with registration information and customer-specific audit history.
- Approvals, suspensions, reactivations, email corrections, and account changes record the acting administrator automatically.
- Stale actions are rejected: an approval cannot silently reactivate a customer who was suspended after the page loaded.

## Customer Portal

1. Open [Customer registration](http://localhost:8080/PROERP/customer/register), choose a username and password, and leave the approval page open.
2. In an admin tab, open **Customers & Accounts > Pending review** and approve the new customer. The waiting customer tab signs in automatically after approval. Customers can also sign in manually later. For admin-created customers, use **Contact details > Set customer sign-in password** to issue a temporary credential.
3. Use **Overview** to see account balances, **Send money** to transfer to a PROERP account number, and **Activity** to filter transactions by account. **Loans** shows principal due, and **Profile** shows contact details and identity verification status.

Apply `08_customer_portal.sql` once before publishing the portal or using customer approval and suspension. Transfers use simulated balances and appear in the sender's and recipient's activity. An administrator still opens accounts and manages loans.

Apply `09_customer_services.sql` for the expanded dashboard. Use **Loans > Apply for a loan** as the customer, then **Customer requests > Approve and fund** as an admin. The payout appears in the customer's account activity. Customers can repay principal from the receiving account. The portal also has saved payees, printable transfer receipts, CSV statements, simulated cards and bill payments, fixed deposits, and in-app updates. Admins can mark submitted service requests resolved.

Apply `10_savings_goals.sql` for **Savings goals**. Customers can set aside funds, track progress and move funds back to the linked account. **Activity & statements** supports date, account and transaction type filters, plus search by reference or description. Use **Printable statement** to print or save the filtered results as PDF, or **Download CSV** for a spreadsheet.

For local demos, add `src/demo.properties` with `demo.customer.username` and `demo.customer.password` for an approved fictional customer, then refresh and publish. This ignored file enables **Sign in as demo customer** on loopback requests.

The same file accepts `demo.admin.username` and `demo.admin.password` for a dedicated local presentation admin. These settings enable **Sign in as demo admin** for direct loopback requests. Keep the file out of shared deployments.

## Sample Customers

After applying `06_customer_kyc.sql`, run `com.netbanking.setup.SamplePortfolioSetup` as a Java Application to populate the admin workspace with fictional full names, numbered accounts, and a week of deposits, loan payouts, and repayments. It also sets fictional KYC statuses, which are displayed separately from account access. It skips already posted sample transactions.

Run `com.netbanking.setup.SampleCustomerSetup` as a Java Application in Eclipse to seed a fresh local database after applying the SQL scripts. The seed uses JDBC and the existing schema. It skips existing sample usernames and never overwrites their passwords or statuses.

- Sample usernames use the `first.last` format and sample emails use `example.test`.
- Initial distribution: 5 pending, 7 active, 3 suspended.
- Registration dates vary across the last 43 days.
- Sample customer sign-in is enabled individually by setting a temporary password from the admin customer record.
- The existing administrator account is preserved.

Open **Customers & Accounts > Pending review** and select the customer. Choose **Approve customer**, or **Approve and continue** to open the next pending record. Active customers can be suspended and suspended customers can be reactivated. Every successful decision appears in Audit history.

Verification exercised sign-in, all four admin pages, suspension and reactivation of a sample customer, and rejection of a stale approval. The sample customer was restored to ACTIVE. Two audit entries labeled `Demo verification` document these real verification actions.

## Eclipse and Local Setup

1. Import this folder as an existing Eclipse project. Set Java to 17 and associate **Tomcat v10.1** under **Project Properties → Project Facets → Runtimes**.
2. Keep MySQL Connector/J in `WebContent/WEB-INF/lib`; it is included in the project build path.
3. Copy `src/db.properties.example` to `src/db.properties` and enter local MySQL settings. The local file is ignored by Git.
4. The initial admin account has already been created in the local database. Do not rerun `InitialAdminSetup` unless setting up a fresh database.
5. Run the SQL scripts in `database` in numeric order with the `netbanking` schema selected. For an existing installation, apply any missing scripts through `12_customer_profile.sql`.
6. Import/refresh the renamed project in Eclipse, add it to the existing Tomcat server, then open `http://localhost:8080/PROERP/admin/login`.

The local admin password was set to the value requested in the setup conversation. No database or admin password is stored in this README.

## Project Layout

- `src/com/netbanking/web`: Servlets and request routing.
- `src/com/netbanking/dao`: JDBC queries and transactional updates.
- `src/com/netbanking/model`: Small records passed between DAO, servlet, and JSP layers.
- `src/com/netbanking/security`: Password hashing and CSRF tokens. JSP escaping is in `src/com/netbanking/util/Html.java`.
- `src/com/netbanking/setup`: One-time initial admin creation utility.
- `WebContent/WEB-INF/views/admin`: JSP pages, protected from direct URL access.
- `WebContent/assets`: CSS and static browser assets.
- `database`: MySQL schema and additive migration scripts.

## Setup Problems We Encountered and Fixes

- **Tomcat `Bootstrap` class not found:** the Tomcat installation path was incorrect or incomplete. Use the extracted Tomcat folder that contains `bin`, `conf`, `lib`, and `webapps`.
- **Java facet/compiler mismatch and missing `src`:** the project compiler and Java facet were aligned to Java 17, and the source folder was added.
- **Tomcat showed 404 while the landing JSP worked:** Tomcat was running, but the project runtime was not associated correctly and compiled servlet classes were not included in the published web app. Assign Tomcat v10.1 in Project Facets → Runtimes and deploy the Java output to `WEB-INF/classes`.
- **Workbench reported “nothing is selected” or the SQL toolbar was disabled:** the SQL file was open but its tab was unconnected. Connect to the MySQL server first, then execute the schema script.
- **Workbench label looked like the database name:** `NetBanking` was the saved connection name. The app schema created by the SQL script is `netbanking`.
- **Browser showed connection refused:** Tomcat was stopped; start the existing server before opening the local URL.
- **404 returned after cleanup:** generated classes had been removed without being rebuilt. Keep `build/classes` while developing. Refresh the Eclipse project after external edits, rebuild if needed, and publish the server. Confirm `WEB-INF/classes/com/netbanking/web/AdminLoginServlet.class` exists in the deployed app.
- **Missing database configuration in the deployed app:** `db.properties` must be copied from `src` to `build/classes` and published to `WEB-INF/classes`, alongside the Java classes. Compilation with `javac` alone does not copy resource files.

If an admin route returns 404, check the deployed context path and confirm Eclipse has the project’s Tomcat runtime selected. If the route loads but shows a database message, check `src/db.properties` and verify MySQL is running.

## Remaining Work

- Customer registration and profile management.
- Customer access to their bank accounts.
- Internal transfers backed by the double-entry ledger, with concurrency-safe posting.
- Statements, simulated cards, service requests, and the concurrency demonstration.
## Standalone demo on macOS

Open Terminal in the PROERP folder and run:

```bash
bash Run-PROERP.command
```

To make it double-clickable in Finder, run `chmod +x Run-PROERP.command` once.

The launcher supports Apple Silicon and Intel on macOS 15 or later. It selects and verifies the correct Java and MySQL archives. It uses Microsoft OpenJDK 17.0.21.1, Tomcat 10.1.62, Connector/J 26.10.0, and MySQL 8.4 LTS. MySQL is pinned to 8.4.13 on Apple Silicon and 8.4.11 on Intel, which uses Oracle's archived Intel build. [Microsoft OpenJDK downloads](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-17) · [MySQL archives](https://downloads.mysql.com/archives/community/)

The first run needs internet access and about 3 GB of free space. You can run the launcher with a standard macOS user account. It sets up its own local runtime paths, builds the app, applies the SQL migrations, and adds fictional demo users and accounts. Open [PROERP](http://127.0.0.1:8081/PROERP/) and use the demo sign-in buttons. Press Enter in Terminal to stop both servers. Later launches reuse the downloads and database.

- Use `bash Run-PROERP.command --check` to test both demo sign-ins and stop the servers.
- Use `bash Run-PROERP.command --http-port 8082 --db-port 3308` if the default ports are busy.
- Startup logs are in `.standalone/macos-arm64/logs` or `.standalone/macos-x86_64/logs`. After an abrupt shutdown, the launcher can leave a lock under the corresponding runtime folder. Its `pid` file identifies the launcher process. Remove the lock only after checking that the launcher and its servers have stopped.
- When sharing the project, leave out `.standalone`, `build`, `src/db.properties`, and `src/demo.properties`.

## Standalone demo on Windows

Open `Run-PROERP.bat` from the PROERP folder on Windows x64. The first run needs internet access and about 500 MB of downloads. Allow about 3 GB of free disk space for the downloaded and extracted runtimes, database, and application.

The launcher verifies and caches these pinned downloads, builds the Java 17 source, applies the SQL migrations to its own MySQL database, and starts Tomcat at [http://127.0.0.1:8081/PROERP/](http://127.0.0.1:8081/PROERP/).

| Dependency | Pinned version |
|---|---|
| [Microsoft Build of OpenJDK](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-17) | 17.0.21.1 |
| [Apache Tomcat](https://tomcat.apache.org/download-10.cgi) | 10.1.62 |
| [MySQL Community Server](https://dev.mysql.com/downloads/mysql/8.4.html) | 8.4.13 LTS |
| [MySQL Connector/J](https://dev.mysql.com/downloads/connector/j/?os=26) | 26.10.0 |

Use **Sign in as demo admin** or **Sign in as demo customer**. The launcher seeds fictional accounts and transactions on the first run. Press Enter in the launcher window to stop Tomcat and MySQL. Run the same batch file later to rebuild and reuse your data.

The standalone files, data, logs and locally generated credentials live in the ignored `.standalone` directory. Share the source project and leave out `.standalone`, `build`, `src/db.properties`, and `src/demo.properties`. The launcher starts local processes on loopback ports 8081 and 3307. If either port is occupied, run `Run-PROERP.bat -HttpPort 8082 -DbPort 3308`. Windows needs the Microsoft Visual C++ x64 Redistributable if it is missing. [Microsoft's Visual C++ runtime download](https://learn.microsoft.com/en-us/cpp/windows/latest-supported-vc-redist)

Run `Run-PROERP.bat -Check` to test both demo sign-ins and then stop the local processes. Check `.standalone/logs` for startup errors.
