package application;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;


import java.sql.*;

public class Controller {
    @FXML
    private TableView<Book> tableView;
    @FXML
    private TableColumn<Book, String> titleColumn;
    @FXML
    private TableColumn<Book, String> authorColumn;
    @FXML
    private TableColumn<Book, String> isbnColumn;
    @FXML
    private TableColumn<Book, Boolean> availabilityColumn;
    @FXML
    private TextField searchField;
    @FXML
    private Label statusBar;

    private ObservableList<Book> bookList = FXCollections.observableArrayList();

    
    public static class DBUtil {
        private static final String JDBC_DRIVER = "com.mysql.cj.jdbc.Driver";
        private static final String DB_URL = "jdbc:mysql://localhost:3306/librarysystem";
        private static final String USER = "root";
        private static final String PASS = "i2269910$";

        public static Connection getConnection() throws ClassNotFoundException, SQLException {
            Class.forName(JDBC_DRIVER);
            return DriverManager.getConnection(DB_URL, USER, PASS);
        }
    }

    // Book class
    public static class Book {
        private final SimpleStringProperty title;
        private final SimpleStringProperty author;
        private final SimpleStringProperty isbn;
        private final SimpleBooleanProperty available;

        public Book(String title, String author, String isbn, boolean available) {
            this.title = new SimpleStringProperty(title);
            this.author = new SimpleStringProperty(author);
            this.isbn = new SimpleStringProperty(isbn);
            this.available = new SimpleBooleanProperty(available);
        }

        public String getTitle() { return title.get(); }
        public void setTitle(String title) { this.title.set(title); }
        public String getAuthor() { return author.get(); }
        public void setAuthor(String author) { this.author.set(author); }
        public String getIsbn() { return isbn.get(); }
        public void setIsbn(String isbn) { this.isbn.set(isbn); }
        public boolean isAvailable() { return available.get(); }
        public void setAvailable(boolean available) { this.available.set(available); }
    }

    @FXML
    public void initialize() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        authorColumn.setCellValueFactory(new PropertyValueFactory<>("author"));
        isbnColumn.setCellValueFactory(new PropertyValueFactory<>("isbn"));
        availabilityColumn.setCellValueFactory(new PropertyValueFactory<>("available"));

        loadBooks();

