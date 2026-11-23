# PROERP agent handoff and troubleshooting

Read [AGENTS.md](AGENTS.md) first. This is a placement presentation project. It models banking with fictional data. Keep the workflow easy to demonstrate and describe what the project actually does.

## Start here

1. Run `git status --short` in the outer workspace. Preserve the user's local edits. The outer folder may be named `Netbaking`. The Eclipse project to import and deploy is the inner `PROERP/` folder.
2. Use the existing Eclipse and MySQL setup when it works. Check the reported route and server console before changing runtimes, project facets, database settings, or ports.
3. Edit source in `PROERP/`. Compile, publish, and check the actual local route before claiming a live fix. Use the Eclipse-managed server instance.
4. If a terminal command is blocked by the sandbox, treat that separately from Tomcat or MySQL errors. Follow your tool's approval process for access outside the project or to localhost. Previous approvals may not apply to a new agent session.

## Stack and source layout

| Location | Purpose |
|---|---|
| `PROERP/.project`, `.classpath`, `.settings/` | Eclipse project name, Java build path, facets, UTF-8 settings, and server deployment mapping. |
| `PROERP/src/com/netbanking/web/` | Servlets validate requests, call DAOs, set view data, and forward or redirect. |
| `PROERP/src/com/netbanking/filter/AdminAuthFilter.java` | Shared filter for `/admin/*`. Protects admin routes and checks CSRF on POST. |
| `PROERP/src/com/netbanking/dao/` | `AdminDao` for customers and audit, `BankAccountDao` for accounts, `FinanceDao` for money movements and loans. |
| `PROERP/src/com/netbanking/config/` | `DatabaseConnection` reads `db.properties` from the classpath and opens JDBC connections. |
| `PROERP/src/com/netbanking/security/`, `util/Html.java` | Password hashing, CSRF tokens, and HTML escaping. |
| `PROERP/src/com/netbanking/setup/` | `InitialAdminSetup` for a fresh database, `SampleCustomerSetup` for fictional customer records. |
| `PROERP/WebContent/index.jsp` | Public home page and links to Customer and Admin sign-in. |
| `PROERP/WebContent/WEB-INF/views/admin/` | Private JSP views, including `navigation.jspf`. Open their servlet routes, not the JSP files. |
| `PROERP/WebContent/assets/css/` | `home.css` for the public page, `app.css` for base styles, `admin-console.css` for shared admin styles, and page-specific stylesheets for customers. |
| `PROERP/database/` | MySQL schema scripts, in numeric order. |

Use Java 17 for the application and `jakarta.servlet.*` for web APIs. Tomcat 10.1 supplies the servlet API. Keep Connector/J in `WebContent/WEB-INF/lib`; the current file is `mysql-connector-j-26.10.0.jar`. JSP builds happen separately from Java compilation. Keep JSP scriptlets compatible with the configured Jasper source level and put modern Java syntax in servlet or DAO classes.

## Eclipse and Tomcat setup

| Setting | Required value |
|---|---|
| Eclipse distribution | Eclipse IDE for Enterprise Java and Web Developers with WTP server tools |
| Import | File > Import > General > Existing Projects into Workspace. Select the inner `PROERP` folder and use the existing files. |
| Project name and deployed context | `PROERP` and `/PROERP` |
| Java build path | `src` source folder, JavaSE-17 library, `build/classes` output |
| Project facets | Java 17 and Dynamic Web Module 6.0 |
| Target runtime | `Apache Tomcat v10.1`, matching the name in `.classpath` and the project facet configuration |
| Deployment assembly | `WebContent` at `/` and Java output at `/WEB-INF/classes` |
| Source encoding | UTF-8 for Java and JSP |

