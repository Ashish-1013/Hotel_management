import java.sql.*;
import java.util.Scanner;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class HotelReservationSystem {
    private static final String URL = "jdbc:mysql://localhost:3306/hotel_db?useSSL=false&serverTimezone=UTC";
    private static final String USERNAME = "root";
    private static final String PASSWORD = "Ashish@10";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static void main(String[] args) {
        // Load MySQL JDBC driver
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("MySQL JDBC Driver loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("\nERROR: MySQL JDBC Driver not found!");
            System.err.println("Download the MySQL Connector/J from:");
            System.err.println("https://dev.mysql.com/downloads/connector/j/");
            System.err.println("Add the JAR file to your project's classpath\n");
            System.exit(1);
        }

        // Initialize database
        initializeDatabase();

        // Main application loop
        try (Connection connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
             Scanner scanner = new Scanner(System.in)) {
            
            System.out.println("\n=== HOTEL RESERVATION SYSTEM ===");
            System.out.println("Connected to database successfully!");

            while (true) {
                displayMainMenu();
                int choice = getIntInput(scanner, "Choose an option: ");
                
                switch (choice) {
                    case 1:
                        reserveRoom(connection, scanner);
                        break;
                    case 2:
                        viewReservations(connection);
                        break;
                    case 3:
                        getRoomNumber(connection, scanner);
                        break;
                    case 4:
                        updateReservation(connection, scanner);
                        break;
                    case 5:
                        deleteReservation(connection, scanner);
                        break;
                    case 6:
                        viewAvailableRooms(connection);
                        break;
                    case 7:
                        viewRoomDetails(connection, scanner);
                        break;
                    case 0:
                        exit();
                        return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
        }
    }

    private static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/?useSSL=false", USERNAME, PASSWORD);
            Statement stmt = conn.createStatement()) {
            
            // Create database if not exists
            stmt.execute("CREATE DATABASE IF NOT EXISTS hotel_db");
            stmt.execute("USE hotel_db");
            
            // Create reservations table
            String createReservationsTable = "CREATE TABLE IF NOT EXISTS reservations (" +
                "reservation_id INT AUTO_INCREMENT PRIMARY KEY, " +
                "guest_name VARCHAR(100) NOT NULL, " +
                "room_number INT NOT NULL, " +
                "contact_number VARCHAR(20) NOT NULL, " +
                "reservation_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "check_in_date DATE NOT NULL, " +
                "check_out_date DATE NOT NULL, " +
                "status VARCHAR(20) DEFAULT 'CONFIRMED', " +
                "FOREIGN KEY (room_number) REFERENCES rooms(room_number))";
            stmt.execute(createReservationsTable);
            
            // Create rooms table
            String createRoomsTable = "CREATE TABLE IF NOT EXISTS rooms (" +
                "room_number INT PRIMARY KEY, " +
                "room_type VARCHAR(50) NOT NULL, " +
                "price_per_night DECIMAL(10,2) NOT NULL, " +
                "max_occupancy INT NOT NULL, " +
                "amenities VARCHAR(200), " +
                "status VARCHAR(20) DEFAULT 'AVAILABLE')";
            stmt.execute(createRoomsTable);
            
            // Insert sample room data if table is empty
            if (!tableHasData(conn, "rooms")) {
                String insertRooms = "INSERT INTO rooms (room_number, room_type, price_per_night, max_occupancy, amenities) VALUES " +
                    "(101, 'Standard', 99.99, 2, 'TV, WiFi, AC'), " +
                    "(102, 'Standard', 99.99, 2, 'TV, WiFi, AC'), " +
                    "(201, 'Deluxe', 149.99, 3, 'TV, WiFi, AC, Mini-Bar'), " +
                    "(202, 'Deluxe', 149.99, 3, 'TV, WiFi, AC, Mini-Bar'), " +
                    "(301, 'Suite', 249.99, 4, 'TV, WiFi, AC, Mini-Bar, Jacuzzi')";
                stmt.executeUpdate(insertRooms);
            }
            
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static boolean tableHasData(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private static void displayMainMenu() {
        System.out.println("\n=== MAIN MENU ===");
        System.out.println("1. Reserve a room");
        System.out.println("2. View all reservations");
        System.out.println("3. Find room number by reservation");
        System.out.println("4. Update reservation");
        System.out.println("5. Cancel reservation");
        System.out.println("6. View available rooms");
        System.out.println("7. View room details");
        System.out.println("0. Exit");
    }

    private static void reserveRoom(Connection connection, Scanner scanner) {
        try {
            viewAvailableRooms(connection);
            
            System.out.println("\n=== NEW RESERVATION ===");
            String guestName = getStringInput(scanner, "Enter guest name: ");
            int roomNumber = getIntInput(scanner, "Enter room number: ");
            String contactNumber = getStringInput(scanner, "Enter contact number: ");
            
            LocalDate checkInDate = getValidDateInput(scanner, "Enter check-in date (YYYY-MM-DD): ");
            LocalDate checkOutDate = getValidDateInput(scanner, "Enter check-out date (YYYY-MM-DD): ");
            
            if (checkOutDate.isBefore(checkInDate) || checkOutDate.isEqual(checkInDate)) {
                System.out.println("Error: Check-out date must be after check-in date");
                return;
            }

            if (!isRoomAvailableForDates(connection, roomNumber, checkInDate, checkOutDate)) {
                System.out.println("Room " + roomNumber + " is not available for the selected dates");
                return;
            }

            String sql = "INSERT INTO reservations (guest_name, room_number, contact_number, check_in_date, check_out_date) " +
                         "VALUES (?, ?, ?, ?, ?)";
            
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, guestName);
                statement.setInt(2, roomNumber);
                statement.setString(3, contactNumber);
                statement.setDate(4, Date.valueOf(checkInDate));
                statement.setDate(5, Date.valueOf(checkOutDate));
                
                int affectedRows = statement.executeUpdate();
                
                if (affectedRows > 0) {
                    if (checkInDate.isEqual(LocalDate.now())) {
                        updateRoomStatus(connection, roomNumber, "OCCUPIED");
                    }
                    System.out.println("\nReservation successful!");
                    displayReservationDetails(connection, getLastInsertId(connection));
                } else {
                    System.out.println("Reservation failed.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during reservation: " + e.getMessage());
        }
    }

    private static void viewReservations(Connection connection) {
        String sql = "SELECT r.reservation_id, r.guest_name, r.room_number, rm.room_type, " +
                     "rm.price_per_night, r.contact_number, r.reservation_date, " +
                     "r.check_in_date, r.check_out_date, r.status " +
                     "FROM reservations r JOIN rooms rm ON r.room_number = rm.room_number " +
                     "ORDER BY r.reservation_date DESC";
        
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            
            System.out.println("\n=== CURRENT RESERVATIONS ===");
            System.out.println("+-----+-------------------+--------+------------+-------------+------------------+---------------------+--------------+--------------+-----------+");
            System.out.println("| ID  | Guest Name        | Room   | Room Type  | Price/Night | Contact          | Reservation Date    | Check-In     | Check-Out    | Status    |");
            System.out.println("+-----+-------------------+--------+------------+-------------+------------------+---------------------+--------------+--------------+-----------+");

            while (resultSet.next()) {
                int id = resultSet.getInt("reservation_id");
                String guest = resultSet.getString("guest_name");
                int room = resultSet.getInt("room_number");
                String type = resultSet.getString("room_type");
                double price = resultSet.getDouble("price_per_night");
                String contact = resultSet.getString("contact_number");
                String resDate = resultSet.getTimestamp("reservation_date").toString();
                String checkIn = resultSet.getString("check_in_date");
                String checkOut = resultSet.getString("check_out_date");
                String status = resultSet.getString("status");

                System.out.printf("| %-3d | %-17s | %-6d | %-10s | $%-10.2f | %-16s | %-19s | %-12s | %-12s | %-9s |\n",
                        id, guest, room, type, price, contact, resDate, checkIn, checkOut, status);
            }
            System.out.println("+-----+-------------------+--------+------------+-------------+------------------+---------------------+--------------+--------------+-----------+");
        } catch (SQLException e) {
            System.err.println("Error viewing reservations: " + e.getMessage());
        }
    }

    private static void getRoomNumber(Connection connection, Scanner scanner) {
        int reservationId = getIntInput(scanner, "Enter reservation ID: ");
        String guestName = getStringInput(scanner, "Enter guest name: ");

        String sql = "SELECT r.room_number, rm.room_type, r.check_in_date, r.check_out_date " +
                     "FROM reservations r JOIN rooms rm ON r.room_number = rm.room_number " +
                     "WHERE r.reservation_id = ? AND r.guest_name = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reservationId);
            statement.setString(2, guestName);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    System.out.println("\n=== RESERVATION DETAILS ===");
                    System.out.println("Reservation ID: " + reservationId);
                    System.out.println("Guest Name: " + guestName);
                    System.out.println("Room Number: " + resultSet.getInt("room_number") + 
                                     " (" + resultSet.getString("room_type") + ")");
                    System.out.println("Check-in: " + resultSet.getString("check_in_date"));
                    System.out.println("Check-out: " + resultSet.getString("check_out_date"));
                } else {
                    System.out.println("No reservation found with ID " + reservationId + " for guest " + guestName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving reservation: " + e.getMessage());
        }
    }

    private static void updateReservation(Connection connection, Scanner scanner) {
        int reservationId = getIntInput(scanner, "Enter reservation ID to update: ");
        
        if (!reservationExists(connection, reservationId)) {
            System.out.println("Reservation not found with ID: " + reservationId);
            return;
        }

        System.out.println("\n=== UPDATE RESERVATION ===");
        String newGuestName = getStringInput(scanner, "Enter new guest name (leave blank to keep current): ");
        String newContact = getStringInput(scanner, "Enter new contact number (leave blank to keep current): ");
        String newCheckIn = getStringInput(scanner, "Enter new check-in date (YYYY-MM-DD, leave blank to keep current): ");
        String newCheckOut = getStringInput(scanner, "Enter new check-out date (YYYY-MM-DD, leave blank to keep current): ");

        try {
            // Get current reservation details
            String currentSql = "SELECT room_number, guest_name, contact_number, check_in_date, check_out_date " +
                            "FROM reservations WHERE reservation_id = ?";
            
            int currentRoom = 0;
            String currentGuest = "";
            String currentContact = "";
            String currentCheckIn = "";
            String currentCheckOut = "";
            
            try (PreparedStatement currentStmt = connection.prepareStatement(currentSql)) {
                currentStmt.setInt(1, reservationId);
                try (ResultSet rs = currentStmt.executeQuery()) {
                    if (rs.next()) {
                        currentRoom = rs.getInt("room_number");
                        currentGuest = rs.getString("guest_name");
                        currentContact = rs.getString("contact_number");
                        currentCheckIn = rs.getString("check_in_date");
                        currentCheckOut = rs.getString("check_out_date");
                    }
                }
            }

            // Prepare update
            String updateSql = "UPDATE reservations SET " +
                "guest_name = ?, contact_number = ?, check_in_date = ?, check_out_date = ? " +
                "WHERE reservation_id = ?";
            
            try (PreparedStatement updateStmt = connection.prepareStatement(updateSql)) {
                updateStmt.setString(1, newGuestName.isEmpty() ? currentGuest : newGuestName);
                updateStmt.setString(2, newContact.isEmpty() ? currentContact : newContact);
                updateStmt.setString(3, newCheckIn.isEmpty() ? currentCheckIn : newCheckIn);
                updateStmt.setString(4, newCheckOut.isEmpty() ? currentCheckOut : newCheckOut);
                updateStmt.setInt(5, reservationId);
                
                int affectedRows = updateStmt.executeUpdate();
                
                if (affectedRows > 0) {
                    System.out.println("Reservation updated successfully!");
                    displayReservationDetails(connection, reservationId);
                } else {
                    System.out.println("Failed to update reservation.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error updating reservation: " + e.getMessage());
        }
    }

    private static void deleteReservation(Connection connection, Scanner scanner) {
        int reservationId = getIntInput(scanner, "Enter reservation ID to delete: ");
        
        if (!reservationExists(connection, reservationId)) {
            System.out.println("Reservation not found with ID: " + reservationId);
            return;
        }

        try {
            // Get room number before deleting
            String roomSql = "SELECT room_number FROM reservations WHERE reservation_id = ?";
            int roomNumber = 0;
            
            try (PreparedStatement roomStmt = connection.prepareStatement(roomSql)) {
                roomStmt.setInt(1, reservationId);
                try (ResultSet rs = roomStmt.executeQuery()) {
                    if (rs.next()) {
                        roomNumber = rs.getInt("room_number");
                    }
                }
            }

            // Delete reservation
            String deleteSql = "DELETE FROM reservations WHERE reservation_id = ?";
            
            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, reservationId);
                int affectedRows = deleteStmt.executeUpdate();
                
                if (affectedRows > 0) {
                    updateRoomStatus(connection, roomNumber, "AVAILABLE");
                    System.out.println("Reservation deleted successfully!");
                } else {
                    System.out.println("Failed to delete reservation.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error deleting reservation: " + e.getMessage());
        }
    }

    private static void viewAvailableRooms(Connection connection) {
        String sql = "SELECT room_number, room_type, price_per_night, max_occupancy, amenities " +
                     "FROM rooms WHERE status = 'AVAILABLE'";
        
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            
            System.out.println("\n=== AVAILABLE ROOMS ===");
            System.out.println("+--------+------------+-------------+--------------+---------------------------+");
            System.out.println("| Room   | Type       | Price/Night | Max Occupancy | Amenities                |");
            System.out.println("+--------+------------+-------------+--------------+---------------------------+");

            while (resultSet.next()) {
                int room = resultSet.getInt("room_number");
                String type = resultSet.getString("room_type");
                double price = resultSet.getDouble("price_per_night");
                int occupancy = resultSet.getInt("max_occupancy");
                String amenities = resultSet.getString("amenities");

                System.out.printf("| %-6d | %-10s | $%-10.2f | %-12d | %-25s |\n", 
                                room, type, price, occupancy, amenities);
            }
            System.out.println("+--------+------------+-------------+--------------+---------------------------+");
        } catch (SQLException e) {
            System.err.println("Error viewing available rooms: " + e.getMessage());
        }
    }

    private static void viewRoomDetails(Connection connection, Scanner scanner) {
        int roomNumber = getIntInput(scanner, "Enter room number to view details: ");
        
        String sql = "SELECT * FROM rooms WHERE room_number = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, roomNumber);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    System.out.println("\n=== ROOM DETAILS ===");
                    System.out.println("Room Number: " + resultSet.getInt("room_number"));
                    System.out.println("Type: " + resultSet.getString("room_type"));
                    System.out.println("Price per night: $" + resultSet.getDouble("price_per_night"));
                    System.out.println("Max Occupancy: " + resultSet.getInt("max_occupancy"));
                    System.out.println("Amenities: " + resultSet.getString("amenities"));
                    System.out.println("Status: " + resultSet.getString("status"));
                    
                    // Check reservation status
                    String resSql = "SELECT COUNT(*) FROM reservations " +
                                   "WHERE room_number = ? AND check_out_date >= CURDATE()";
                    try (PreparedStatement resStmt = connection.prepareStatement(resSql)) {
                        resStmt.setInt(1, roomNumber);
                        try (ResultSet resRs = resStmt.executeQuery()) {
                            if (resRs.next() && resRs.getInt(1) > 0) {
                                System.out.println("\nThis room has upcoming reservations.");
                            } else {
                                System.out.println("\nThis room has no upcoming reservations.");
                            }
                        }
                    }
                } else {
                    System.out.println("Room " + roomNumber + " not found.");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error viewing room details: " + e.getMessage());
        }
    }

    private static boolean reservationExists(Connection connection, int reservationId) {
        String sql = "SELECT reservation_id FROM reservations WHERE reservation_id = ?";
        
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reservationId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            System.err.println("Error checking reservation existence: " + e.getMessage());
            return false;
        }
    }

    private static boolean isRoomAvailableForDates(Connection connection, int roomNumber, 
                                                 LocalDate checkIn, LocalDate checkOut) throws SQLException {
        String sql = "SELECT COUNT(*) FROM reservations " +
                     "WHERE room_number = ? " +
                     "AND ((check_in_date <= ? AND check_out_date >= ?) " +
                     "OR (check_in_date <= ? AND check_out_date >= ?) " +
                     "OR (check_in_date >= ? AND check_out_date <= ?))";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, roomNumber);
            stmt.setDate(2, Date.valueOf(checkOut));
            stmt.setDate(3, Date.valueOf(checkIn));
            stmt.setDate(4, Date.valueOf(checkOut));
            stmt.setDate(5, Date.valueOf(checkIn));
            stmt.setDate(6, Date.valueOf(checkIn));
            stmt.setDate(7, Date.valueOf(checkOut));
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 0;
                }
            }
        }
        return false;
    }

    private static void updateRoomStatus(Connection connection, int roomNumber, String status) throws SQLException {
        String sql = "UPDATE rooms SET status = ? WHERE room_number = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setInt(2, roomNumber);
            stmt.executeUpdate();
        }
    }

    private static int getLastInsertId(Connection connection) throws SQLException {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT LAST_INSERT_ID()")) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static void displayReservationDetails(Connection connection, int reservationId) throws SQLException {
        String sql = "SELECT r.*, rm.room_type, rm.price_per_night " +
                     "FROM reservations r JOIN rooms rm ON r.room_number = rm.room_number " +
                     "WHERE r.reservation_id = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, reservationId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    System.out.println("\n=== RESERVATION DETAILS ===");
                    System.out.println("Reservation ID: " + rs.getInt("reservation_id"));
                    System.out.println("Guest Name: " + rs.getString("guest_name"));
                    System.out.println("Room Number: " + rs.getInt("room_number") + 
                                     " (" + rs.getString("room_type") + ")");
                    System.out.println("Price per night: $" + rs.getDouble("price_per_night"));
                    System.out.println("Contact: " + rs.getString("contact_number"));
                    System.out.println("Check-in: " + rs.getDate("check_in_date"));
                    System.out.println("Check-out: " + rs.getDate("check_out_date"));
                    System.out.println("Status: " + rs.getString("status"));
                    
                    long nights = rs.getDate("check_out_date").toLocalDate()
                                  .until(rs.getDate("check_in_date").toLocalDate())
                                  .getDays();
                    double totalCost = nights * rs.getDouble("price_per_night");
                    System.out.println("Total cost: $" + String.format("%.2f", totalCost));
                }
            }
        }
    }

    private static String getStringInput(Scanner scanner, String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static int getIntInput(Scanner scanner, String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                int value = scanner.nextInt();
                scanner.nextLine(); // Consume newline
                return value;
            } catch (Exception e) {
                System.out.println("Invalid input. Please enter a number.");
                scanner.nextLine(); // Clear invalid input
            }
        }
    }

    private static LocalDate getValidDateInput(Scanner scanner, String prompt) {
        while (true) {
            try {
                System.out.print(prompt);
                String input = scanner.nextLine().trim();
                return LocalDate.parse(input, DATE_FORMATTER);
            } catch (DateTimeParseException e) {
                System.out.println("Invalid date format. Please use YYYY-MM-DD.");
            }
        }
    }

    private static void exit() {
        System.out.println("\nThank you for using the Hotel Reservation System!");
        System.out.println("Exiting...");
        System.exit(0);
    }
}