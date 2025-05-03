package network;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.swing.*;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.awt.*;
import java.awt.geom.Line2D;

public class Server extends JFrame {
    // Record of stock price array
    private static double[] PRICES = {10.00, 9.00, 11.00, 12.00, 10.00};
    // use AtomicInteger to manage current transaction day（start from 0）
    private static final AtomicInteger currentDay = new AtomicInteger(0);
    // define text area
    private JTextArea ta;
    // update: define chart panel
    private StockChartPanel chartPanel;
    
    public Server() {
    	
    	// gui title
        super("Stock Server");
        
        ta = new JTextArea();
        chartPanel = new StockChartPanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, 
            new JScrollPane(ta), 
            chartPanel
        );
        splitPane.setResizeWeight(0.6);
        
        setLayout(new BorderLayout());
        //add(new JScrollPane(ta), BorderLayout.CENTER);
        //add(chartPanel, BorderLayout.SOUTH);
        add(splitPane, BorderLayout.CENTER);
        
        setSize(400, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        
        loadPricesfromDatabase();
        
        // thread to control status server launch and connection
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8000)) {
                ta.append("Server launched, waiting for clients to connect...\n");
                while (true) {
                	// create new socket
                    Socket socket = serverSocket.accept();
                    ta.append("Server connection：" + socket.getInetAddress() + "\n");
                    // start clienthandler as a thread
                    new ClientHandler(socket).start();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }
    
    private class ClientHandler extends Thread {
        private final Socket socket;
        
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }
        
        // override thread run
        @Override
        public void run() {
            try (
            	// define datainputsteam and dataoutputstream
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
            ) {
                // send initial stock price to client
                output.writeDouble(PRICES[currentDay.get()]);
                ta.append("Initial stock price sending: $" + PRICES[currentDay.get()] + "\n");
                while (true) {
                	// read utf from input (write utf by client) equals button name
                    String command = input.readUTF();
                    ta.append("Received command: " + command + "\n");
                    if ("NEXT".equals(command)) {
                        // keep going until the last transaction day
                        if (currentDay.get() < PRICES.length - 1) {
                        	// atomicinteger autoincrement
                            currentDay.incrementAndGet();
                            ta.append("Update to Day" + (currentDay.get()+1) + "，Stock price: $" + PRICES[currentDay.get()] + "\n");
                            // output whether current day is last day and price
                            output.writeBoolean(currentDay.get() == PRICES.length - 1);
                            output.writeDouble(PRICES[currentDay.get()]);
                            SwingUtilities.invokeLater(() -> chartPanel.repaint());
                        } else {
                            ta.append("Last transaction day，Stock price: $" + PRICES[currentDay.get()] + "\n");
                            output.writeBoolean(true);
                            output.writeDouble(PRICES[currentDay.get()]);
                            ta.append("Transaction ends. Disconnect to server\n");
                            // jump out while loop
                            break; 
                        }
                    } else if ("BUY".equals(command)) {
                        // If the day is the last transaction day, disable buy operation
                        if (currentDay.get() == PRICES.length - 1) {
                            ta.append("Last transaction day, disabled Buy operation。\n");
                            output.writeBoolean(true);
                            output.writeDouble(PRICES[currentDay.get()]);
                            break;
                        } else {
                            ta.append("BUY operation, current Stock price: $" + PRICES[currentDay.get()] + "\n");
                            output.writeBoolean(false);
                            output.writeDouble(PRICES[currentDay.get()]);
                        }
                    } else if ("SELL".equals(command)) {
                        ta.append("SELL operation, current Stock price: $" + PRICES[currentDay.get()] + "\n");
                        output.writeDouble(PRICES[currentDay.get()]);
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
    
    private class StockChartPanel extends JPanel {
    	@Override
    	protected void paintComponent(Graphics g) {
    		super.paintComponent(g);
    		Graphics2D g2d = (Graphics2D) g;
    		int width = getWidth();
    		int height = getHeight();
    		//int padding = 25;
    		//int labelPadding = 25;
    		int paddingLeft = 50;
    		int paddingRight = 20;
    		int paddingTop = 25;
    		int paddingBottom = 25;
    		
    		// clean
    		g2d.setColor(Color.WHITE);
    		g2d.fillRect(0, 0, width, height);
    		g2d.setColor(Color.BLACK);
    		
    		// plotting axes
    		g2d.drawLine(paddingLeft, height-paddingBottom, width-paddingRight, height-paddingBottom); // x-axis
    		g2d.drawLine(paddingLeft, paddingTop, paddingLeft, height-paddingBottom);
    		
    		// axes range and scale
    		int current = currentDay.get();
            int totalDays = PRICES.length;
            
            double maxPrice = Double.MIN_VALUE;
            double minPrice = Double.MAX_VALUE;
            
            for (int i = 0; i <= current; i++) {
                if (PRICES[i] > maxPrice) maxPrice = PRICES[i];
                if (PRICES[i] < minPrice) minPrice = PRICES[i];
            }
            if (maxPrice == minPrice) {
                maxPrice += 1.0;
                minPrice -= 1.0;
            }
            
            // x-axis label
            for (int i = 0; i < totalDays; i++) {
                int x = paddingLeft + i * (width - paddingLeft - paddingRight) / (totalDays - 1);
                g2d.drawString(String.valueOf(i + 1), x - 10, height - paddingBottom + 15);
            }
            
            // y-axis label
            FontMetrics metrics = g2d.getFontMetrics();
            int ySteps = 5;
            for (int i = 0; i <= ySteps; i++) {
                double value = minPrice + (maxPrice - minPrice) * i / ySteps;
                int y = height - paddingBottom - (int) ((value - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                
                String label = String.format("%.1f", value);
                int textWidth = metrics.stringWidth(label);
                int x = paddingLeft - textWidth - 5;
                g2d.drawString(label, x, y + metrics.getAscent() / 2);
            }
            
            g2d.setColor(Color.BLUE);
            g2d.setStroke(new BasicStroke(2));
            
            for (int i = 0; i < current; i++) {
                int x1 = paddingLeft + i * (width - paddingLeft - paddingRight) / (totalDays - 1);
                int y1 = height - paddingBottom - (int) ((PRICES[i] - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                int x2 = paddingLeft + (i + 1) * (width - paddingLeft - paddingRight) / (totalDays - 1);
                int y2 = height - paddingBottom - (int) ((PRICES[i + 1] - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                g2d.draw(new Line2D.Double(x1, y1, x2, y2));
            }
            
            g2d.setColor(Color.RED);
            for (int i = 0; i <= current; i++) {
                int x = paddingLeft + i * (width - paddingLeft - paddingRight) / (totalDays - 1);
                int y = height - paddingBottom - (int) ((PRICES[i] - minPrice) * (height - paddingTop - paddingBottom) / (maxPrice - minPrice));
                g2d.fillOval(x - 3, y - 3, 6, 6);
            }
    	}
    }
    
    
    // store data from database to PRICES
    
    private void loadPricesfromDatabase() {
    	String url = "jdbc:sqlite:stock.db";
    	String sql = "SELECT stock_price FROM stock_change ORDER BY day";
    	
    	try (Connection con = DriverManager.getConnection(url);
    		 Statement stmt = con.createStatement();
    		 ResultSet rs = stmt.executeQuery(sql)) {
    		
    		List<Double> pricelist = new ArrayList<>();
    		while (rs.next()) {
    			pricelist.add(rs.getDouble("stock_price"));
    		}
    		
    		// transfer to PRICES
    		// PRICES = new double[pricelist.size()];
    		for (int i = 0; i < pricelist.size(); i++) {
    			PRICES[i] = pricelist.get(i);
    		}
    		
    	} catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Failed to load prices: " + e.getMessage());
            PRICES = new double[0]; // 防止空指针
        }
    }
    
    
    
    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 200);
    }
    
    public static void main(String[] args) {
        new Server();
    }
}