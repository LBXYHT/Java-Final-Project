package network;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

public class Client extends JFrame {
    private double cash = 1000.0;
    private Map<String, Integer> holdings = new HashMap<>();
    private Map<String, Double> stockPrices = new HashMap<>();
    private String selectedStock = "AAPL";
    
    private String username;
    private DataOutputStream toServer;
    private DataInputStream fromServer;
    private JTextArea ta = new JTextArea(10, 30);
    // define button to control
    private JButton buyMenuBtn, sellMenuBtn, nextBtn, infoBtn, priceBtn, logBtn;
    private JMenuItem buyAllItem, buyPartItem, sellAllItem, sellPartItem;
    private JPopupMenu buyMenu, sellMenu;
    private JComboBox<String> stockSelector;
    
    private boolean tradingEnded = false;
    
    public Client() {
        super("Stock Client");
        setupUI();
        connectToServer();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        
        JPanel buttonPanel = new JPanel();
        JPanel topPanel = new JPanel();
        stockSelector = new JComboBox<>();
        stockSelector.addActionListener(e -> {
            selectedStock = (String) stockSelector.getSelectedItem();
            ta.append(" Selected stock: " + selectedStock + "\n");
        });
        topPanel.add(new JLabel("Stock:"));
        topPanel.add(stockSelector);
        
        buyMenuBtn = new JButton("Buy");
        buyMenu = new JPopupMenu();
        buyAllItem = new JMenuItem("Buy All");
        buyPartItem = new JMenuItem("Buy Partial");
        buyAllItem.addActionListener(e -> buyAll());
        buyPartItem.addActionListener(e -> buyPartial());
        buyMenu.add(buyAllItem);
        buyMenu.add(buyPartItem);
        buyMenuBtn.addActionListener(e -> buyMenu.show(buyMenuBtn, 0, buyMenuBtn.getHeight()));
        
        sellMenuBtn = new JButton("Sell");
        sellMenu = new JPopupMenu();
        sellAllItem = new JMenuItem("Sell All");
        sellPartItem = new JMenuItem("Sell Partial");
        sellAllItem.addActionListener(e -> sellAll());
        sellPartItem.addActionListener(e -> sellPartial());
        sellMenu.add(sellAllItem);
        sellMenu.add(sellPartItem);
        sellMenuBtn.addActionListener(e -> sellMenu.show(sellMenuBtn, 0, sellMenuBtn.getHeight()));
        
        nextBtn = new JButton("Next Day");
        nextBtn.addActionListener(e -> updatePrice());
        infoBtn = new JButton("View Info");
        infoBtn.addActionListener(e -> showUserInfo());
        priceBtn = new JButton("View Prices");
        priceBtn.addActionListener(e -> showPrices());
        
        logBtn = new JButton("Transaction record");
        logBtn.addActionListener(e -> {
            try {
                toServer.writeUTF("GET_LOG");
                int count = fromServer.readInt();
                StringBuilder sb = new StringBuilder("Transaction record：\n");
                for (int i = 0; i < count; i++) {
                    sb.append(fromServer.readUTF()).append("\n");
                }
                JOptionPane.showMessageDialog(Client.this, sb.toString(), "Transaction record", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
        
        topPanel.add(logBtn);
        topPanel.add(buyMenuBtn);
        topPanel.add(sellMenuBtn);
        topPanel.add(nextBtn);
        topPanel.add(infoBtn);
        topPanel.add(priceBtn);
        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(ta), BorderLayout.CENTER);

        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }
    
    private void loginOrRegister() {
        String[] options = {"Login", "Register"};
        int choice = JOptionPane.showOptionDialog(this, "Choose Action", "Login/Register",
            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, options, options[0]);
        
        if (choice == -1) {
            JOptionPane.showMessageDialog(this, "You must choose Login or Register.");
            System.exit(0);
        }
        
        String username = JOptionPane.showInputDialog("Enter username:");
        String password = JOptionPane.showInputDialog("Enter password:");

        try {
            toServer.writeUTF(choice == 0 ? "LOGIN" : "REGISTER");
            toServer.writeUTF(username);
            toServer.writeUTF(password);

            boolean success = fromServer.readBoolean();
            if (!success) {
                JOptionPane.showMessageDialog(this, "Authentication failed. Closing app.");
                System.exit(0);
            } else {
            	this.username = username; // 保存用户名
            	this.setTitle("Stock Client - User: " + username); // 设置窗口标题
            	this.cash = fromServer.readDouble();
           
                Gson gson = new Gson();
                java.lang.reflect.Type type = new TypeToken<Map<String, Integer>>() {}.getType();
                holdings = gson.fromJson(fromServer.readUTF(), type);
                if (holdings == null) holdings = new HashMap<>();
                while (true) {
                    String stock = fromServer.readUTF();
                    if ("END".equals(stock)) break;
                    double price = fromServer.readDouble();
                    stockPrices.put(stock, price);
                }
                
                //初始化股票列表
                stockSelector.setModel(new DefaultComboBoxModel<>(stockPrices.keySet().toArray(new String[0])));
                selectedStock = (String) stockSelector.getSelectedItem();
                
                ta.append(String.format("Welcome %s! Cash: $%.2f, Holdings: %s\n",
                    username, cash, holdings));
                
                ta.append("Initial prices:\n");
                for (String stock : stockPrices.keySet()) {
                    ta.append(String.format(" - %s: $%.2f\n", stock, stockPrices.get(stock)));
                }
                
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Connection error during login.");
            System.exit(0);
        }
    }
    
    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 8000);
            toServer = new DataOutputStream(socket.getOutputStream());
            fromServer = new DataInputStream(socket.getInputStream());
            loginOrRegister();
            // 初始读取服务器发送的当前股价
            //currentPrice = fromServer.readDouble();
            //ta.append("Current stock price: $" + currentPrice + "\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Connection to server fails");
        }
    }
    
    private void updateUserInfoFromServer() throws IOException {
        Gson gson = new Gson();
        java.lang.reflect.Type type = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(fromServer.readUTF(), type);
        cash = ((Number) data.get("cash")).doubleValue();
        Map<String, Double> rawHoldings = (Map<String, Double>) data.get("holdings");
        holdings.clear();
        for (Map.Entry<String, Double> e : rawHoldings.entrySet()) {
            holdings.put(e.getKey(), e.getValue().intValue());
        }
    }
    
    private void buyAll() {
    	if (tradingEnded) {
            ta.append("End of transaction！Unable to buy!\n");
            return;
        }
        try {
        	toServer.writeUTF("BUY_ALL " + selectedStock);
            double price = fromServer.readDouble();
            updateUserInfoFromServer();
            int amount = holdings.getOrDefault(selectedStock, 0);
            ta.append(String.format("[%s] Bought ALL %d %s @ $%.2f\n", username, amount, selectedStock, price));
            updateBalance();
            ta.append(String.format("After transaction: Total assets = $%.2f\n", cash + holdings.getOrDefault(selectedStock, 0) * price));
            
        } catch (IOException e) {
            ta.append("Buy failed\n");
        }
    }
    
    private void buyPartial() {
        if (tradingEnded) return;
        String input = JOptionPane.showInputDialog(this, "Enter number of shares to BUY:");
        try {
        	int amount = Integer.parseInt(input);
            toServer.writeUTF("BUY_PARTIAL " + selectedStock);
            toServer.writeInt(amount);
            boolean success = fromServer.readBoolean();
            double price = fromServer.readDouble();
            updateUserInfoFromServer();
            if (success) {
                cash -= amount * price;
                holdings.put(selectedStock, holdings.getOrDefault(selectedStock, 0) + amount);
                ta.append(String.format("[%s] Bought %d %s @ $%.2f\n", username, amount, selectedStock, price));
                updateBalance();
                ta.append(String.format("After transaction: Total assets = $%.2f\n", cash + holdings.getOrDefault(selectedStock, 0) * price));
            } else {
                ta.append("Buy failed\n");
            }
        } catch (Exception e) {
            ta.append("Invalid input.\n");
        }
    }
    
    private void sellAll() {
        try {
        	toServer.writeUTF("SELL_ALL " + selectedStock);
            double price = fromServer.readDouble();
            updateUserInfoFromServer();
            ta.append(String.format("[%s] Sold ALL %s @ $%.2f\n", username, selectedStock, price));
            updateBalance();
            ta.append(String.format("After transaction: Total assets = $%.2f\n", cash + holdings.getOrDefault(selectedStock, 0) * price));
        } catch (IOException e) {
            ta.append("Sell failed\n");
        }
    }
    
    private void sellPartial() {
        if (tradingEnded) return;
        String input = JOptionPane.showInputDialog(this, "Enter number of shares to SELL:");
        try {
        	int amount = Integer.parseInt(input);
            toServer.writeUTF("SELL_PARTIAL " + selectedStock);
            toServer.writeInt(amount);
            boolean success = fromServer.readBoolean();
            double price = fromServer.readDouble();
            updateUserInfoFromServer();
            if (success) {
                cash += amount * price;
                holdings.put(selectedStock, holdings.getOrDefault(selectedStock, 0) - amount);
                ta.append(String.format("[%s] Sold %d %s @ $%.2f\n", username, amount, selectedStock, price));
                updateBalance();
                ta.append(String.format("After transaction: Total assets = $%.2f\n", cash + holdings.getOrDefault(selectedStock, 0) * price));
            } else {
                ta.append("Sell failed\n");
            }
        } catch (Exception e) {
            ta.append("Invalid input.\n");
        }
    }
    
    
    private void showUserInfo() {
    	try {
            toServer.writeUTF("GET_USER_INFO");
            updateUserInfoFromServer();
        } catch (IOException e) {
            ta.append("Failed to fetch user info.\n");
        }
        ta.append(String.format("[%s] Cash: $%.2f\n", username, cash));
        for (String stock : stockPrices.keySet()) {
            int s = holdings.getOrDefault(stock, 0);
            double p = stockPrices.get(stock);
            try {
                toServer.writeUTF("GET_AVG_PRICE " + stock);
                double avg = fromServer.readDouble();
                double pnl = (p - avg) * s;
                ta.append(String.format(" - %s: %d shares @ $%.2f (avg $%.2f) = $%.2f | PnL: $%.2f\n",
                    stock, s, p, avg, s * p, pnl));
            } catch (IOException ex) {
                ta.append(String.format(" - %s: %d shares @ $%.2f = $%.2f\n", stock, s, p, s * p));
            }
        }
    }
    
    private void showPrices() {
        ta.append("Current Stock Prices:\n");
        for (Map.Entry<String, Double> entry : stockPrices.entrySet()) {
            ta.append(String.format(" - %s: $%.2f\n", entry.getKey(), entry.getValue()));
        }
    }
    
    private void updatePrice() {
    	if (tradingEnded) {
            ta.append("End of transaction！Unable to update!\n");
            return;
        }
        try {
        	toServer.writeUTF("NEXT_ALL");
        	Map<String, Double> oldPrices = new HashMap<>(stockPrices);
        	ta.append(" Market advanced to next day:\n");
        	while (true) {
        	    String stock = fromServer.readUTF();
        	    if ("END".equals(stock)) break;
        	    double newPrice = fromServer.readDouble();
                double oldPrice = oldPrices.getOrDefault(stock, newPrice);
                stockPrices.put(stock, newPrice);
                
                String trend = "No change";
                if (newPrice > oldPrice) trend = "Up";
                else if (newPrice < oldPrice) trend = "Down";

                ta.append(String.format(" - %s: $%.2f (%s)\n", stock, newPrice, trend));
        	}
            updateBalance();
        } catch (IOException e) {
            tradingEnded = true;
            ta.append("Price update failed\n");
            // End of stock update, disable transaction buttons
        	/*
            ta.append("End of transaction！Unable to operate!\n");
            buyBtn.setEnabled(false);
            sellBtn.setEnabled(false);
            nextBtn.setEnabled(false);
            */
            endTrading();
        }
    }
    
    private void updateBalance() {
    	double price = stockPrices.getOrDefault(selectedStock, 0.0);
        int shares = holdings.getOrDefault(selectedStock, 0);
        double total = shares * price + cash;
        ta.append(String.format("[%s] %s shares: %d, Cash: $%.2f, Total: $%.2f\n",
            username, selectedStock, shares, cash, total));
    }
    
    private void endTrading() {
        tradingEnded = true;
        buyMenuBtn.setEnabled(false);
        sellMenuBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        ta.append("End of transaction！Unable to operate!\\n");
    }
    
    public static void main(String[] args) {
        new Client();
    }
}