1. Register the installed JDK 17 under Window > Preferences > Java > Installed JREs. Check Project Properties > Java Compiler if the Java facet and compiler level differ.
2. Register the Tomcat 10.1 installation under Window > Preferences > Server > Runtime Environments. Point Eclipse at the extracted folder containing `bin`, `conf`, `lib` and `webapps`. Confirm the runtime is associated under Project Properties > Targeted Runtimes or Project Facets > Runtimes.
3. Open Window > Show View > Servers. Add `PROERP` to the existing Tomcat server if it is missing, then start it. Only one Tomcat should listen on the application port.
4. After external edits, select `PROERP`, press F5, let Eclipse build, then publish from the **Servers** view. With automatic builds enabled, the manual Build commands can be unavailable. A full rebuild is under Eclipse's top-level Project menu. Server actions are on the server row or toolbar in the Servers view, rather than in Project Explorer or beneath an individual module.
5. Check [the homepage](http://localhost:8080/PROERP/), then [Admin sign-in](http://localhost:8080/PROERP/admin/login). The server instance uses port 8080 on the current machine. Use the actual configured port on another machine.

### Local Windows installation observed during development

These are reference paths for the user's machine, not settings to hardcode into the application:

- JDK 17: `C:\Program Files\Java\jdk-17`.
- Tomcat installation: `D:\Tomcat\apache-tomcat-10.1.62`.
- Eclipse workspace: `C:\Users\User\eclipse-workspace`. Its previously active WTP instance was `.metadata\.plugins\org.eclipse.wst.server.core\tmp0` under that workspace. Confirm the current instance in the Eclipse console's `CATALINA_BASE` line. The installation directory appears as `CATALINA_HOME`.
- The WTP deployment was `<CATALINA_BASE>\wtpwebapps\PROERP`. The instance logs are in `<CATALINA_BASE>\logs` and its effective configuration in `<CATALINA_BASE>\conf`. The installation's `webapps` directory may not be the one Eclipse is serving.

### Command-line build fallback

If Eclipse does not rebuild after a refresh, compile from the inner `PROERP` directory. Adjust these reference paths for the machine:

```powershell
$jdk = 'C:\Program Files\Java\jdk-17'
$tomcat = 'D:\Tomcat\apache-tomcat-10.1.62'
New-Item -ItemType Directory -Force 'build\classes' | Out-Null
$sources = Get-ChildItem -LiteralPath 'src' -Recurse -Filter '*.java' | ForEach-Object FullName
& (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 --release 17 -cp (Join-Path $tomcat 'lib\servlet-api.jar') -d 'build\classes' $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed' }
Copy-Item -LiteralPath 'src\db.properties' -Destination 'build\classes\db.properties'
```

That creates Java output and copies the local JDBC configuration. It does not compile JSPs or publish to Tomcat. Refresh in Eclipse and publish. If publishing cannot be done through Eclipse and an authorized local file copy is needed, verify the actual WTP deployment first. Copy only the needed files to their matching locations. Java classes go to `WEB-INF/classes`, JSPs and assets follow their `WebContent` paths, and Connector/J goes to `WEB-INF/lib`. Treat the manual copy as a local recovery step. Keep all source changes in the repo and make sure a future Eclipse publish reproduces the fix.

## MySQL and credentials

MySQL Workbench is a client. MySQL Server needs to be running separately. On the user's machine the local connection uses port 3306. The Workbench saved connection name `NetBanking` is a label. The actual schema is `netbanking`.

| Script | Creates |
|---|---|
| `01_create_schema.sql` | Schema and `users` with admin and customer roles |
| `02_admin_audit_log.sql` | `admin_audit_log` |
| `03_bank_accounts.sql` | `bank_accounts` |
| `04_admin_operations.sql` | `customer_profiles`, `bank_loans`, and `account_movements` |
| `05_account_review.sql` | `account_review_flags` for the account-level review queue |
| `06_customer_kyc.sql` | `customer_kyc` for fictional verification statuses, separate from customer access status |
| `07_balance_adjustments.sql` | Adds credit and debit adjustments to the account movement types for **Edit balance** |
| `08_customer_portal.sql` | Adds customer credential versioning and the transfer journal. Apply once before publishing the portal. |
| `09_customer_services.sql` | Adds loan applications, saved payees, simulated cards, fixed deposits, customer postings and service requests. |
| `10_savings_goals.sql` | Adds savings goals and the related credit and debit posting types. |
| `11_card_activity.sql` | Adds the simulated card purchase posting type for the customer Overview. |
| `12_customer_profile.sql` | Adds mailing address fields and fills missing contact details for known fictional records. |
| `13_account_onboarding.sql` | Stores requested account types for pending customer registration and admin onboarding. |
| `14_loan_terms.sql` | Stores fixed rates and durations on applications and loans, plus remaining interest on disbursed loans. |

For a fresh database, connect in Workbench and apply the scripts in order. Run `USE netbanking;` in the connected session before scripts 03 and 04. For an existing database, inspect `SHOW TABLES;` and apply missing additive scripts. Avoid dropping or resetting the user's schema. For future changes, add a new migration and account for existing rows.

Copy `PROERP/src/db.properties.example` to `PROERP/src/db.properties` for a new setup. The actual file is ignored by Git. Confirm the URL selects the intended schema. Copy the local file into `build/classes` if compiling manually, and check that it also lands in deployed `WEB-INF/classes`. Verify presence without printing contents.

Admin login credentials and MySQL credentials serve different purposes. For a fresh database, run `InitialAdminSetup` as a Java Application in Eclipse after the schema and JDBC configuration are ready. It leaves an existing admin in place. Existing passwords are stored as hashes. If an authorized reset is necessary, use the application password hasher to update the hash, salt, and iteration count together. Do not put credentials in SQL scripts, docs, commit history, commands visible to other users, or screenshots.

## Implemented workflows and boundaries

| Route below `/PROERP` | What it does |
|---|---|
| `/` | Public homepage and banking information. |
| `/admin/login` and `/admin/logout` | Session login and POST sign-out. Admin passwords use salted PBKDF2. Inactivity timeout is 15 minutes. |
| `/admin/password` | Change the signed-in admin's password using the current password. The update is audited and rotates the current session ID and CSRF token. |
| `/admin/dashboard` | Customer and account counts, oldest-first pending queue, recent activity, borrower list, and finance totals. |
| `/admin/queue` | Shows only accounts with an explicit flag, a freeze, or suspended customer access. Review pending registrations in Customers. |
| `/admin/customers` | Search by username or email, filter status, review pending customers, or choose Add customer. |
| `/admin/customers?new=1` | Add contact and address details, account types, KYC status, access status and an optional temporary password. Active customers get their selected accounts during creation. |
| `/admin/customer?id=<id>` | Customer details, email correction, approve, suspend or reactivate, linked accounts and audit. Approve & continue moves to the next pending registration. |
| `/admin/accounts` | Search or filter bank accounts. Opening from the customer record requires Active customer status. Each customer can hold one savings and one current account. New balances start at zero. Admins can freeze or unfreeze accounts. |
| `/admin/finance` | Manage accounts and loans. Recent activity includes customer transfers, bill payments, deposits and repayments. `?accountId=<id>` limits the view to one account. |
| `/admin/requests` | Approve and fund or decline loan applications, resolve service requests, and review completed decisions. |
| `/admin/audit` | Events across the admin workspace. Filter customer or account events and search by actor, customer, action, or note. |
| `/customer/login` | Active customer sign-in. An admin may issue a temporary password from an active customer record. |
| `/customer/register` | Creates a customer with pending access and a requested account type. From Pending review, approve the requested accounts, select a different type, or approve access only. The registrant keeps their chosen password. |
| `/customer/approval` | A verified pending customer can keep this page open to sign in automatically once approved. |
| `/customer/overview`, `/customer/activity`, `/customer/loans` | Customer-owned account balances, account-filtered history, and loan principal. |
| `/customer/transfer`, `/customer/profile`, `/customer/password` | Transfers by PROERP account number, identity details, and password changes. |
| `/customer/cards`, `/customer/deposits`, `/customer/bills`, `/customer/services`, `/customer/updates` | Simulated debit card settings, fixed deposits, bill payments, service requests and in-app updates. |
| `/customer/goals`, `/customer/statement` | Set aside funds against a savings target, and export filtered customer activity as CSV or a printable statement. |

- Routine actions use buttons and compact fields, without mandatory reason boxes. Keep automatic audit recording. Existing customers created before `customer_profiles` may have no profile row. Display the username and available data as a fallback.
- Sample usernames use `first.last`, without the old `demo.` prefix. Fictional emails use `example.test`. The seed adds 15 sample customers if absent and skips existing usernames or email addresses. The local database may also contain a manually added fictional customer and sample transactions. Do not assume fixed IDs or counts.
- Deposits add to balances. Loan payouts add principal to the borrower's balance. New term loans use simple interest on the original principal. The quote shows the monthly schedule, final rounded payment and total due before submission. Payments are manual and reduce interest first, then principal. Any remaining fixed interest is included in settlement. Existing principal-only loans keep their original balances and terms.
- The dashboard's customer balance total is the sum of account balances. It includes loan proceeds still in customer accounts and does not measure cash reserves. Keep deposits, loan payouts, and outstanding principal separate.
- `FinanceDao` uses `BigDecimal`, SQL row locks, a unique request key, and one transaction for balance, loan, movement, and audit updates. Preserve duplicate prevention, positive amount validation, account and customer status checks, and repayment limits. Never set balances directly to fabricate dashboard figures.
- Admins can set a temporary password during customer creation or from the customer record after activation. The customer must replace it before using the portal. Self-registered customers choose a password and can use it after approval. A password reset or customer access change invalidates prior customer sessions on their next request.
- Customer transfers use one transaction for both account balance updates and the transfer journal. Customer activity merges that journal with admin movements and customer postings. Statements can be filtered by account and date and exported as CSV. Transfer receipts are printable. Cards and bills are simulated and use no external network.
- Savings goals move funds between the linked bank account and a separate goal balance. Both changes and the activity posting commit together. Empty goals can be closed, while funded goals must be emptied first.
- The customer Overview includes seven days of posted credits and debits, recent activity for each owned account, and card controls. Simulated card purchases are posted to the card's linked account and checked against its block status and daily limit inside the posting transaction.
- Loan applications use the customer's active account. Admin approval locks the application and posts the payout, loan with the saved terms, and audit event together. Customers can make payments from the same account. Fixed deposits use a simulated annual simple rate and can be redeemed at maturity. Service request resolution records an admin action and changes the request status.
- Keep all admin mutations behind POST, the existing auth filter, and CSRF. Continue to use prepared SQL, escape customer content with `Html.escape`, and use the app context path in links and forms.

## Troubleshooting by symptom

| Symptom | Check and fix |
|---|---|
| `Could not find or load main class org.apache.catalina.startup.Bootstrap` | Verify the configured Tomcat folder, including `bin/bootstrap.jar` and `bin/tomcat-juli.jar`. Check the Eclipse runtime path for a stale or incomplete extraction. |
| Missing `src`, invalid build path, or Java facet version mismatch | Confirm that the inner `PROERP` folder is imported. Verify `src`, compiler compliance 17, Java facet 17, JavaSE-17 library and Tomcat runtime. Fix the first entry in Eclipse Problems before investigating downstream errors. |
| Browser says connection refused | Check server status in the Servers view and startup logs. Confirm which port is listening. If the port is busy, identify its owner before stopping anything or changing ports. |
| Homepage renders but admin login is 404 | Check whether `AdminLoginServlet.class` exists in the project's `build/classes` and the deployed `WEB-INF/classes/com/netbanking/web`. If missing in both, rebuild. If only missing in the deployment, refresh and publish. The JSP homepage can work while Java servlet classes are absent. |
| Whole `/PROERP/` returns 404 | Check the WTP module list and actual context path. Eclipse may still have a stale `NetBankingEclipseTest` module from before the rename. Update the server module registration and use the existing inner `PROERP` project. Do not delete the source folder to remove an old registration. |
| New servlet is 404 after publish | Verify compiled class, `@WebServlet` path, Jakarta imports, active deployment directory, and server reload. Check Console for annotation or startup failures. JSPs under `WEB-INF` are intentionally unreachable directly. |
| Build passes but a route is missing | Check that the expected Java source file is nonempty and produces a class. An empty Java file can compile without emitting one. Keep generated classes in `build/classes` rather than source `WebContent/WEB-INF/classes`. |
| JSP returns 500 or Jasper rejects Java syntax | Inspect the first JSP filename and line number in the error. Java 17 application classes compiled successfully even when Jasper rejected a switch expression in a JSP. Move logic to Java or use compatible template syntax, then publish and request the route again. |
| Workbench reports nothing selected, or Run is disabled | Open a connected SQL editor. Select the schema, place the cursor in a statement or select the intended SQL, and run it. Inspect Action Output for failures. |
| JDBC connection refused or communications link failure | Confirm MySQL Server is running on the expected host and port. A CLI sandbox block may report permission denied even when Tomcat can connect. Check Tomcat's exception and distinguish host reachability from tooling restrictions. |
| `Access denied`, `Unknown database`, or missing table | Confirm JDBC credentials, configured schema, and tables. Apply the missing migration to the correct schema. Avoid logging secrets. |
| Missing `db.properties` or driver class | Check deployed `WEB-INF/classes/db.properties` and `WEB-INF/lib/mysql-connector-j-26.10.0.jar`. Plain `javac` does not copy resource files. |
| A form shows “This form expired” | Use the recovery link and reopen the form. Each portal has its own CSRF token and 15-minute idle timer, so signing into the other portal leaves current forms usable. A Tomcat restart can end both sessions. |
| UI looks unchanged after editing | Compare source and published copies, confirm HTTP is serving the expected stylesheet path, and hard-refresh the browser. Keep shared admin colors neutral and check stylesheet order before adding overrides. |
| Balance is zero or loan absent | Zero is normal for a new account. Record a simulated deposit or payout through Finance. Check that the account and customer are active, and use the transaction history to verify what was posted. |

## What to verify after a change

- Build with Java 17, confirm the right files reached the active Tomcat deployment, and check the real route. A successful compile alone does not prove that JSP rendering or deployment works.
- For UI changes, confirm the stylesheet loads and the edited page is reachable through the normal navigation. Check narrow widths when layouts change. Follow the Notion-style preferences in `AGENTS.md`.
- For admin work, sign in and check the relevant flow. When using HTTP requests, keep the session cookie, read the login token from the form, and retrieve a fresh token after login. Do not record credentials in test files.
- For data changes, use a fictional record and check that IDs and links to accounts and audit entries remain intact. For finance, verify balance and outstanding principal, and check that retries, frozen accounts, and excess repayments cannot post invalid changes. Do not modify the user's records simply to smoke-test a page.
- Tell the user what was changed, what was verified against the live deployment, and any behavior still unverified. Update this guide when a new deployment or setup issue is understood.
