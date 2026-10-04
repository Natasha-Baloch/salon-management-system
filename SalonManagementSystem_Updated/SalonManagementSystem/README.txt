SALON MANAGEMENT SYSTEM - Java Swing + MySQL

REQUIREMENTS
1. JDK 17 or newer
2. MySQL Server
3. VS Code with Extension Pack for Java
4. Maven (or use the Maven support in VS Code)

DATABASE SETUP
1. Start MySQL.
2. Open MySQL Workbench or MySQL command line.
3. Run the SQL in database.sql.
4. Open src/main/java/com/salon/DB.java.
5. Change DB_USER and DB_PASSWORD to your MySQL username/password.

RUN IN VS CODE
1. Open this SalonManagementSystem folder.
2. Make sure Java 17+ is installed.
3. Let Maven download mysql-connector-j.
4. Run:
   mvn clean compile exec:java

MAIN FEATURES
- Colorful Java Swing GUI
- Dashboard with revenue/booking graph
- Service catalog
- Hair Cut, Hair Color, Facial, Party Ready, Wedding Ready,
  Bridal Package, Makeup, Manicure, Pedicure and Spa
- New client + booking
- Date and time for every client
- Booking status: BOOKED / READY / COMPLETED / CANCELLED
- Complete booking when the client is ready
- First payment is recorded when a booking is completed
- Completed booking disappears from the active booking table
- Paid amount is added to the account/revenue
- Payment history
- Active client booking list in the Clients tab
- Select a client and Complete & Pay or Cancel directly from Clients
- Completed/cancelled clients automatically disappear from the active Clients list
- Completed payments remain saved in Payments and revenue totals
- MySQL database storage

IMPORTANT
The program creates the database tables automatically after the database
"salon_db" exists. You can also run database.sql manually.


UPDATED UI
The booking form now uses high-contrast text, visible fields, stronger borders,
clear labels, readable table text, and automatic current date as the default booking date.
You still enter the client name, phone, service, date and time, then click BOOK CLIENT.


CLIENT TAB WORKFLOW
1. Book a client from Book Client.
2. Open the Clients tab.
3. Select the active client/booking row.
4. Click COMPLETE & PAY, choose the payment method, and confirm.
   The payment is saved, the booking becomes COMPLETED, and the row disappears from Clients.
5. Or click CANCEL BOOKING and confirm. No payment is created, the booking becomes CANCELLED,
   and the row disappears from Clients.
6. Completed payments can be viewed in the Payments tab.
