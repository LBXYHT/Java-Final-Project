package network;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

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
    
    // plotting
    private List<Double> prices = new ArrayList<>(); // store history prices
    private int currentDay = 0; // current day
    private StockChartPanel chartPanel; // plot stock chart
    
    public Client() {
        super("Stock Server");
        setupUI();
        connectToServer();
    }
    
    private void setupUI() {
        setLayout(new BorderLayout());
        
        // add split panel
        chartPanel = new StockChartPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
            new JScrollPane(ta), 
            chartPanel
        );
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
        
        // button
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
        
        ta.append("Initial account: $1000\n");
        setSize(600, 800);
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
            prices.add(currentPrice);
            ta.append("Current stock price: $" + currentPrice + "\n");
            SwingUtilities.invokeLater(() -> chartPanel.repaint());
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
            prices.add(newPrice); // add new price to list
            currentDay++; // day increment
            ta.append("New day Stock Price: $" + newPrice + "\n");
            updateBalance();
            chartPanel.repaint(); // repaint plot
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
    
    private class StockChartPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (prices.isEmpty()) return;

            Graphics2D g2d = (Graphics2D) g;
            int width = getWidth();
            int height = getHeight();
            int paddingLeft = 50;
            int paddingRight = 20;
            int paddingTop = 25;
            int paddingBottom = 25;

            // clear bg
            g2d.setColor(Color.WHITE);
            g2d.fillRect(0, 0, width, height);
            g2d.setColor(Color.BLACK);

            // plot axis
            g2d.drawLine(paddingLeft, height - paddingBottom, width - paddingRight, height - paddingBottom);
            g2d.drawLine(paddingLeft, paddingTop, paddingLeft, height - paddingBottom);

            // calculate price range
            double maxPrice = prices.stream().mapToDouble(Double::doubleValue).max().orElse(1);
            double minPrice = prices.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            if (maxPrice == minPrice) {
                maxPrice += 1.0;
                minPrice -= 1.0;
            }

            // plot x-axis label
            int totalDays = prices.size();
            
            int availableWidth = width - paddingLeft - paddingRight; //
            
            if (totalDays == 1) {
                int x = paddingLeft + availableWidth / 2;
                g2d.drawString("1", x - 5, height - paddingBottom + 15);
            } else {
                for (int i = 0; i < totalDays; i++) {
                    int x = paddingLeft + (i * availableWidth) / (totalDays - 1);
                    g2d.drawString(String.valueOf(i + 1), x - 5, height - paddingBottom + 15);
                }
            }

            // plot y-axis label
            FontMetrics metrics = g2d.getFontMetrics();
            int ySteps = 5;
            for (int i = 0; i <= ySteps; i++) {
                double value = minPrice + (maxPrice - minPrice) * i / ySteps;
                int y = height - paddingBottom - (int) ((value - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                String label = String.format("%.1f", value);
                g2d.drawString(label, paddingLeft - metrics.stringWidth(label) - 5, y + metrics.getAscent() / 2);
            }

            // plot lines
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(2));
            for (int i = 0; i < totalDays - 1; i++) {
                int x1 = paddingLeft + i * (availableWidth) / (totalDays - 1);
                int y1 = height - paddingBottom - (int) ((prices.get(i) - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                int x2 = paddingLeft + (i + 1) * (availableWidth) / (totalDays - 1);
                int y2 = height - paddingBottom - (int) ((prices.get(i + 1) - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                g2d.draw(new Line2D.Double(x1, y1, x2, y2));
            }

            // plot points
            g2d.setColor(Color.RED);
            for (int i = 0; i < totalDays; i++) {
                int x;
                if (totalDays == 1) {
                    x = paddingLeft + availableWidth / 2;
                } else {
                    x = paddingLeft + (i * availableWidth) / (totalDays - 1);
                }
                int y = height - paddingBottom - (int) ((prices.get(i) - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                g2d.fillOval(x - 3, y - 3, 6, 6);
            }
        }
    }
    
    
    public static void main(String[] args) {
        new Client();
    }
}