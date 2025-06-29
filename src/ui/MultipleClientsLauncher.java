package ui;

import javax.swing.SwingUtilities;

public class MultipleClientsLauncher {
    public static void main(String[] args) {
        int numberOfClients = 3; // Spécifiez le nombre de clients à lancer

        for (int i = 1; i <= numberOfClients; i++) {
            final int clientId = i;
            SwingUtilities.invokeLater(() -> {
                Client client = new Client();
                client.setTitle("Restaurant Client " + clientId);
                client.setVisible(true);
            });
        }
    }
}
