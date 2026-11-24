# PROERP Netbanking

PROERP is a college net banking project with separate customer and administrator portals. It uses fictional accounts, balances, and transactions to demonstrate how a customer moves from registration and account approval to everyday banking.

## Customer portal

- Register and request a savings account, a current account, or both. Access begins after an administrator approves the registration.
- View account balances and recent activity, save payees, and send money between PROERP accounts. Printable receipts show the sender, recipient, amount, posting time, reference, and any transfer note.
- Filter transaction history and export statements as CSV or print them.
- Review the interest rate, repayment duration, monthly payment, and total cost before applying for a loan. Track the decision and make repayments after funding.
- Manage simulated debit cards and spending limits, pay bills, open fixed deposits, set savings goals, and track service requests.

## Admin portal

- Create customer records, review registrations and requested account types, manage identity verification, and issue temporary passwords.
- Search the customer and account directory, review flagged accounts, and open, freeze, or unfreeze accounts. Record deposits and balance adjustments.
- Monitor total customer balances, outstanding loan principal, and recent money movements on the dashboard.
- Approve or decline loan applications, fund approved loans, and record repayments. Resolve customer service requests and review the admin audit history.

## Technology

PROERP is an Eclipse Dynamic Web Project built with Java 17, JSP, and Jakarta Servlets. It runs on Apache Tomcat 10.1 and stores data in MySQL through JDBC.

A transfer commits the debit, credit, and transaction record together. Transfer receipts are available to the customers involved, and administrative changes are recorded in the audit history.
