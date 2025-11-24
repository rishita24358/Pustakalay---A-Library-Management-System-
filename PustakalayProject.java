package com.vityarthi.pustakalay;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Pustakalay - Library Management System
 * Single File Submission for VITyarthi Project
 */
public class PustakalayProject {

    // --- MAIN APPLICATION ENTRY POINT ---
    public static void main(String[] args) throws IOException {
        // Initialize Services
        UserService userService = new UserService();
        LibraryService libraryService = new LibraryService();
        TransactionService transactionService = new TransactionService(libraryService);

        // Start API Server in a separate thread
        new Thread(() -> {
            try {
                startAPIServer(userService, libraryService, transactionService);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Start CLI
        startCLI(userService, libraryService, transactionService);
    }

    // --- CLI LOGIC ---
    private static void startCLI(UserService userService, LibraryService libraryService,
            TransactionService transactionService) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Welcome to Pustakalay - Library Management System (CLI)");
        System.out.println("API Server running on http://localhost:8080");

        while (true) {
            if (userService.getCurrentUser() == null) {
                System.out.println("\n--- Login Menu ---");
                System.out.println("1. Login");
                System.out.println("2. Exit");
                System.out.print("Enter choice: ");
                String choice = scanner.nextLine();

                if ("1".equals(choice)) {
                    System.out.print("Enter User ID: ");
                    String userId = scanner.nextLine();
                    System.out.print("Enter Password: ");
                    String password = scanner.nextLine();
                    User user = userService.login(userId, password);
                    if (user != null)
                        System.out.println("Login successful! Welcome, " + user.getName());
                    else
                        System.out.println("Invalid credentials.");
                } else if ("2".equals(choice)) {
                    System.exit(0);
                }
            } else {
                System.out.println("\n--- Main Menu ---");
                System.out.println("1. Search Books");
                System.out.println("2. Issue Book");
                System.out.println("3. Return Book");
                System.out.println("0. Logout");
                System.out.print("Enter choice: ");
                String choice = scanner.nextLine();

                if ("1".equals(choice)) {
                    System.out.print("Search query: ");
                    libraryService.searchBooks(scanner.nextLine()).forEach(System.out::println);
                } else if ("2".equals(choice)) {
                    System.out.print("Book ID: ");
                    System.out.println(
                            transactionService.issueBook(userService.getCurrentUser().getUserId(), scanner.nextLine()));
                } else if ("3".equals(choice)) {
                    System.out.print("Book ID: ");
                    System.out.println(transactionService.returnBook(scanner.nextLine()));
                } else if ("0".equals(choice)) {
                    userService.logout();
                }
            }
        }
    }

    // --- API SERVER LOGIC ---
    private static void startAPIServer(UserService userService, LibraryService libraryService,
            TransactionService transactionService) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/api/books", exchange -> {
            handleCors(exchange);
            if ("GET".equals(exchange.getRequestMethod())) {
                String response = toJson(libraryService.getAllBooks());
                sendResponse(exchange, 200, response);
            } else {
                sendResponse(exchange, 405, "Method Not Allowed");
            }
        });

        server.createContext("/api/login", exchange -> {
            handleCors(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String userId = extractJsonValue(body, "userId");
                String password = extractJsonValue(body, "password");
                User user = userService.login(userId, password);
                if (user != null) {
                    String response = "{\"status\":\"success\", \"user\":{\"name\":\"" + user.getName()
                            + "\", \"role\":\"" + user.getRole() + "\", \"id\":\"" + user.getUserId() + "\"}}";
                    sendResponse(exchange, 200, response);
                } else {
                    sendResponse(exchange, 401, "{\"status\":\"error\"}");
                }
            }
        });

        server.createContext("/api/register", exchange -> {
            handleCors(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String userId = extractJsonValue(body, "userId");
                String name = extractJsonValue(body, "name");
                String password = extractJsonValue(body, "password");
                String role = extractJsonValue(body, "role");
                if (role.isEmpty())
                    role = "STUDENT";

                userService.registerUser(new User(userId, name, role, password));
                sendResponse(exchange, 200, "{\"status\":\"success\"}");
            }
        });

        server.createContext("/api/issue", exchange -> {
            handleCors(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String result = transactionService.issueBook(extractJsonValue(body, "userId"),
                        extractJsonValue(body, "bookId"));
                String status = result.contains("success") ? "success" : "error";
                sendResponse(exchange, 200, "{\"status\":\"" + status + "\", \"message\":\"" + result + "\"}");
            }
        });

