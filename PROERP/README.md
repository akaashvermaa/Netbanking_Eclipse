# PROERP Netbanking Application

An Eclipse Dynamic Web Project using Java 17, JSP, Jakarta Servlets, Apache Tomcat 10.1, JDBC, and MySQL. Spring Boot is not used.

## Current Admin Features

- Bank account directory with search by customer username or account number and filters for active and frozen accounts.
- Open simulated savings and current accounts from an active customer's record. Each customer can have one account of each type, and new balances start at ₹0.00.
- View account details and freeze or unfreeze an account with a required decision reason. Account decisions appear in customer history and the shared audit log.

- Admin sign-in with salted PBKDF2 password hashes, CSRF checks, session renewal, and inactivity timeout.
- Admin dashboard with customer status counts.
- Customer search and filtering, approval of pending customers, suspension, and reactivation.
- Audit history for customer status changes.
- Database writes for a status change and its audit record happen in one transaction.
- Responsive admin sidebar, pending-review queue, and recent decisions on the dashboard.
- Customer detail pages with registration information and customer-specific audit history.
- Required decision reasons (up to 140 characters) for approvals, suspensions, and reactivations.
- Stale actions are rejected: an approval cannot silently reactivate a customer who was suspended after the page loaded.

Customer registration is a later phase. The local database now contains 15 fictional sample customers for admin demonstrations.

## Sample Customers

Run `com.netbanking.setup.SampleCustomerSetup` as a Java Application in Eclipse to seed a fresh local database after applying both SQL scripts. The seed uses JDBC and the existing schema. It skips existing sample usernames and never overwrites their passwords or statuses.

- Usernames start with `demo.` and emails use `example.test`.
- Initial distribution: 5 pending, 7 active, 3 suspended.
- Registration dates vary across the last 43 days.
- Sample passwords are random hashes; customer sign-in is not implemented yet.
- The existing administrator account is preserved.

Open Customers, filter by Pending, then choose **View & manage**. Review a record, enter a decision reason, and approve it. Active customers can be suspended; suspended customers can be reactivated. Every successful decision appears on that customer's history, the dashboard, and Audit history.

Verification exercised sign-in, all four admin pages, suspension and reactivation of a sample customer, and rejection of a stale approval. The sample customer was restored to ACTIVE. Two audit entries labeled `Demo verification` document these real verification actions.

## Eclipse and Local Setup

1. Import this folder as an existing Eclipse project. Set Java to 17 and associate **Tomcat v10.1** under **Project Properties → Project Facets → Runtimes**.
2. Keep MySQL Connector/J in `WebContent/WEB-INF/lib`; it is included in the project build path.
3. Copy `src/db.properties.example` to `src/db.properties` and enter local MySQL settings. The local file is ignored by Git.
4. The initial admin account has already been created in the local database. Do not rerun `InitialAdminSetup` unless setting up a fresh database.
5. For a fresh database, run `database/01_create_schema.sql` and `database/02_admin_audit_log.sql` in MySQL Workbench. With the `netbanking` schema selected, run `database/03_bank_accounts.sql`. For an existing database that already has the users and audit tables, use only the third script.
6. Import/refresh the renamed project in Eclipse, add it to the existing Tomcat server, then open `http://localhost:8080/PROERP/admin/login`.

The local admin password was set to the value requested in the setup conversation. No database or admin password is stored in this README.

## Project Layout

- `src/com/netbanking/web`: Servlets and request routing.
- `src/com/netbanking/dao`: JDBC queries and transactional updates.
- `src/com/netbanking/model`: Small records passed between DAO, servlet, and JSP layers.
- `src/com/netbanking/security`: Password hashing, CSRF tokens, and HTML escaping support.
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
