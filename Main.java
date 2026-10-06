import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

// Custom Exceptions
class BookNotAvailableException extends Exception {
    public BookNotAvailableException(String message) { super(message); }
}

class TransactionNotFoundException extends Exception {
    public TransactionNotFoundException(String message) { super(message); }
}

// Domain Model
class Book {
    private int id;
    private String title;
    private String author;
    private String isbn;
    private String genre;
    private int copies;

    public Book(int id, String title, String author, String isbn, String genre, int copies) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.isbn = isbn;
        this.genre = genre;
        this.copies = copies;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public String getGenre() { return genre; }
    public int getCopies() { return copies; }
}

// User Classes (OOP)
abstract class User {
    protected int id;
    protected String username;
    protected String role;

    public User(int id, String username, String role) {
        this.id = id;
        this.username = username;
        this.role = role;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public abstract void displayRoleDetails();
}

class Librarian extends User {
    public Librarian(int id, String username) { super(id, username, "LIBRARIAN"); }
    @Override
    public void displayRoleDetails() { System.out.println("Role: Librarian"); }
}

class Member extends User {
    public Member(int id, String username) { super(id, username, "MEMBER"); }
    @Override
    public void displayRoleDetails() { System.out.println("Role: Member"); }
}

// Database Helper
class DatabaseHelper {
    private static final String URL = "jdbc:sqlite:library.db";

