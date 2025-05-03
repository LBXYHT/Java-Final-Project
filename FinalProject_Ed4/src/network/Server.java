package network;

import java.io.*;
import java.net.*;
import javax.swing.*;
import javax.swing.table.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;


public class Server extends JFrame {
    // Record of stock price array
	private static final Map<String, Double> currentPrices = new HashMap<>();
	private static final Map<String, Integer> currentDayMap = new HashMap<>();
	private static final Map<String, double[]> returnMap = new HashMap<>();
	private static final Map<String, List<String>> transactionLog = new HashMap<>();
	private static final Map<String, Map<String, Double>> avgBuyPrice = new HashMap<>();
	
	private static final double[] aaplReturns = {
	        0.012772, 0.001484, 0.000123, -0.000823, 0.016101,
	        0.004134, -0.005166, 0.005964, 0.000686, 0.011728,
	        0.000839, 0.010144, -0.000402, -0.008697, -0.003644,
	        0.009493, -0.006575, 0.000242, 0.001947, -0.003757,
	        0.004054, 0.010286, -0.000122, -0.002334, -0.002218,
	        0.002341, 0.008805, -0.004429, 0.007101, 0.004523,
	        -0.006497, -0.002474, -0.000842, 0.009104, -0.00024,
	        0.004444, 0.002705, -0.001882, 0.004001, 0.00511
	    };
	    private static final double[] tslaReturns = {
	        -0.015878, 0.018525, 0.032297, 0.053398, 0.001464,
	        -0.00899, -0.003728, -0.017632, 0.027738, -0.001679,
	        -0.005881, 0.008681, 0.020878, -0.020801, -0.014496,
	        0.004792, 0.016737, 0.008223, -0.011879, 0.002463,
	        0.023423, 0.004989, -0.007857, -0.019041, -0.009993
	    };
	    private static final double[] amznReturns = {
	        0.012956, 0.022114, 0.010955, 0.029381, -0.00414,
	        0.01458, -0.006506, 0.012215, -0.009285, -0.015253,
	        0.020588, -0.00734, 0.003578, -0.009884, 0.008708,
	        -0.010957, 0.001378, 0.015262, 0.006183, -0.012379,
	        -0.002302, 0.018451, -0.008571, 0.012282, 0.001673
	    };
    // use AtomicInteger to manage current transaction day（start from 0）
    private JTextArea ta;
    private Random rand = new Random();
    
