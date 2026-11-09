BEAUTY BLOOM SALON  -  Salon Management System (Java Swing + MySQL)
====================================================================

HOW TO RUN
  1. Start MySQL. Open db.properties and put your MySQL password in db.password.
  2. In this folder run:   mvn clean compile exec:java
     (The app creates the database "salon_db", all tables, the staff/owner accounts
      and sample data by itself on first start. Existing data is never deleted.)

THE 3 KINDS OF USERS
  CLIENT   - signs in with just an EMAIL (no password). First time: asked for name + phone.
             Demo client: sana.malik@example.com   (others: hira.ali@, zara.khan@, maria.shah@example.com)
  STAFF    - username + password (3 staff accounts, each with its own password):
               staff  / Staff@123    (Senior Stylist)
               ayesha / Ayesha@123   (Ayesha Khan)
               sara   / Sara@123     (Sara Ahmed)
  OWNER    - username + password.   admin / Admin@123   (the salon owner / administrator)
  Staff and owner must choose a new password at first login.
  The app repairs these four logins on every start: a missing one is created, and one whose password was
  never changed is set back to the password above - so they always work on a first try.

FORGOT A PASSWORD / LOCKED OUT?
  - Staff: the owner opens Users & Staff -> select the person -> RESET PASSWORD.
  - Owner (or anyone, if nobody can log in): run once
        mvn clean compile exec:java -Dexec.args="--reset-demo"
    This resets admin, staff, ayesha and sara to the passwords above (and re-enables them).
    Clients never need a password - they sign in with their email.

THE WORKFLOW
  1. CLIENT  signs in with email -> browses services with pictures and prices -> REQUEST BOOKING
             (preferred staff, date, time, note).
  2. STAFF   gets a notification -> "Requests" tab -> SET TIME & CONFIRM (or DECLINE).
  3. CLIENT  gets a notification "booking confirmed with date/time and total".
  4. CLIENT  opens "My Bookings & Payments" -> sees every service, amount and payment status -> PAY NOW
             (Card / JazzCash-Easypaisa / Bank Transfer / Cash). Demo payment: no real money moves.
             The payment is saved as PENDING (not counted as revenue yet).
  5. STAFF   gets a notification -> "Confirm Payments" tab -> CONFIRM PAYMENT (or NOT RECEIVED, then the
             client is told to pay again). The CLIENT gets "payment confirmed"; the payment now counts as revenue.
  6. STAFF   starts the service and marks it completed -> CLIENT is notified.
  THE OWNER IS NOTIFIED AT EVERY STEP: client chose a staff member, staff confirmed/declined, client paid,
  staff confirmed the payment, service started/completed, client cancelled. The owner only watches
  (Bookings, Payments, Reports, Notifications) - the owner no longer books clients or handles payments.
  7. CLIENT  writes feedback: stars, comment and an optional photo picked from the computer.
     Feedback goes to the OWNER and the STAFF member who served (notification + "Feedback" tab)
     and shows in the "Reviews" tab for other clients (shown as "Sana M.").
  Notifications update by themselves every few seconds (bell button in the top bar), so you can
  open the app twice (client in one window, staff/owner in another) to see the whole flow live.

WHAT EACH USER SEES
  Client : Dashboard, Services & Prices (cards with photos), My Bookings & Payments, Reviews, Notifications
  Staff  : Dashboard, Requests, My Appointments (with paid/unpaid), My Work History, My Feedback, Notifications
  Staff  also: Confirm Payments tab (payments of their own bookings only)
  Owner  : Dashboard, Clients, Bookings (view only), Services (add/edit + PHOTO from the file
           browser), Payments, Feedback, Reports, Users & Staff, Notifications

PICTURES
  - Front page picture:      src/main/resources/images/salon.png
  - Service photos:          Owner -> Services -> select a service -> PHOTO (opens the computer's file browser)
  - Feedback photo:          Client -> WRITE FEEDBACK -> Add photo...
  Pictures are shrunk and saved in the database. Services without a photo get a coloured placeholder.

BOOKING STATUSES
  REQUESTED -> CONFIRMED -> IN_PROGRESS -> COMPLETED   (or CANCELLED).
PAYMENT STATUSES
  PENDING (client says paid) -> CONFIRMED (staff received it). Only CONFIRMED payments count as revenue.

NOTES
  - The old Receptionist role was removed. If your database had receptionist accounts they are kept but disabled.
  - If salon_db already had users or clients, the sample bookings/reviews are NOT added (your data is left alone).
    To reload the sample data, drop the database (DROP DATABASE salon_db;) and start the app again.
  - migration.sql can be run in MySQL Workbench instead (optional); the app does the same on start.


SERVICE PICTURES
  The 11 service pictures (Hair Color, Hair Cut, Hair Styling, Makeup, Manicure, Pedicure, Facial Glow, Spa Relax,
  Bridal Package, Party Ready, Wedding Ready) are in src/main/resources/images/services/ and are loaded into the
  database automatically on start for any service that has no picture yet. A picture the owner uploads later is kept.

FRONT PAGE VIDEO
  The welcome page plays a silent, looping 3D salon walk-through as its background (frames in
  src/main/resources/video/intro/, played by VideoPanel.java - no extra libraries needed). It moves slightly with the
  mouse for a 3D feel and pauses when you leave the page. To change it, replace the f0001.jpg ... frames and update count.txt.

Front page: the background video (with its sound) is in src/main/resources/video/intro (frames + audio.wav). Use the "Sound on/off" button on the front page to mute it.