    public static Connection connect() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            JOptionPane.showMessageDialog(null, 
                "SQLite JDBC Driver not found in Classpath!\nMake sure you run with: java -cp \".;sqlite-jdbc-3.45.1.0.jar\" Main", 
                "Driver Error", JOptionPane.ERROR_MESSAGE);
        }
        return DriverManager.getConnection(URL);
    }

    public static void initializeDatabase() {
        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS books (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "title TEXT, author TEXT, isbn TEXT UNIQUE, genre TEXT, copies INTEGER)");

            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "book_id INTEGER, user_id INTEGER, borrow_date TEXT, return_date TEXT, status TEXT)");

            // Pre-seed catalog if empty
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM books");
            if (rs.next() && rs.getInt("total") == 0) {
                stmt.execute("INSERT INTO books (title, author, isbn, genre, copies) VALUES " +
                        "('Clean Code', 'Robert Martin', '9780132350884', 'Technical', 3)," +
                        "('Design Patterns', 'Erich Gamma', '9780201633610', 'Architecture', 2)," +
                        "('Operating Systems', 'Silberschatz', '9781118063330', 'Core CS', 5)");
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(null, "DB Initialization Error: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}

// Service Interface & Implementation
interface ILibraryService {
    void borrowBook(int bookId, int userId) throws BookNotAvailableException, SQLException;
    void returnBook(int txId, int bookId) throws TransactionNotFoundException, SQLException;
}

class LibraryService implements ILibraryService {
    private static final ReentrantLock checkoutLock = new ReentrantLock();
    private static final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void borrowBook(int bookId, int userId) throws BookNotAvailableException, SQLException {
        checkoutLock.lock();
        try (Connection conn = DatabaseHelper.connect()) {
            conn.setAutoCommit(false);
            
            PreparedStatement getBook = conn.prepareStatement("SELECT copies FROM books WHERE id = ?");
            getBook.setInt(1, bookId);
            ResultSet rs = getBook.executeQuery();

            if (rs.next() && rs.getInt("copies") > 0) {
                int updatedCopies = rs.getInt("copies") - 1;

                PreparedStatement updateBook = conn.prepareStatement("UPDATE books SET copies = ? WHERE id = ?");
                updateBook.setInt(1, updatedCopies);
                updateBook.setInt(2, bookId);
                updateBook.executeUpdate();

                PreparedStatement insertTx = conn.prepareStatement(
                        "INSERT INTO transactions (book_id, user_id, borrow_date, return_date, status) VALUES (?, ?, ?, ?, ?)");
                insertTx.setInt(1, bookId);
                insertTx.setInt(2, userId);
                insertTx.setString(3, LocalDateTime.now().format(dtf));
                insertTx.setString(4, "N/A");
                insertTx.setString(5, "BORROWED");
                insertTx.executeUpdate();

                conn.commit();
            } else {
                conn.rollback();
                throw new BookNotAvailableException("No copies available for checkout.");
            }
        } finally {
            checkoutLock.unlock();
        }
    }

    @Override
    public void returnBook(int txId, int bookId) throws TransactionNotFoundException, SQLException {
        checkoutLock.lock();
        try (Connection conn = DatabaseHelper.connect()) {
            conn.setAutoCommit(false);

            PreparedStatement getTx = conn.prepareStatement("SELECT status FROM transactions WHERE id = ?");
            getTx.setInt(1, txId);
            ResultSet rs = getTx.executeQuery();

            if (rs.next() && "BORROWED".equals(rs.getString("status"))) {
                PreparedStatement updateTx = conn.prepareStatement(
                        "UPDATE transactions SET status = 'RETURNED', return_date = ? WHERE id = ?");
                updateTx.setString(1, LocalDateTime.now().format(dtf));
                updateTx.setInt(2, txId);
                updateTx.executeUpdate();

                PreparedStatement updateBook = conn.prepareStatement("UPDATE books SET copies = copies + 1 WHERE id = ?");
                updateBook.setInt(1, bookId);
                updateBook.executeUpdate();

                conn.commit();
            } else {
                conn.rollback();
                throw new TransactionNotFoundException("Active borrowed record not found.");
            }
        } finally {
            checkoutLock.unlock();
        }
    }
}

// GUI Frontend
public class Main extends JFrame {
    private final ILibraryService libraryService = new LibraryService();
    private DefaultTableModel catalogModel;
    private JTable catalogTable;
    private User currentUser;

    public Main() {
        setTitle("Campus Library Management System");
        setSize(850, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        showLoginDialog();
        initUI();
        refreshCatalogTable();
    }

    private void showLoginDialog() {
        String[] options = {"Librarian (Admin)", "Member (Student)"};
        int roleChoice = JOptionPane.showOptionDialog(
                null, "Select User Role to Login:", "Authentication",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (roleChoice == 0) {
            currentUser = new Librarian(1, "admin");
        } else {
            currentUser = new Member(2, "student1");
        }
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.add(new JLabel("Active User: " + currentUser.getUsername() + " | Role: " + currentUser.getRole()));
        add(headerPanel, BorderLayout.NORTH);

        catalogModel = new DefaultTableModel(new String[]{"ID", "Title", "Author", "ISBN", "Genre", "Copies Available"}, 0);
        catalogTable = new JTable(catalogModel);
        add(new JScrollPane(catalogTable), BorderLayout.CENTER);

        JPanel controlPanel = new JPanel();
        JButton btnBorrow = new JButton("Borrow Book");
        JButton btnReturn = new JButton("Return Book");
        JButton btnRefresh = new JButton("Refresh");

        controlPanel.add(btnBorrow);
        controlPanel.add(btnReturn);
        controlPanel.add(btnRefresh);

        // ONLY LIBRARIANS SEE ADD BOOK
        if ("LIBRARIAN".equals(currentUser.getRole())) {
            JButton btnAdd = new JButton("Add Book (Admin)");
            btnAdd.addActionListener(e -> addBookDialog());
            controlPanel.add(btnAdd);
        }

        add(controlPanel, BorderLayout.SOUTH);

        btnRefresh.addActionListener(e -> refreshCatalogTable());

        btnBorrow.addActionListener(e -> {
            int selectedRow = catalogTable.getSelectedRow();
            if (selectedRow == -1) {
                JOptionPane.showMessageDialog(this, "Please click and select a book from the table first.");
                return;
            }
            int bookId = (int) catalogModel.getValueAt(selectedRow, 0);

            new Thread(() -> {
                try {
                    libraryService.borrowBook(bookId, currentUser.getId());
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "Book borrowed successfully!");
                        refreshCatalogTable();
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE));
                }
            }).start();
        });

        btnReturn.addActionListener(e -> {
            String txInput = JOptionPane.showInputDialog(this, "Enter Transaction ID:");
            String bookInput = JOptionPane.showInputDialog(this, "Enter Book ID:");
            if (txInput != null && bookInput != null) {
                try {
                    int txId = Integer.parseInt(txInput.trim());
                    int bookId = Integer.parseInt(bookInput.trim());
                    libraryService.returnBook(txId, bookId);
                    JOptionPane.showMessageDialog(this, "Book returned successfully!");
                    refreshCatalogTable();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void addBookDialog() {
        JTextField titleField = new JTextField();
        JTextField authorField = new JTextField();
        JTextField isbnField = new JTextField();
        JTextField genreField = new JTextField();
        JTextField copiesField = new JTextField();

        Object[] fields = {
                "Title:", titleField,
                "Author:", authorField,
                "ISBN:", isbnField,
                "Genre:", genreField,
                "Copies:", copiesField
        };

        int option = JOptionPane.showConfirmDialog(this, fields, "Add Book to Catalog", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            try (Connection conn = DatabaseHelper.connect()) {
                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO books (title, author, isbn, genre, copies) VALUES (?, ?, ?, ?, ?)");
                stmt.setString(1, titleField.getText().trim());
                stmt.setString(2, authorField.getText().trim());
                stmt.setString(3, isbnField.getText().trim());
                stmt.setString(4, genreField.getText().trim());
                stmt.setInt(5, Integer.parseInt(copiesField.getText().trim()));
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Book added successfully!");
                refreshCatalogTable();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to insert book: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public void refreshCatalogTable() {
        catalogModel.setRowCount(0);
        try (Connection conn = DatabaseHelper.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM books")) {

            while (rs.next()) {
                catalogModel.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getString("isbn"),
                        rs.getString("genre"),
                        rs.getInt("copies")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to load catalog: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void main(String[] args) {
        DatabaseHelper.initializeDatabase();
        SwingUtilities.invokeLater(() -> new Main().setVisible(true));
    }
}