# PROERP agent instructions

Read [AGENT_GUIDE.md](AGENT_GUIDE.md) before changing the application or troubleshooting the local server. The Eclipse project is in [PROERP](PROERP/), inside the outer workspace folder.

## Project rules

- Keep PROERP an Eclipse Dynamic Web Project. Use Java 17, JSP and Jakarta Servlets on Tomcat 10.1, and MySQL through JDBC. Do not introduce Spring Boot, Spring, another app server, or another database.
- Preserve the `PROERP` project name and context path, the `src` and `WebContent` layout, and the Eclipse deployment mappings. Check the existing configuration before proposing another import or server setup.
- Keep routine admin actions short. Do not require a reason or description for routine operations. Record the actor, target, action and time automatically.
- Keep the Admin and Customer roles separate. Customer self-registration starts pending and only administrators can approve access. Admins can also create customer records and issue temporary passwords.
- Use fictional data for demonstrations. Never commit or print database passwords, admin credentials, or `src/db.properties`. Preserve existing records, balances and audit history.
- Treat the source project as authoritative. Eclipse's generated classes and Tomcat's published copy can be regenerated. Restore and publish generated files after a rebuild before checking a route.

## Interface preferences

- Use a clean, Notion-style interface: solid neutral surfaces, clear borders, and readable typography.
- Use plain text labels. Avoid emojis and decorative Unicode symbols, including arrows used only as decoration.
- Do not use green dots to imply a user is online. Use a text label when status information is needed.
- Do not use gradients or blue and purple accent palettes. Use solid colors and reserve muted status colors for actual states.
- Preserve meaningful characters in customer data and currency amounts.
