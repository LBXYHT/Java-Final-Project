package network;

import java.sql.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class UserDB {
    private static final String DB_URL = "jdbc:sqlite:user.db";

    static {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            String sql = """
                CREATE TABLE IF NOT EXISTS users (
                    username TEXT PRIMARY KEY,
                    password TEXT NOT NULL,
                    cash REAL NOT NULL
                );
            """;
            String hold="""
                CREATE TABLE IF NOT EXISTS holdings (
                    username TEXT,
                    stock TEXT,
                    amount INTEGER,
                    avg_price REAL,
                    PRIMARY KEY(username, stock)
                );
               """;

            String logTable = """
                    CREATE TABLE IF NOT EXISTS transaction_log (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        username TEXT,
                        log TEXT,
                        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
                    );
                """;
            
            stmt.execute(sql);
            stmt.execute(hold);
            stmt.execute(logTable);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean register(String username, String password) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "INSERT INTO users(username, password, cash) VALUES (?, ?, 1000)";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false; // 用户已存在
        }
    }

    public static boolean login(String username, String password) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT * FROM users WHERE username=? AND password=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public static double getCash(String username) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT cash FROM users WHERE username=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            return rs.getDouble("cash");
        } catch (SQLException e) {
            return 0;
        }
    }

    
    public static Map<String, Integer> getHoldings(String username) {
        Map<String, Integer> holdings = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "SELECT stock, amount FROM holdings WHERE username=?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                holdings.put(rs.getString("stock"), rs.getInt("amount"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return holdings;
    }
    
    public static void updateHolding(String username, String stock, int amount, double avgPrice) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO holdings (username, stock, amount, avg_price) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(username, stock) DO UPDATE SET amount = ?, avg_price = ?"
            );
            stmt.setString(1, username);
            stmt.setString(2, stock);
            stmt.setInt(3, amount);
            stmt.setDouble(4, avgPrice);
            stmt.setInt(5, amount);
            stmt.setDouble(6, avgPrice);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static void updateCash(String username, double cash) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "UPDATE users SET cash=? WHERE username=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setDouble(1, cash);
            pstmt.setString(2, username);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static List<String[]> getAllUserRecordsWithAvg(List<String> stocks) {
        List<String[]> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username, cash FROM users")) {
        	
            while (rs.next()) {
            	String username = rs.getString("username");
                double cash = rs.getDouble("cash");
                Map<String, Integer> amounts = getHoldings(username);
                Map<String, Double> avgs = getAllAvgPrices(username);
                
                // 构建行数据：用户名 + 现金 + 每个股票的持仓
                String[] row = new String[2 + stocks.size() * 2 + 1];
                row[0] = username;
                row[1] = String.format("%.2f", cash);
                for (int i = 0; i < stocks.size(); i++) {
                    String stock = stocks.get(i);
                    int amount = amounts.getOrDefault(stock, 0);
                    double avg = avgs.getOrDefault(stock, 0.0);
                    row[2 + i * 2] = String.valueOf(amount);
                    row[2 + i * 2 + 1] = String.format("%.2f", avg);
                }
                
                row[row.length - 1] = "";
                list.add(row);
            }
        } catch (SQLException e) {
            list.add(new String[]{"Error", "0", "0"});
        }
        return list;
    }
    
    public static List<String> getAllUsernames() {
        List<String> usernames = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT username FROM users")) {
            while (rs.next()) {
                usernames.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            usernames.add("Error reading users");
        }
        return usernames;
    }
    
    public static boolean addUser(String username, String password) {
        return register(username, password);
    }
    
    public static boolean deleteUser(String username) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "DELETE FROM users WHERE username=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
    
    public static boolean resetPassword(String username, String newPassword) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            String sql = "UPDATE users SET password=? WHERE username=?";
            PreparedStatement pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, newPassword);
            pstmt.setString(2, username);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            return false;
        }
    }
    
    public static void appendTransaction(String username, String logEntry) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO transaction_log (username, log) VALUES (?, ?)");
            stmt.setString(1, username);
            stmt.setString(2, logEntry);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static List<String> getTransactionLog(String username) {
        List<String> result = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement stmt = conn.prepareStatement("SELECT log FROM transaction_log WHERE username = ? ORDER BY id ASC");
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                result.add(rs.getString("log"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }
    
    public static int getShareAmount(String username, String stock) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT amount FROM holdings WHERE username = ? AND stock = ?");
            stmt.setString(1, username);
            stmt.setString(2, stock);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) return rs.getInt("amount");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
    
    public static double getAvgPrice(String username, String stock) {
        double avg = 0.0;
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement stmt = conn.prepareStatement(
                "SELECT avg_price FROM holdings WHERE username = ? AND stock = ?"
            );
            stmt.setString(1, username);
            stmt.setString(2, stock);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                avg = rs.getDouble("avg_price");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return avg;
    }
    
    public static Map<String, Double> getAllAvgPrices(String username) {
        Map<String, Double> map = new HashMap<>();
        String sql = "SELECT stock, avg_price FROM holdings WHERE username = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String stock = rs.getString("stock");
                double avg = rs.getDouble("avg_price");
                map.put(stock, avg);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }

}