        FilteredList<Book> filteredList = new FilteredList<>(bookList, b -> true);
        SortedList<Book> sortedList = new SortedList<>(filteredList);
        sortedList.comparatorProperty().bind(tableView.comparatorProperty());
        tableView.setItems(sortedList);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(book -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                String lowerCaseFilter = newValue.toLowerCase();
                return book.getTitle().toLowerCase().contains(lowerCaseFilter) ||
                        book.getAuthor().toLowerCase().contains(lowerCaseFilter);
            });
        });
    }

    private void loadBooks() {
        bookList.clear();
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM book");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                bookList.add(new Book(rs.getString("title"), rs.getString("author"), rs.getString("isbn"), rs.getBoolean("available")));
            }
        } catch (SQLException | ClassNotFoundException e) {
            statusBar.setText("Error loading books from database.");
            e.printStackTrace();
        }
    }

   
    @FXML
    private TextField titleField, authorField, isbnField; 
    @FXML
    private CheckBox availabilityCheckBox; 

    @FXML
    private void addBook() {
        String title = titleField.getText();
        String author = authorField.getText();
        String isbn = isbnField.getText();
        boolean available = availabilityCheckBox.isSelected();

        if (title.isEmpty() || author.isEmpty()) {
            statusBar.setText("Title and Author fields cannot be empty.");
            return;}

        if (!isbn.matches("^(?:ISBN(?:-1[03])?:? )?(?=[0-9X]{10}$|(?=(?:[0-9]+[- ]){3})[- 0-9X]{13}$|97[89][0-9]{10}$|(?=(?:[0-9]+[- ]){4})[- 0-9]{17}$)(?:97[89][- ]?)?[0-9]{1,5}[- ]?[0-9]+[- ]?[0-9]+[- ]?[0-9X]$")) {
            statusBar.setText("Invalid ISBN format.");
            return; }

        String query = "INSERT INTO book (title, author, isbn, available) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, title);
            pstmt.setString(2, author);
            pstmt.setString(3, isbn);
            pstmt.setBoolean(4, available);
            pstmt.executeUpdate();
            statusBar.setText("Book added successfully.");
            loadBooks();
        } catch (SQLException | ClassNotFoundException e) {
            statusBar.setText("Error adding book to database.");
            e.printStackTrace();
        }
    }


    @FXML
    private void editBook() {
        Book selectedBook = tableView.getSelectionModel().getSelectedItem();
        if (selectedBook != null) {
            String title = titleField.getText().trim();
            String author = authorField.getText().trim();
            String isbn = isbnField.getText().trim();
            boolean available = availabilityCheckBox.isSelected();

            if (title.isEmpty() || author.isEmpty()) {
                statusBar.setText("Title and Author fields cannot be empty.");
                return; 
            }

            if (!isbn.matches("\\d{13}")) { 
                statusBar.setText("ISBN should be a 13-digit number.");
                return;
            }

            String query = "UPDATE book SET title = ?, author = ?, available = ? WHERE isbn = ?";
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, title);
                pstmt.setString(2, author);
                pstmt.setBoolean(3, available);
                pstmt.setString(4, selectedBook.getIsbn());
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    selectedBook.setTitle(title);
                    selectedBook.setAuthor(author);
                    selectedBook.setIsbn(isbn); 
                    selectedBook.setAvailable(available);
                    tableView.refresh(); 
                    statusBar.setText("Book updated successfully.");
                } else {
                    statusBar.setText("No changes were made to the book.");
                }
            } catch (SQLException | ClassNotFoundException e) {
                statusBar.setText("Error updating book in database.");
                e.printStackTrace();
            }
        } else {
            statusBar.setText("No book selected to edit.");
        }
    }

    @FXML
    private void deleteBook() {
        Book selectedBook = tableView.getSelectionModel().getSelectedItem();
        if (selectedBook != null) {
            String query = "DELETE FROM book WHERE isbn = ?";
            try (Connection conn = DBUtil.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, selectedBook.getIsbn());
                pstmt.executeUpdate();
                statusBar.setText("Book deleted successfully.");
                loadBooks(); 
            } catch (SQLException | ClassNotFoundException e) {
                statusBar.setText("Error deleting book from database.");
                e.printStackTrace();
            }
        } else {
            statusBar.setText("NO BOOK SELECTED.");
        }
    }
    @FXML
    private void viewAllBooks() {
        loadBooks();
        statusBar.setText("VIEWING ALL BOOKS.");
    }

    @FXML
    private void searchBooks() {
        String searchText = searchField.getText();
        if (searchText == null || searchText.isEmpty()) {
            tableView.setItems(bookList); 
            statusBar.setText("SHOWING ALL BOOKS.");
        } else {
            FilteredList<Book> filteredList = new FilteredList<>(bookList, book -> {
                String lowerCaseFilter = searchText.toLowerCase();
                return book.getTitle().toLowerCase().contains(lowerCaseFilter) ||
                        book.getAuthor().toLowerCase().contains(lowerCaseFilter);
            });
            SortedList<Book> sortedList = new SortedList<>(filteredList);
            sortedList.comparatorProperty().bind(tableView.comparatorProperty());
            tableView.setItems(sortedList);
            statusBar.setText("DISPLAYING SEARCH RESULTS.");
        }
    }

	
    private void saveCurrentStateToFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Current Books");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(null); 
        if (file != null) {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                for (Book book : bookList) {
                    writer.write(book.getTitle() + "," + book.getAuthor() + "," + book.getIsbn() + "," + book.isAvailable());
                    writer.newLine();
                }
                statusBar.setText("Current books saved successfully.");
            } catch (IOException e) {
                statusBar.setText("Failed to save current books.");
                e.printStackTrace();
            }
        }
    }
    @FXML
    private void newBook() {
        saveCurrentStateToFile(); 
        // Clear the database
        String query = "DELETE FROM book";
        try (Connection conn = DBUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.executeUpdate();
            bookList.clear(); 
            tableView.getItems().clear(); 
            statusBar.setText("All books have been cleared from the database.");
        } catch (SQLException | ClassNotFoundException e) {
            statusBar.setText("Error clearing books from database.");
            e.printStackTrace();
        }

        titleField.clear();
        authorField.clear();
        isbnField.clear();
        availabilityCheckBox.setSelected(false);
        tableView.getSelectionModel().clearSelection();
        statusBar.setText("Ready to add a new book.");
    }
	
    @FXML
    private void openBook() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Book File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile != null) {
            loadBooksFromFile(selectedFile);
        }
    }

    private void loadBooksFromFile(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file));
             Connection conn = DBUtil.getConnection()) { 
            String line;
            String query = "INSERT INTO book (title, author, isbn, available) VALUES (?, ?, ?, ?)";
            PreparedStatement pstmt = conn.prepareStatement(query);

            while ((line = reader.readLine()) != null) {
                String[] bookData = line.split(",");
                if (bookData.length == 4) {
                    String title = bookData[0];
                    String author = bookData[1];
                    String isbn = bookData[2];
                    boolean available = Boolean.parseBoolean(bookData[3]);

                    pstmt.setString(1, title);
                    pstmt.setString(2, author);
                    pstmt.setString(3, isbn);
                    pstmt.setBoolean(4, available);
                    pstmt.executeUpdate();

                    
                    bookList.add(new Book(title, author, isbn, available));
                }
            }
            statusBar.setText("Books loaded from file and saved to database successfully.");
        } catch (IOException e) {
            statusBar.setText("Error loading books from file.");
            e.printStackTrace();
        } catch (SQLException | ClassNotFoundException e) {
            statusBar.setText("Error inserting books into database.");
            e.printStackTrace();
        }
    }
    
    
    
	

    private String bookDataFilePath = "books.txt"; 

    private File currentFile = null; 

    @FXML
    private void saveBook() {
        if (currentFile == null) {
            saveAsBook(); 
        } else {
            saveBooksToFile(currentFile);
        }
    }

    @FXML
    private void saveAsBook() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save As");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Text Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        Window stage = tableView.getScene().getWindow(); 
        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            saveBooksToFile(file);
            currentFile = file; 
        }
    }

    private void saveBooksToFile(File file) {
        try (FileWriter fileWriter = new FileWriter(file)) {
            for (Book book : bookList) {
                fileWriter.write(book.getTitle() + "," + book.getAuthor() + "," + book.getIsbn() + "," + book.isAvailable() + "\n");
            }
            statusBar.setText("Books saved successfully to " + file.getName());
        } catch (IOException e) {
            statusBar.setText("Failed to save books.");
            e.printStackTrace();
        }
    }

    
    

    @FXML
    private void exitApplication() {
        System.exit(0);
    }
}