package passwordmanager;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import passwordmanager.forms.LoginForm;
import java.util.Random;
import javax.swing.JTextField;
import passwordmanager.forms.MainForm;
import passwordmanager.network.Extension;
import passwordmanager.network.Server;
import passwordmanager.storage.Storage;

/**
 * @author Kean Buyst
 */
public class PasswordManager {
    // constant variables
    public static final String NAME = "CyferKey";
    private static final String OPTIONS = 
            "0123456789@qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";
    
    // global varibles
    public static Storage storage;
    public static String key;
    public static String identifier;

    public static void main(String[] args) {
        // create form and set attributes
        LoginForm loginFrm = new LoginForm();
        loginFrm.pack();
        loginFrm.setLocationRelativeTo(null);
        loginFrm.setTitle(NAME + " - Login");
        loginFrm.setVisible(true);
    }
    
    public static void INIT(String username,String password){
        // initilize storage
        storage = new Storage(username);
        // set fields
        key = password;
        identifier = username;
        // create form and set attributes
        MainForm form = new MainForm();
        form.pack();
        form.setLocationRelativeTo(null);
        form.setTitle(NAME);
        // start communication server for extension
        Server server = new Extension(form);
        server.start();
        // add form close event listener
        form.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                server.close();
                storage.save();
                
            }
        });
        
        form.setVisible(true);     
    }
    
    public static String Cyfer(String password){
        // simple encryption using the XNOR oprator
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (char b : password.toCharArray())
        {
            if (index == key.length()) index = 0;
            builder.append((char) ((short) b ^ key.charAt(index)));
            index++;
        }
        return builder.toString();
    }
    
    public static String GenPassword(int length){
        // create password from given digits and numbers randomly
        Random random = new Random();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < length;i++){
            int pos = random.nextInt(OPTIONS.length());
            builder.append(OPTIONS.charAt(pos));
        }
        return builder.toString();
    }
    
    public static void AddGhostText(JTextField textField, String ghostText) {
         // Set the initial text color and ghost text
        textField.setForeground(Color.GRAY);
        textField.setText(ghostText);

        // Add a focus listener to handle focus events
        textField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (textField.getText().equals(ghostText)) {
                    textField.setText("");
                    textField.setForeground(Color.BLACK);
                }
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (textField.getText().isEmpty()) {
                    textField.setForeground(Color.GRAY);
                    textField.setText(ghostText);
                }
            }
        });
    }
}
