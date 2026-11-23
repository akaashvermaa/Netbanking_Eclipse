# PROERP Netbanking Application

An Eclipse Dynamic Web Project using Java 17, JSP, Jakarta Servlets, Apache Tomcat 10.1, JDBC, and MySQL. Spring Boot is not used.

For agent handoff, see [the project rules](../AGENTS.md) and [the setup and troubleshooting guide](../AGENT_GUIDE.md).

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