        server.createContext("/api/return", exchange -> {
            handleCors(exchange);
            if ("POST".equals(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String result = transactionService.returnBook(extractJsonValue(body, "bookId"));
                String status = result.contains("success") ? "success" : "error";
                sendResponse(exchange, 200, "{\"status\":\"" + status + "\", \"message\":\"" + result + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
    }

    // --- UTILITIES ---
    private static void handleCors(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String toJson(List<Book> books) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < books.size(); i++) {
            Book b = books.get(i);
            sb.append("{")
                    .append("\"bookId\":\"").append(b.getBookId()).append("\",")
                    .append("\"title\":\"").append(b.getTitle()).append("\",")
                    .append("\"author\":\"").append(b.getAuthor()).append("\",")
                    .append("\"genre\":\"").append(b.getGenre()).append("\",")
                    .append("\"isAvailable\":").append(b.isAvailable())
                    .append("}");
            if (i < books.size() - 1)
                sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start == -1)
            return "";
        start += search.length();
        char firstChar = json.charAt(start);
        while (firstChar == ' ' || firstChar == '"') {
            start++;
            firstChar = json.charAt(start);
        }
        int end = json.indexOf("\"", start);
        if (end == -1)
            end = json.indexOf("}", start);
        return json.substring(start, end);
    }

    // --- MODELS & SERVICES (INNER CLASSES) ---

    static class Book {
        private String bookId;
        private String title;
        private String author;
        private String genre;
        private boolean isAvailable;

        public Book(String bookId, String title, String author, String genre, boolean isAvailable) {
            this.bookId = bookId;
            this.title = title;
            this.author = author;
            this.genre = genre;
            this.isAvailable = isAvailable;
        }

        public String getBookId() {
            return bookId;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getGenre() {
            return genre;
        }

        public boolean isAvailable() {
            return isAvailable;
        }

        public void setAvailable(boolean available) {
            isAvailable = available;
        }

        @Override
        public String toString() {
            return "Book{" + "ID='" + bookId + '\'' + ", Title='" + title + '\'' + ", Available=" + isAvailable + '}';
        }
    }

    static class User {
        private String userId;
        private String name;
        private String role;
        private String password;

        public User(String userId, String name, String role, String password) {
            this.userId = userId;
            this.name = name;
            this.role = role;
            this.password = password;
        }

        public String getUserId() {
            return userId;
        }

        public String getName() {
            return name;
        }

        public String getRole() {
            return role;
        }

        public boolean checkPassword(String input) {
            return this.password.equals(input);
        }
    }

    static class Transaction {
        private String transactionId;
        private String userId;
        private String bookId;
        private LocalDate issueDate;
        private LocalDate returnDate;
        private String status;

        public Transaction(String transactionId, String userId, String bookId, LocalDate issueDate) {
            this.transactionId = transactionId;
            this.userId = userId;
            this.bookId = bookId;
            this.issueDate = issueDate;
            this.status = "ISSUED";
        }

        public String getBookId() {
            return bookId;
        }

        public String getUserId() {
            return userId;
        }

        public String getStatus() {
            return status;
        }

        public void setReturnDate(LocalDate returnDate) {
            this.returnDate = returnDate;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

    static class LibraryService {
        private List<Book> books;

        public LibraryService() {
            this.books = new ArrayList<>();
            books.add(new Book("B001", "Wings of Fire", "A.P.J. Abdul Kalam", "Autobiography", true));
            books.add(new Book("B002", "The White Tiger", "Aravind Adiga", "Fiction", true));
            books.add(new Book("B003", "Malgudi Days", "R.K. Narayan", "Short Stories", true));
            books.add(new Book("B004", "The God of Small Things", "Arundhati Roy", "Drama", true));
            books.add(new Book("B005", "Train to Pakistan", "Khushwant Singh", "Historical Fiction", true));
        }

        public void addBook(Book book) {
            books.add(book);
        }

        public boolean removeBook(String bookId) {
            return books.removeIf(b -> b.getBookId().equals(bookId));
        }

        public Optional<Book> findBookById(String bookId) {
            return books.stream().filter(b -> b.getBookId().equals(bookId)).findFirst();
        }

        public List<Book> searchBooks(String query) {
            String q = query.toLowerCase();
            return books.stream()
                    .filter(b -> b.getTitle().toLowerCase().contains(q) || b.getAuthor().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        public List<Book> getAllBooks() {
            return new ArrayList<>(books);
        }
    }

    static class UserService {
        private List<User> users;
        private User currentUser;

        public UserService() {
            this.users = new ArrayList<>();
            users.add(new User("A001", "Admin User", "ADMIN", "admin123"));
            users.add(new User("S001", "John Doe", "STUDENT", "student123"));
        }

        public void registerUser(User user) {
            users.add(user);
        }

        public User login(String userId, String password) {
            Optional<User> u = users.stream()
                    .filter(user -> user.getUserId().equals(userId) && user.checkPassword(password)).findFirst();
            if (u.isPresent()) {
                currentUser = u.get();
                return currentUser;
            }
            return null;
        }

        public void logout() {
            currentUser = null;
        }

        public User getCurrentUser() {
            return currentUser;
        }
    }

    static class TransactionService {
        private List<Transaction> transactions;
        private LibraryService libraryService;

        public TransactionService(LibraryService libraryService) {
            this.transactions = new ArrayList<>();
            this.libraryService = libraryService;
        }

        public String issueBook(String userId, String bookId) {
            Book book = libraryService.findBookById(bookId).orElse(null);
            if (book == null)
                return "Book not found.";
            if (!book.isAvailable())
                return "Book is currently unavailable.";
            String tId = UUID.randomUUID().toString().substring(0, 8);
            transactions.add(new Transaction(tId, userId, bookId, LocalDate.now()));
            book.setAvailable(false);
            return "Book issued successfully! Transaction ID: " + tId;
        }

        public String returnBook(String bookId) {
            Transaction t = transactions.stream()
                    .filter(tr -> tr.getBookId().equals(bookId) && "ISSUED".equals(tr.getStatus())).findFirst()
                    .orElse(null);
            if (t == null)
                return "No active transaction found.";
            t.setReturnDate(LocalDate.now());
            t.setStatus("RETURNED");
            libraryService.findBookById(bookId).ifPresent(b -> b.setAvailable(true));
            return "Book returned successfully.";
        }
    }
}
