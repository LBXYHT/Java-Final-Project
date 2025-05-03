package network;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class Client extends JFrame {
    private double cash = 1000.0;
    private int shares = 0;
    private double currentPrice = 0;
    private DataOutputStream toServer;
    private DataInputStream fromServer;
    private JTextArea ta = new JTextArea(10, 30);
    // define button to control
    private JButton buyBtn;
    private JButton sellBtn;
    private JButton nextBtn;
    
    private boolean tradingEnded = false;
    
    public Client() {
        super("Stock Server");
        setupUI();
        connectToServer();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        
        JPanel buttonPanel = new JPanel();
        buyBtn = new JButton("Buy All");
        sellBtn = new JButton("Sell All");
        nextBtn = new JButton("Next Day");
        
        // check button press
        buyBtn.addActionListener(e -> buyStocks());
        sellBtn.addActionListener(e -> sellStocks());
        nextBtn.addActionListener(e -> updatePrice());
        
        buttonPanel.add(buyBtn);
        buttonPanel.add(sellBtn);
        buttonPanel.add(nextBtn);
        
        add(buttonPanel, BorderLayout.NORTH);
        add(new JScrollPane(ta), BorderLayout.CENTER);
        
        ta.append("Initial account: $1000\n");
        setSize(400, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
    }
    
    private void connectToServer() {
        try {
            Socket socket = new Socket("localhost", 8000);
            toServer = new DataOutputStream(socket.getOutputStream());
            fromServer = new DataInputStream(socket.getInputStream());
            // read initial price sent by server
            currentPrice = fromServer.readDouble();
            ta.append("Current stock price: $" + currentPrice + "\n");
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Connection to server fails");
        }
    }
    
    private void buyStocks() {
    	if (tradingEnded) {
            ta.append("End of transaction！Unable to buy!\n");
            return;
        }
        try {
            toServer.writeUTF("BUY");
            boolean endFlag = fromServer.readBoolean();
            double price = fromServer.readDouble();
            if(endFlag) {
                ta.append("End of transaction！Unable to buy!\n");
                tradingEnded = endFlag;
                return;
            }
            int newShares = (int) (cash / price);
            double cost = newShares * price;
            cash -= cost;
            shares += newShares;
            ta.append("Buy " + newShares + " newShares in $" + price + " with cost $" + cost + "\n");
            updateBalance();
        } catch (IOException ex) {
            ex.printStackTrace();
            endTrading();
        }
    }
    
    private void sellStocks() {
        try {
            toServer.writeUTF("SELL");
            double price = fromServer.readDouble();
            double proceeds = shares * price;
            ta.append("Sell " + shares + " shares in $" + price + ", proceeding $" + proceeds + "\n");
            shares = 0;
            cash += proceeds;
            updateBalance();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    private void updatePrice() {
    	if (tradingEnded) {
            ta.append("End of transaction！Unable to update!\n");
            return;
        }
        try {
            toServer.writeUTF("NEXT");
            boolean endFlag = fromServer.readBoolean();
            double newPrice = fromServer.readDouble();
            currentPrice = newPrice;
            ta.append("New day Stock Price: $" + newPrice + "\n");
            updateBalance();
            if (endFlag) {
                ta.append("End of transaction！Unable to update!\n");
                tradingEnded = endFlag;
            }
        } catch (IOException ex) {
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
        double total = shares * currentPrice + cash;
        ta.append(String.format("Current position: %d shares, Cash: $%.2f, Total assets: $%.2f\n", 
            shares, cash, total));
    }
    
    private void endTrading() {
        tradingEnded = true;
        buyBtn.setEnabled(false);
        sellBtn.setEnabled(false);
        nextBtn.setEnabled(false);
        ta.append("End of transaction！Unable to operate!\\n");
    }
    
    public static void main(String[] args) {
        new Client();
    }
}