package ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;
import java.util.UUID;

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

public class Client extends JFrame {
    private String clientId;
    private JList<String> menuList;
    private DefaultListModel<String> menuListModel;
    private JTextField commentTextField;
    private JTextField tableNumberTextField;
    private JButton sendOrderButton;
    private JTextArea notificationTextArea;
    private Session session;
    private MessageProducer orderProducer;
    private MessageConsumer menuConsumer;
    private MessageConsumer notificationConsumer;

    public Client() {
        // Génère un identifiant unique pour ce client
        this.clientId = UUID.randomUUID().toString();

        setTitle("Restaurant Client");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Menu area
        menuListModel = new DefaultListModel<>();
        menuList = new JList<>(menuListModel);
        add(new JScrollPane(menuList), BorderLayout.NORTH);

        // Order input area
        JPanel orderPanel = new JPanel(new GridLayout(4, 2));
        commentTextField = new JTextField();
        tableNumberTextField = new JTextField();
        sendOrderButton = new JButton("Send Order");

        orderPanel.add(new JLabel("Select Item:"));
        orderPanel.add(menuList);
        orderPanel.add(new JLabel("Comment:"));
        orderPanel.add(commentTextField);
        orderPanel.add(new JLabel("Table Number:"));
        orderPanel.add(tableNumberTextField);
        orderPanel.add(sendOrderButton);
        add(orderPanel, BorderLayout.CENTER);

        // Notifications area
        notificationTextArea = new JTextArea();
        notificationTextArea.setEditable(false);
        add(new JScrollPane(notificationTextArea), BorderLayout.SOUTH);

        sendOrderButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendOrder();
            }
        });

        // Initialize JMS
        initializeJMS();
    }

    private void initializeJMS() {
        try {
            // Set JNDI properties programmatically
            Properties props = new Properties();
            props.setProperty("java.naming.factory.initial", "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
            props.setProperty("java.naming.provider.url", "tcp://localhost:61616");
            props.setProperty("queue.OrderQueue", "OrderQueue");
            props.setProperty("queue.NotificationQueue", "NotificationQueue");
            props.setProperty("topic.MenuTopic", "MenuTopic");

            InitialContext ctx = new InitialContext(props);
            ConnectionFactory connectionFactory = (ConnectionFactory) ctx.lookup("ConnectionFactory");
            Topic menuTopic = (Topic) ctx.lookup("MenuTopic");
            Queue orderQueue = (Queue) ctx.lookup("OrderQueue");
            Queue notificationQueue = (Queue) ctx.lookup("NotificationQueue");

            Connection connection = connectionFactory.createConnection();
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            orderProducer = session.createProducer(orderQueue);
            menuConsumer = session.createConsumer(menuTopic);
            notificationConsumer = session.createConsumer(notificationQueue, "clientId = '" + clientId + "'");

            connection.start();

            menuConsumer.setMessageListener(message -> {
                try {
                    TextMessage textMessage = (TextMessage) message;
                    String[] menuItems = textMessage.getText().split(", ");
                    menuListModel.clear();
                    for (String item : menuItems) {
                        menuListModel.addElement(item);
                    }
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            });

            notificationConsumer.setMessageListener(message -> {
                try {
                    TextMessage textMessage = (TextMessage) message;
                    notificationTextArea.append(textMessage.getText() + "\n");
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            });

        } catch (NamingException | JMSException e) {
            e.printStackTrace();
        }
    }

    private void sendOrder() {
        try {
            String selectedItem = menuList.getSelectedValue();
            String comment = commentTextField.getText();
            String tableNumber = tableNumberTextField.getText();

            if (selectedItem == null || tableNumber.isEmpty()) {
                notificationTextArea.append("Please select an item and enter the table number.\n");
                return;
            }

            String order = clientId + " Table: " + tableNumber + ", Item: " + selectedItem + ", Comment: " + comment;
            TextMessage orderMessage = session.createTextMessage(order);
            orderMessage.setStringProperty("clientId", clientId);
            orderProducer.send(orderMessage);

            commentTextField.setText("");
            tableNumberTextField.setText("");
        } catch (JMSException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Client client = new Client();
            client.setVisible(true);
        });
    }
}
