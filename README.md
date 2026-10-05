# Campus Lost-and-Found System

A Java 17+ OOP lab project with a responsive browser website and a Java console application. Both can use the same Supabase PostgreSQL reports table.

## Open the website

1. In your Supabase Dashboard, open **SQL Editor**.
2. Open `supabase/schema.sql` from this project, copy its contents into the SQL Editor, and click **Run**. This creates the table and carefully limits which fields the public website can read.
3. Double-click `Start Website.bat` or open `website/index.html` in your browser.

The website connects directly to Supabase using the project URL and publishable key you provided. It does not need the Java server running. If it was already open, refresh the page after the SQL has been run. Lost/found item details appear on the board. Contact details and campus IDs are stored in Supabase but are excluded from public reads.

The publishable key is designed for browser applications, but it must be paired with Row Level Security and appropriate grants. This project's SQL file enables RLS and limits public column access. Do not put a Supabase database password or secret/service-role key in `website/` or `src/main/resources/web/`.

## Java console application

The Java console program connects directly to PostgreSQL using a server-side database password. For it, copy `supabase.env.bat.example` to `supabase.env.bat` and enter the database connection URL, username, and password from Supabase Dashboard **Connect**. Keep that private file on your computer. Then run:

```powershell
call .\supabase.env.bat
mvn compile exec:java "-Dexec.mainClass=edu.campus.lostfound.Main"
```

A session pooler connection is the most compatible option for an IPv4-only network. Use the exact host, port, and username Supabase shows. The JDBC URL format is `jdbc:postgresql://HOST:PORT/postgres?sslmode=require`.

## Database design

The `public.reports` table stores report ID, type, item details, location, report date, reporter name, campus ID, contact, status, and claimant campus ID. The website only receives report card columns; PostgreSQL column grants prevent public reads of contact and campus IDs. Row Level Security limits public inserts to open reports and status updates to claim/return transitions. Java's repository uses prepared statements and a transaction when saving the report snapshot.

Existing `data/reports.tsv` data is imported by the Java application the first time it connects, if the Supabase table is empty. The former local SQLite file is retained as a local backup and is not automatically uploaded.

## Website features

- Submit lost and found reports
- Search and filter the shared reports board
- See text-based match suggestions
- Claim reports and mark them returned
- Store records in the Supabase-hosted PostgreSQL database

The browser website uses Supabase's Data API. Supabase hosts the database, while the standalone page is opened from this project folder. To let other people visit from their own devices, publish the website files to a static hosting service and keep the same RLS setup.

## OOP concepts

- **Encapsulation:** private report fields and guarded lifecycle methods
- **Abstraction:** abstract `ItemReport` base class
- **Inheritance:** `LostItemReport` and `FoundItemReport` extend `ItemReport`
- **Polymorphism:** report subtypes are handled through `ItemReport` references
- **Composition:** each report contains a `User` reporter
- **Enums and collections:** `ItemStatus`, `ReportType`, and `ArrayList<ItemReport>`
- **Separation of concerns:** `Main`, `LostAndFoundService`, `ReportRepository`, and `WebServer` have separate roles

## Project structure

```text
src/main/java/edu/campus/lostfound/   Java domain, service, console, database, and web server
src/main/resources/web/                Website files served by the optional Java HTTP server
website/                               Editable standalone website source files
supabase/schema.sql                    Supabase PostgreSQL schema, grants, and RLS policies
supabase.env.bat.example               Local Java database-connection template
Start Website.bat                      Opens the standalone website page
pom.xml                                Java 17 and PostgreSQL JDBC build configuration
```

## Deploy the website with GitHub Pages

This project includes a GitHub Actions workflow that publishes the static frontend from `website/` whenever code is pushed to `main`. After the repository is created and the first push completes, GitHub Pages will provide a public website URL under `https://<account>.github.io/<repository>/`. In the repository's **Settings → Pages**, select **GitHub Actions** as the build and deployment source if GitHub has not selected it automatically.

The browser frontend uses the Supabase project URL and publishable key in `website/supabase-config.js`. A publishable key is intended for browser use; the database must retain the Row Level Security policies from `supabase/schema.sql`. Never commit `supabase.env.bat`, database passwords, or Supabase secret/service-role keys. The Java console program's database password must be configured separately and should not be deployed to GitHub Pages.
