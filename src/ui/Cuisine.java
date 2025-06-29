package ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.swing.JButton;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import jakarta.jms.Topic;

public class Cuisine extends JFrame {
    private JTextField menuItemTextField;
    private JTextField priceTextField;
    private DefaultListModel<String> menuListModel;
    private JList<String> menuList;
    private DefaultListModel<String> orderListModel;
    private JList<String> orderList;
    private JButton addMenuItemButton;
    private JButton sendMenuButton;
    private JButton deleteMenuItemButton;
    private JButton markAsPreparedButton;
    private Session session;
    private MessageConsumer orderConsumer;
    private MessageProducer notificationProducer;
    private MessageProducer menuPublisher;

    public Cuisine() {
        setTitle("Restaurant Cuisine");
        setSize(600, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Menu input area
        menuItemTextField = new JTextField();
        priceTextField = new JTextField();
        menuListModel = new DefaultListModel<>();
        menuList = new JList<>(menuListModel);
        addMenuItemButton = new JButton("Add Menu Item");
        sendMenuButton = new JButton("Send Menu");
        deleteMenuItemButton = new JButton("Delete Selected Item");
        JPanel menuPanel = new JPanel(new GridLayout(6, 1));
        menuPanel.add(new JLabel("Menu Item:"));
        menuPanel.add(menuItemTextField);
        menuPanel.add(new JLabel("Price:"));
        menuPanel.add(priceTextField);
        menuPanel.add(addMenuItemButton);
        menuPanel.add(sendMenuButton);
        menuPanel.add(deleteMenuItemButton);
        JPanel menuContainer = new JPanel(new BorderLayout());
        menuContainer.add(menuPanel, BorderLayout.NORTH);
        menuContainer.add(new JScrollPane(menuList), BorderLayout.CENTER);
        add(menuContainer, BorderLayout.NORTH);

        // Order area
        orderListModel = new DefaultListModel<>();
        orderList = new JList<>(orderListModel);
        markAsPreparedButton = new JButton("Mark as Prepared");
        JPanel orderPanel = new JPanel(new BorderLayout());
        orderPanel.add(new JScrollPane(orderList), BorderLayout.CENTER);
        orderPanel.add(markAsPreparedButton, BorderLayout.SOUTH);
        add(orderPanel, BorderLayout.CENTER);

        addMenuItemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                addMenuItem();
            }
        });

        sendMenuButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMenu();
            }
        });

        deleteMenuItemButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteMenuItem();
            }
        });

        markAsPreparedButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                markOrderAsPrepared();
            }
        });

        // Initialize JMS
        initializeJMS();
    }

    private void initializeJMS() {
        try {
            Properties props = new Properties();
            props.setProperty("java.naming.factory.initial", "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            props.setProperty("java.naming.provider.url", "tcp://localhost:61616");
            props.setProperty("queue.OrderQueue", "OrderQueue");
            props.setProperty("queue.NotificationQueue", "NotificationQueue");
            props.setProperty("topic.MenuTopic", "MenuTopic");

            InitialContext ctx = new InitialContext(props);
            ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
            Queue orderQueue = (Queue) ctx.lookup("OrderQueue");
            Queue notificationQueue = (Queue) ctx.lookup("NotificationQueue");
            Topic menuTopic = (Topic) ctx.lookup("MenuTopic");

            Connection connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            orderConsumer = session.createConsumer(orderQueue);
            notificationProducer = session.createProducer(notificationQueue);
            menuPublisher = session.createProducer(menuTopic);

            connection.start();

            orderConsumer.setMessageListener(message -> {
                try {
                    TextMessage textMessage = (TextMessage) message;
                    String order = textMessage.getText();
                    orderListModel.addElement(order);
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            });

        } catch (NamingException | JMSException e) {
            e.printStackTrace();
        }
    }

    private void addMenuItem() {
        String menuItem = menuItemTextField.getText();
        String price = priceTextField.getText();
        if (!menuItem.isEmpty() && !price.isEmpty()) {
            menuListModel.addElement(menuItem + " - DA" + price);
            menuItemTextField.setText("");
            priceTextField.setText("");
        }
    }

    private void sendMenu() {
        try {
            StringBuilder menu = new StringBuilder();
            for (int i = 0; i < menuListModel.size(); i++) {
                menu.append(menuListModel.getElementAt(i));
                if (i < menuListModel.size() - 1) {
                    menu.append(", ");
                }
            }
            TextMessage menuMessage = session.createTextMessage(menu.toString());
            menuPublisher.send(menuMessage);
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private void deleteMenuItem() {
        try {
            String selectedItem = menuList.getSelectedValue();
            if (selectedItem != null) {
                menuListModel.removeElement(selectedItem);

                StringBuilder updatedMenu = new StringBuilder();
                for (int i = 0; i < menuListModel.size(); i++) {
                    updatedMenu.append(menuListModel.getElementAt(i));
                    if (i < menuListModel.size() - 1) {
                        updatedMenu.append(", ");
                    }
                }

                TextMessage menuMessage = session.createTextMessage(updatedMenu.toString());
                menuPublisher.send(menuMessage);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    private void markOrderAsPrepared() {
        try {
            String selectedOrder = orderList.getSelectedValue();
            if (selectedOrder != null) {
                String[] parts = selectedOrder.split(" ", 4);
                if (parts.length == 4) {
                    String clientId = parts[0];
                    String tableNumber = parts[2];
                    String notification = "Table " + tableNumber + ", votre plat est prêt. Bon appétit!";
                    
                    TextMessage notificationMessage = session.createTextMessage(notification);
                    notificationMessage.setStringProperty("clientId", clientId);
                    notificationProducer.send(notificationMessage);
                }
                orderListModel.removeElement(selectedOrder);
            }
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Cuisine cuisine = new Cuisine();
            cuisine.setVisible(true);
        });
    }
}
