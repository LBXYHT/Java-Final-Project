package network;

import java.io.*;
import java.net.*;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server extends JFrame {
    // Record of stock price array
    private static final int[] PRICES = {10, 9, 11, 12, 10};
    // use AtomicInteger to manage current transaction day（start from 0）
    private static final AtomicInteger currentDay = new AtomicInteger(0);
    // textarea
    private JTextArea ta;
    
    public Server() {
    	// ui title
        super("Stock Server");
        ta = new JTextArea();
        add(new JScrollPane(ta));
        setSize(400, 200);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setVisible(true);
        
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
    
    public static void main(String[] args) {
        new Server();
    }
}