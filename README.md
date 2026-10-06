# Campus Library Management System

A desktop-based Library Management System built with **Java Swing**, **SQLite JDBC**, and **Multithreading** to manage book cataloging, circulation workflows, and role-based actions.

---

## 📌 Project Overview

The system provides a graphical interface with role-based access for two user groups:

- **Librarian (Admin)**: Add books to catalog, view all inventory, monitor circulation.
- **Member (Student)**: Browse book catalog, borrow available copies, return borrowed books, track status.

---

## 🛠️ Tech Stack & Key Features

- **Language**: Java (JDK 17+)
- **GUI Framework**: Java Swing (`JFrame`, `JTable`, `DefaultTableModel`, `JOptionPane`)
- **Database**: SQLite 3 embedded database (`library.db`) via native JDBC
- **Concurrency**: `ReentrantLock` for thread-safe inventory decrementing during concurrent checkouts
- **Design Patterns**: MVC-inspired modular architecture, Data Access Object (DAO) pattern

---

## 🏗️ Evaluation Rubric Compliance

- **Object-Oriented Programming (OOP)**: Abstract class `User` extended by `Librarian` and `Member`; polymorphic role handling; custom exceptions (`BookNotAvailableException`, `TransactionNotFoundException`).
- **Collections & Generics**: Strong typing using generic collections (`List<Book>`, `Map<Integer, Book>`).
- **Multithreading & Synchronization**: Worker threads for non-blocking UI operations and `ReentrantLock` around critical stock updates.
- **Database Connectivity (JDBC)**: Parameterized queries via `PreparedStatement`, transactional commit/rollback handling, auto-schema initialization.

---

## 🚀 How to Run Locally

### Prerequisites

- Java Development Kit (JDK 17 or higher)
- SQLite JDBC Driver (`sqlite.jar` or `sqlite-jdbc-3.45.1.0.jar`)

### Compilation & Execution

Open terminal in the project directory:

```bash
# Compile
javac -cp ".;sqlite.jar" Main.java

# Run
java -cp ".;sqlite.jar" Main
```