    public Server() {
        super("Stock Server");
        ta = new JTextArea();
        add(new JScrollPane(ta));
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        
        currentPrices.put("AAPL", 100.0);
        currentPrices.put("TSLA", 200.0);
        currentPrices.put("AMZN", 300.0);
        currentDayMap.put("AAPL", 0);
        currentDayMap.put("TSLA", 0);
        currentDayMap.put("AMZN", 0);
        returnMap.put("AAPL", aaplReturns);
        returnMap.put("TSLA", tslaReturns);
        returnMap.put("AMZN", amznReturns);
        
        JButton manageBtn = new JButton("User Manager");
        manageBtn.addActionListener(e -> showUserManagerUI());
        JPanel adminPanel = new JPanel();
        adminPanel.add(manageBtn);
        add(adminPanel, BorderLayout.SOUTH);
        
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8000)) {
                ta.append("Server launched, waiting for clients to connect...\n");
                while (true) {
                    Socket socket = serverSocket.accept();
                    ta.append("Server connection：" + socket.getInetAddress() + "\n");
                    new ClientHandler(socket).start();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }
    
    private double simulatePrice(double lastPrice, double[] returns) {
        double realReturn = returns[rand.nextInt(returns.length)];
        double noise = rand.nextGaussian() * 0.005;
        return Math.round(lastPrice * (1 + realReturn + noise) * 100.0) / 100.0;
    }
    
    private void logTransaction(String user, String info) {
    	String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String entry = "[" + timestamp + "] " + info;
        transactionLog.putIfAbsent(user, new ArrayList<>());
        transactionLog.get(user).add(entry);
        UserDB.appendTransaction(user, entry);
        ta.append("[LOG] " + user + ": " + info + "\n");
    }
    
    private void updateAverageBuyPrice(String user, String stock, int newAmount, double price) {
        avgBuyPrice.putIfAbsent(user, new HashMap<>());
        Map<String, Double> stockMap = avgBuyPrice.get(user);

        int oldAmount = UserDB.getShareAmount(user, stock);
        double oldAvg = UserDB.getAvgPrice(user, stock);
        double newAvg = (oldAvg * oldAmount + price * newAmount) / (oldAmount + newAmount);
        stockMap.put(stock, newAvg);
        UserDB.updateHolding(user, stock, newAmount + oldAmount, newAvg);
    }
    
    private class ClientHandler extends Thread {
        private final Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        private void sendUserInfo(DataOutputStream out, String username) throws IOException {
            Gson gson = new Gson();
            Map<String, Object> info = new HashMap<>();
            info.put("cash", UserDB.getCash(username));
            info.put("holdings", UserDB.getHoldings(username));
            out.writeUTF(gson.toJson(info));
        }
        
        @Override
        public void run() {
            try (
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
            ) {
            	//login
            	String mode = input.readUTF(); // "LOGIN" or "REGISTER"
            	String username = input.readUTF();
            	String password = input.readUTF();

            	boolean success = false;
            	if ("REGISTER".equals(mode)) {
            	    success = UserDB.register(username, password);
            	} else if ("LOGIN".equals(mode)) {
            	    success = UserDB.login(username, password);
            	}
            	output.writeBoolean(success);
            	if (!success) {
            	    ta.append("Authentication failed：" + username + "\n");
            	    return; 
            	}

            	double cash = UserDB.getCash(username);
            	Map<String, Integer> holdings = UserDB.getHoldings(username);
            	ta.append("Login successful：" + username + "，Cash $" + cash + "，Holdings: " + holdings + "\n");
            	
            	output.writeDouble(cash); 
            	Gson gson = new Gson();
            	output.writeUTF(gson.toJson(holdings)); 
            	
            	for (String stock : currentPrices.keySet()) {
            		double price = currentPrices.get(stock);
            	    output.writeUTF(stock);
            	    output.writeDouble(price);
            	    ta.append("Initial: " + stock + " $" + price + "\n");
            	}
            	output.writeUTF("END");
                // send initial stock price
                while (true) {
                    String command = input.readUTF();
                    ta.append("Received command: " + command + "\n");
                    if (command.startsWith("NEXT")) {
                    	for (String stock : currentPrices.keySet()) {
                    		double last = currentPrices.get(stock);
                            double newPrice = simulatePrice(last, returnMap.get(stock));
                            currentPrices.put(stock, newPrice);
                            output.writeUTF(stock);
                            output.writeDouble(newPrice);
                            ta.append(" Day advanced for " + stock + " → $" + newPrice + "\n");
                        }
                        output.writeUTF("END");
                    } else if (command.startsWith("BUY_ALL")) {
                        String stock = command.split(" ")[1];
                        double price = currentPrices.get(stock);
                        double cashNow = UserDB.getCash(username);
                        int amount = (int) (cash / price);

                        if (amount > 0) {
                        	UserDB.updateCash(username, cashNow - amount * price);
                        	int oldAmount = UserDB.getShareAmount(username, stock);
                        	int newAmount = oldAmount + amount;
                        	double oldAvg = UserDB.getAvgPrice(username, stock);
                        	double newAvg = (oldAvg * oldAmount + price * amount) / newAmount;
                        	UserDB.updateHolding(username, stock, newAmount, newAvg);
                            ta.append("[" + username + "] BUY_ALL " + amount + " " + stock + " @ $" + price + "\n");
                            logTransaction(username, "BUY_ALL " + stock + " @ $" + currentPrices.get(stock));
                        }
                        output.writeDouble(price);
                        sendUserInfo(output, username);
                    } else if (command.startsWith("SELL_ALL")) {
                        String stock = command.split(" ")[1];
                        double price = currentPrices.get(stock);
                        int amount = UserDB.getShareAmount(username, stock);
                        
                        if (amount > 0) {
                            double cashNow = UserDB.getCash(username);
                            UserDB.updateCash(username, cashNow + amount * price);
                            UserDB.updateHolding(username, stock, 0, 0.0);
                            ta.append("[" + username + "] SELL_ALL " + amount + " " + stock + " @ $" + price + "\n");
                            logTransaction(username, "SELL_ALL " + stock + " @ $" + currentPrices.get(stock));
                        }
                        output.writeDouble(price);
                        sendUserInfo(output, username);
                    }else if (command.startsWith("BUY_PARTIAL")) {
                    	String[] parts = command.split(" ");
                        String stock = parts[1];
                        int amount = input.readInt();

                        double price = currentPrices.get(stock);
                        double cost = amount * price;
                        double cashNow = UserDB.getCash(username);

                        if (cashNow >= cost && amount > 0) {
                            UserDB.updateCash(username, cashNow - cost);
                            updateAverageBuyPrice(username, stock, amount, price);
                            ta.append("[" + username + "] BUY " + amount + " " + stock + " @ $" + price + "\n");
                            logTransaction(username, "BUY " + amount + " " + stock + " @ $" + currentPrices.get(stock));
                            output.writeBoolean(true);
                        } else {
                            output.writeBoolean(false);
                        }
                        output.writeDouble(price);
                        sendUserInfo(output, username);
                    } else if (command.startsWith("SELL_PARTIAL")) {
                    	String[] parts = command.split(" ");
                        String stock = parts[1];
                        int amount = input.readInt();

                        double price = currentPrices.get(stock);
                        int held = UserDB.getShareAmount(username, stock);

                        if (held >= amount && amount > 0) {
                            double cashNow = UserDB.getCash(username);
                            UserDB.updateCash(username, cashNow + amount * price);
                            double oldAvg = UserDB.getAvgPrice(username, stock);
                            UserDB.updateHolding(username, stock, held - amount, oldAvg);
                            ta.append("[" + username + "] SELL " + amount + " " + stock + " @ $" + price + "\n");
                            logTransaction(username, "SELL " + amount + " " + stock + " @ $" + currentPrices.get(stock));
                            output.writeBoolean(true);
                        } else {
                            output.writeBoolean(false);
                        }
                        output.writeDouble(price);
                        sendUserInfo(output, username);
                    }else if ("GET_USER_INFO".equals(command)) {
                    	sendUserInfo(output, username);
                    }else if ("GET_LOG".equals(command)) {
                    	
                    	List<String> logs = UserDB.getTransactionLog(username);
                        output.writeInt(logs.size());
                        for (String line : logs) output.writeUTF(line);
                    }else if (command.startsWith("GET_AVG_PRICE")) {
                        String stock = command.split(" ")[1];
                        double avg = UserDB.getAvgPrice(username, stock);
                        output.writeDouble(avg);
                    }
                }
            } catch (EOFException e) {
                ta.append("Server disconnected\n");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    
    private void showUserManagerUI() {
        JFrame frame = new JFrame("User Manager");
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        // === 表格部分 ===
        List<String> headers = new ArrayList<>();
        headers.add("Username");
        headers.add("Cash");
        for (String stock : currentPrices.keySet()) {
            headers.add(stock);
            headers.add(stock + " Avg");
        }  // 动态股票列
        headers.add("Log");
        String[] columnNames = headers.toArray(new String[0]);
        
        List<String[]> rows = UserDB.getAllUserRecordsWithAvg(new ArrayList<>(currentPrices.keySet()));
        String[][] data = rows.toArray(new String[0][]);

        DefaultTableModel model = new DefaultTableModel(data, columnNames);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);
        frame.add(scrollPane, BorderLayout.CENTER);

        // === 底部按钮和输入栏 ===
        JPanel bottomPanel = new JPanel();

        JButton addBtn = new JButton("Add");
        JButton deleteBtn = new JButton("Delete Selected");
        JButton updateBtn = new JButton("Update Selected");
        JButton resetPassBtn = new JButton("Reset Password");
        JButton exportBtn = new JButton("Export CSV");

        bottomPanel.add(addBtn);
        bottomPanel.add(deleteBtn);
        bottomPanel.add(updateBtn);
        bottomPanel.add(resetPassBtn);
        bottomPanel.add(exportBtn);

        frame.add(bottomPanel, BorderLayout.SOUTH);

        // 日志按钮点击事件
        table.getColumn("Log").setCellRenderer(new ButtonRenderer());
        table.getColumn("Log").setCellEditor(new ButtonEditor(new JCheckBox(), table));
        
        exportBtn.addActionListener(e -> {
            try (PrintWriter writer = new PrintWriter("user_export.csv")) {
                for (int i = 0; i < model.getColumnCount(); i++) {
                    writer.print(model.getColumnName(i));
                    if (i != model.getColumnCount() - 1) writer.print(",");
                }
                writer.println();
                for (int row = 0; row < model.getRowCount(); row++) {
                    for (int col = 0; col < model.getColumnCount(); col++) {
                        writer.print(model.getValueAt(row, col));
                        if (col != model.getColumnCount() - 1) writer.print(",");
                    }
                    writer.println();
                }
                JOptionPane.showMessageDialog(frame, "Export complete.");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        
        // === 添加用户按钮逻辑 ===
        addBtn.addActionListener(e -> {
            JTextField usernameField = new JTextField();
            JTextField passwordField = new JTextField();
            JPanel panel = new JPanel(new GridLayout(2, 2));
            panel.add(new JLabel("Username:"));
            panel.add(usernameField);
            panel.add(new JLabel("Password:"));
            panel.add(passwordField);

            int result = JOptionPane.showConfirmDialog(frame, panel, "Add New User", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String user = usernameField.getText().trim();
                String pass = passwordField.getText().trim();
                if (UserDB.addUser(user, pass)) {
                	List<Object> row = new ArrayList<>();
                	row.add(user);
                	row.add("1000.0");
                	for (int i = 0; i < currentPrices.size(); i++) {
                	    row.add("0");        
                	    row.add("0.00");     
                	}
                	model.addRow(row.toArray());
                    JOptionPane.showMessageDialog(frame, "User added: " + user);
                } else {
                    JOptionPane.showMessageDialog(frame, "Failed to add user (already exists?)");
                }
            }
        });

        // === 删除选中用户逻辑 ===
        deleteBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                String user = (String) model.getValueAt(row, 0);
                int confirm = JOptionPane.showConfirmDialog(frame, "Delete user: " + user + "?", "Confirm", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION && UserDB.deleteUser(user)) {
                    model.removeRow(row);
                    JOptionPane.showMessageDialog(frame, "User deleted: " + user);
                } else {
                    JOptionPane.showMessageDialog(frame, "Failed to delete user.");
                }
            } else {
                JOptionPane.showMessageDialog(frame, "Please select a user to delete.");
            }
        });

        // === 修改选中用户逻辑 ===
        updateBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
            	try {
                    String user = (String) model.getValueAt(row, 0);
                    double cash = Double.parseDouble(model.getValueAt(row, 1).toString());

                    // 股票列表，需与列名顺序一致
                    List<String> stocks = new ArrayList<>(currentPrices.keySet());
                    Map<String, Integer> holdings = new HashMap<>();
                    Map<String, Double> avgPrices = new HashMap<>();
                    for (int i = 0; i < stocks.size(); i++) {
                    	String stock = stocks.get(i);
                        int amount = Integer.parseInt(model.getValueAt(row, 2 + i * 2).toString());
                        double avg = Double.parseDouble(model.getValueAt(row, 2 + i * 2 + 1).toString());
                        holdings.put(stock, amount);
                        avgPrices.put(stock, avg);
                    }

                    // 更新数据库
                    UserDB.updateCash(user, cash);
                    for (String stock : stocks) {
                    	int amount = holdings.get(stock);
                        double avg = avgPrices.get(stock);
                        UserDB.updateHolding(user, stock, amount, avg);
                    }

                    JOptionPane.showMessageDialog(frame, "Updated user: " + user);

                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame, "Invalid number format.");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error updating user: " + ex.getMessage());
                }
            } else {
                JOptionPane.showMessageDialog(frame, "Please select a user to update.");
            }
        });
        
        // reset password
        resetPassBtn.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                String username = (String) model.getValueAt(row, 0);
                String newPassword = JOptionPane.showInputDialog(frame, "Enter new password for " + username + ":");
                if (newPassword != null && !newPassword.isEmpty()) {
                    if (UserDB.resetPassword(username, newPassword)) {
                        JOptionPane.showMessageDialog(frame, "Password reset successfully.");
                    } else {
                        JOptionPane.showMessageDialog(frame, "Password reset failed.");
                    }
                }
            } else {
                JOptionPane.showMessageDialog(frame, "Please select a user to reset password.");
            }
        });

        frame.setVisible(true);
    }
    
    class ButtonRenderer extends JButton implements TableCellRenderer {
        public ButtonRenderer() { setText("View"); }
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            return this;
        }
    }
    
    class ButtonEditor extends DefaultCellEditor {
        private JButton button;
        private JTable table;
        public ButtonEditor(JCheckBox checkBox, JTable table) {
            super(checkBox);
            this.table = table;
            button = new JButton("View");
            button.addActionListener(e -> {
                int row = table.getSelectedRow();
                String user = (String) table.getValueAt(row, 0);
                List<String> logs = UserDB.getTransactionLog(user);
                StringBuilder sb = new StringBuilder();
                for (String line : logs) sb.append(line).append("\n");
                JOptionPane.showMessageDialog(table, sb.toString(), user + " Log", JOptionPane.INFORMATION_MESSAGE);
            });
        }
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            return button;
        }
    }
    
    public static void main(String[] args) {
        new Server();
    }
}
