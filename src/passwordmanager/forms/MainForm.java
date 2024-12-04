package passwordmanager.forms;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.MediaTracker;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import passwordmanager.PasswordManager;
import javax.swing.*;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import net.sf.image4j.codec.ico.ICODecoder;
import passwordmanager.storage.Metadata;
import passwordmanager.tasks.AsyncTask;

public class MainForm extends javax.swing.JFrame {
    // Static reference to JTree instance
    private static JTree tree;
    // Model for the password list
    private static DefaultListModel<String> model = new DefaultListModel<>();
    
    /**
     * Constructor for MainForm class that initializes the components and sets up the UI.
     */
    public MainForm() {
        // Create the root node of the tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        
        // Populate the tree with data from PasswordManager's storage
        for (Metadata meta : PasswordManager.storage.fetchAll()) {
            DefaultMutableTreeNode website = new DefaultMutableTreeNode(meta.getKey());
            meta.decrypt();
            for (String password : meta.getData()) {
                website.add(new DefaultMutableTreeNode(password));
            }
            root.add(website);
        }
        
        // Set the tree model with the populated root node
        DefaultTreeModel model = new DefaultTreeModel(root);
        tree = new JTree(model);
        
        // Customize the appearance of the tree
        tree.setFont(new Font("Arial", Font.PLAIN, 14));
        tree.setRowHeight(26);
        
        // Set custom cell renderer and UI for the tree
        tree.setCellRenderer(new CustomTreeCellRenderer());
        tree.setUI(new CustomTreeUI());
        
        // Initialize the components of the form
        initComponents();
        
        // Set background colors and borders for the scroll pane and search field
        Color background = new Color(21, 25, 29);
        scrollPane.getViewport().getView().setBackground(background);
        scrollPane.setBorder(null);
        txfSearch.setBorder(null);
        PasswordManager.AddGhostText(txfSearch, "Search");
        tree.setBackground(scrollPane.getViewport().getView().getBackground());
        addFrm.getContentPane().setBackground(new Color(33, 37, 41));
        
        // Create and customize the popup menu for tree nodes
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(background);
        JMenuItem copyItem = new JMenuItem("Copy");
        copyItem.setOpaque(false);
        copyItem.setForeground(Color.WHITE);
        JMenuItem delItem = new JMenuItem("Delete");
        delItem.setOpaque(false);
        delItem.setForeground(Color.WHITE);
        
        // Add action listener for the delete menu item
        delItem.addActionListener(e -> {
            TreePath[] selectedPaths = tree.getSelectionPaths();
            if (selectedPaths == null) return; // No selection, nothing to remove
            
            for (TreePath path : selectedPaths) {
                Object[] nodes = path.getPath();
                if (nodes.length != 3) continue; // Ensure correct node depth

                // Remove the node from storage
                PasswordManager.storage.remove(nodes[1].toString(), nodes[2].toString());

                // Get the selected node from the tree model
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

                // Check if the parent has no children left and remove it if necessary
                if (parent.getChildCount() == 0) {
                    model.removeNodeFromParent(parent);
                }
            }

            // Reload or refresh the tree after deletion
            reload();
        });

        // Add action listener for the copy menu item
        copyItem.addActionListener(e -> {
            Object[] path = tree.getSelectionPath().getPath();
            if (path.length != 3) return;
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(path[2].toString());
            clipboard.setContents(selection, null);
        });
        
        // Add menu items to menu
        menu.add(copyItem);
        menu.add(delItem);
        
        // add listener to show menu when branch in tree is clicked
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1) {
                    if (tree.getSelectionCount() > 0)
                        menu.show(tree, e.getX(), e.getY());
                }
            }
        });
    }

    private static class CustomTreeCellRenderer extends DefaultTreeCellRenderer {
        private ImageIcon password;
        private ImageIcon defaultIcon;
        private final Map<String, ImageIcon> iconCache = new ConcurrentHashMap<>();

        public CustomTreeCellRenderer() {
            // Preload resources from embeded resources
            password = GetResource("key.png");
            defaultIcon = GetResource("default_icon.png");
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

            // Set custom icons based on the node's properties
            if (row == 0) setIcon(null);
            else if (expanded || !leaf) setIcon(getIcon(value.toString()));
            else setIcon(password);

            return this;
        }
        
        @Override
        public Color getBackgroundNonSelectionColor() {
            return null;
        }

        @Override
        public Color getBackgroundSelectionColor() {
            return new Color(0,0,255,100);
        }

        @Override
        public Color getBackground() {
            return null;
        }
        
        @Override
        public Color getForeground() {
            return Color.WHITE.darker();
        }
        
        private ImageIcon getIcon(String site) {
            // Check if the icon is already cached
            ImageIcon icon = iconCache.get(site);
            if (icon != null) return icon;
            // Asynchronously load the favicon if it's not in the cache
            new AsyncTask(site, () -> {
                ImageIcon img;
                try {
                    // Log the loading process
                    System.out.println("Loading ImageIcon from " + site);
                    // Create a URL for the site's favicon
                    URL url = URI.create("http://" + site + "/favicon.ico").toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    // Set the request method and properties for the connection
                    connection.setRequestMethod("GET");
                    connection.setRequestProperty("Content-Type", "image/x-icon");
                    connection.setInstanceFollowRedirects(true);
                    // Handle redirects if the site has moved
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                        responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                        // Get the new location from the response header
                        String newUrl = connection.getHeaderField("Location");
                        // Open a connection to the new URL
                        connection = (HttpURLConnection) URI.create(newUrl).toURL().openConnection();
                        connection.setRequestMethod("GET");
                        connection.setRequestProperty("Content-Type", "image/x-icon");
                    }
                    // Get the input stream for the favicon
                    InputStream in = connection.getInputStream();
                    System.out.println("Found " 
                        + in.available() 
                        + " bytes from " 
                        + connection.getURL() 
                        + " responded with " 
                        + connection.getResponseCode());
                    // Decode the ICO file and find the most suitable image
                    List<BufferedImage> images = ICODecoder.read(in);
                    BufferedImage retrived = null;
                    for (BufferedImage i : images) {
                        if (retrived == null) {
                            retrived = i;
                            continue;
                        }
                        // Prefer the smallest image if multiple are available
                        int size = i.getWidth();
                        if (size < retrived.getWidth())
                            retrived = i;
                    }
                    // Resize the image to 16x16 pixels if it's larger
                    if (retrived.getWidth() > 16) {
                        System.out.println("Reformatting image");
                        BufferedImage resized = new BufferedImage(16, 16, retrived.getType());
                        Graphics2D g2d = resized.createGraphics();
                        g2d.drawImage(retrived, 0, 0, 16, 16, null);
                        g2d.dispose();
                        retrived = resized;
                    }
                    // Create an ImageIcon from the BufferedImage
                    img = new ImageIcon(retrived);
                    in.close();
                    connection.disconnect();
                    // Log the status of the ImageIcon loading
                    System.out.print("ImageIcon status: ");
                    switch (img.getImageLoadStatus()) {
                        case MediaTracker.LOADING -> System.out.println("LOADING");
                        case MediaTracker.ABORTED -> System.out.println("ABORTED");
                        case MediaTracker.ERRORED -> System.out.println("ERRORED");
                        case MediaTracker.COMPLETE -> System.out.println("COMPLETE");
                    }
                } catch (IOException e) {
                    // Handle errors by using a default icon
                    System.out.println("Failed to reach site icon\ncontinuing");
                    img = defaultIcon;
                }
                // Cache the loaded icon and repaint the tree to display it
                iconCache.put(site, img);
                tree.repaint();
            });
            // Return a default icon while the actual icon is loading
            return defaultIcon;
        }
    }
    
    private static class CustomTreeUI extends BasicTreeUI {
        @Override
        protected void paintVerticalPartOfLeg(Graphics g, Rectangle clipBounds, Insets insets, TreePath path) {
            // Override to paint nothing
        }

        @Override
        protected void paintHorizontalPartOfLeg(Graphics g, Rectangle clipBounds, Insets insets, Rectangle bounds,
                                                     TreePath path, int row, boolean isExpanded, boolean hasBeenExpanded,
                                                     boolean isLeaf) {
        }
        @Override
        protected void paintExpandControl(Graphics g, Rectangle clipBounds, Insets insets, Rectangle bounds,
                                          TreePath path, int row, boolean isExpanded, boolean hasBeenExpanded,
                                          boolean isLeaf) {
        }
    }
    
    private static ImageIcon GetResource(String filename){
        return new ImageIcon(PasswordManager.class.getClassLoader().getResource("resources/" + filename));
    }
    
    public void reload() {
        // Create a new root node for the tree
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        // Fetch all stored metadata from the password manager
        for (Metadata meta : PasswordManager.storage.fetchAll()) {
            // Create a new tree node for each website (or key) in the metadata
            DefaultMutableTreeNode website = new DefaultMutableTreeNode(meta.getKey());
            // Decrypt the metadata to access the stored passwords
            meta.decrypt();
            // Add each password as a child node under the corresponding website node
            for (String password : meta.getData()) {
                website.add(new DefaultMutableTreeNode(password));
            }
            // Add the website node to the root node
            root.add(website);
        }
        // Create a new tree model using the root node
        DefaultTreeModel model = new DefaultTreeModel(root);
        // Set the new model to the tree, effectively reloading the data
        tree.setModel(model);
    }

    

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        addFrm = new javax.swing.JFrame();
        lblTitle = new javax.swing.JLabel();
        txfWebsite = new javax.swing.JTextField();
        lblWebsite = new javax.swing.JLabel();
        lblPasswords = new javax.swing.JLabel();
        txfPassword = new javax.swing.JTextField();
        jScrollPane2 = new javax.swing.JScrollPane();
        passList = new javax.swing.JList<>(model);
        btnAddPass = new javax.swing.JButton();
        statusFrm = new javax.swing.JFrame();
        jPanel1 = new javax.swing.JPanel();
        lblTitle2 = new javax.swing.JLabel();
        lblStatus = new javax.swing.JLabel();
        btnCancel = new javax.swing.JButton();
        btnHelp = new javax.swing.JButton();
        panel = new javax.swing.JPanel();
        scrollPane = new javax.swing.JScrollPane(tree);
        btnAdd = new javax.swing.JButton();
        txfSearch = new javax.swing.JTextField();
        btnStatus = new javax.swing.JButton();

        addFrm.setAlwaysOnTop(true);
        addFrm.setBackground(new java.awt.Color(33, 37, 41));
        addFrm.setMinimumSize(new java.awt.Dimension(350, 325));
        addFrm.setResizable(false);

        lblTitle.setFont(new java.awt.Font("Segoe UI Black", 0, 18)); // NOI18N
        lblTitle.setForeground(new java.awt.Color(255, 255, 255));
        lblTitle.setText("ADD PASSWORD");

        txfWebsite.setBackground(new java.awt.Color(49, 59, 75));
        txfWebsite.setForeground(new java.awt.Color(255, 255, 255));
        txfWebsite.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(102, 102, 102)));

        lblWebsite.setForeground(new java.awt.Color(204, 255, 255));
        lblWebsite.setText("Website name");

        lblPasswords.setForeground(new java.awt.Color(204, 255, 255));
        lblPasswords.setText("Passwords (press enter to add to list)");

        txfPassword.setBackground(new java.awt.Color(49, 59, 75));
        txfPassword.setForeground(new java.awt.Color(255, 255, 255));
        txfPassword.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(102, 102, 102)));
        txfPassword.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txfPasswordActionPerformed(evt);
            }
        });

        passList.setBackground(new java.awt.Color(49, 59, 75));
        passList.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(102, 102, 102)));
        passList.setForeground(new java.awt.Color(255, 255, 255));
        jScrollPane2.setViewportView(passList);

        btnAddPass.setBackground(new java.awt.Color(49, 59, 75));
        btnAddPass.setForeground(new java.awt.Color(204, 255, 255));
        btnAddPass.setText("ADD");
        btnAddPass.setBorder(null);
        btnAddPass.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddPassActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout addFrmLayout = new javax.swing.GroupLayout(addFrm.getContentPane());
        addFrm.getContentPane().setLayout(addFrmLayout);
        addFrmLayout.setHorizontalGroup(
            addFrmLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, addFrmLayout.createSequentialGroup()
                .addContainerGap(103, Short.MAX_VALUE)
                .addComponent(lblTitle)
                .addGap(95, 95, 95))
            .addGroup(addFrmLayout.createSequentialGroup()
                .addGap(137, 137, 137)
                .addComponent(btnAddPass, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, addFrmLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(addFrmLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane2)
                    .addComponent(txfWebsite, javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txfPassword, javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, addFrmLayout.createSequentialGroup()
                        .addGroup(addFrmLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblWebsite)
                            .addComponent(lblPasswords))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        addFrmLayout.setVerticalGroup(
            addFrmLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(addFrmLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lblTitle)
                .addGap(3, 3, 3)
                .addComponent(lblWebsite)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txfWebsite, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblPasswords)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txfPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 123, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnAddPass, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        statusFrm.setAlwaysOnTop(true);
        statusFrm.setMinimumSize(new java.awt.Dimension(312, 231));
        statusFrm.setUndecorated(true);
        statusFrm.setResizable(false);

        jPanel1.setBackground(new java.awt.Color(33, 37, 41));

        lblTitle2.setFont(new java.awt.Font("Segoe UI Black", 1, 18)); // NOI18N
        lblTitle2.setForeground(new java.awt.Color(255, 255, 255));
        lblTitle2.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblTitle2.setText("STATUS");
        lblTitle2.setFocusable(false);

        lblStatus.setForeground(new java.awt.Color(255, 255, 255));
        lblStatus.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lblStatus.setText("No extention connected");
        lblStatus.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        btnCancel.setBackground(new java.awt.Color(51, 51, 51));
        btnCancel.setForeground(new java.awt.Color(255, 255, 255));
        btnCancel.setText("Cancel");
        btnCancel.setBorder(null);
        btnCancel.setFocusable(false);
        btnCancel.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCancelActionPerformed(evt);
            }
        });

        btnHelp.setBackground(new java.awt.Color(49, 59, 75));
        btnHelp.setForeground(new java.awt.Color(255, 255, 255));
        btnHelp.setText("Help");
        btnHelp.setBorder(null);
        btnHelp.setFocusable(false);
        btnHelp.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnHelpActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(lblTitle2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnHelp, javax.swing.GroupLayout.PREFERRED_SIZE, 153, javax.swing.GroupLayout.PREFERRED_SIZE))
            .addComponent(lblStatus, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(20, 20, 20)
                .addComponent(lblTitle2, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblStatus, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnHelp, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnCancel, javax.swing.GroupLayout.PREFERRED_SIZE, 37, javax.swing.GroupLayout.PREFERRED_SIZE)))
        );

        javax.swing.GroupLayout statusFrmLayout = new javax.swing.GroupLayout(statusFrm.getContentPane());
        statusFrm.getContentPane().setLayout(statusFrmLayout);
        statusFrmLayout.setHorizontalGroup(
            statusFrmLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        statusFrmLayout.setVerticalGroup(
            statusFrmLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setForeground(java.awt.Color.white);

        panel.setBackground(new java.awt.Color(33, 37, 41));
        panel.setPreferredSize(new java.awt.Dimension(700, 500));

        scrollPane.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));

        btnAdd.setBackground(new java.awt.Color(49, 59, 75));
        btnAdd.setForeground(new java.awt.Color(255, 255, 255));
        btnAdd.setText("Add");
        btnAdd.setBorder(null);
        btnAdd.setFocusable(false);
        btnAdd.setPreferredSize(new java.awt.Dimension(80, 30));
        btnAdd.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddActionPerformed(evt);
            }
        });

        txfSearch.setBackground(new java.awt.Color(49, 59, 75));
        txfSearch.setForeground(new java.awt.Color(255, 255, 255));
        txfSearch.setHorizontalAlignment(javax.swing.JTextField.CENTER);

        btnStatus.setBackground(new java.awt.Color(51, 51, 51));
        btnStatus.setForeground(new java.awt.Color(255, 255, 255));
        btnStatus.setText("Status");
        btnStatus.setBorder(null);
        btnStatus.setBorderPainted(false);
        btnStatus.setFocusable(false);
        btnStatus.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStatusActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout panelLayout = new javax.swing.GroupLayout(panel);
        panel.setLayout(panelLayout);
        panelLayout.setHorizontalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(btnAdd, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 118, Short.MAX_VALUE)
                .addComponent(txfSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 315, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 118, Short.MAX_VALUE)
                .addComponent(btnStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 57, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
            .addComponent(scrollPane, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        panelLayout.setVerticalGroup(
            panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnAdd, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnStatus, javax.swing.GroupLayout.PREFERRED_SIZE, 38, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txfSearch, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(scrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(panel, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void txfPasswordActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txfPasswordActionPerformed
        model.addElement(txfPassword.getText());
        txfPassword.setText("");
    }//GEN-LAST:event_txfPasswordActionPerformed

    private void btnAddPassActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddPassActionPerformed
        String website = txfWebsite.getText();
        // Validate user imput
        if (website.isBlank() || !website.matches("^[a-zA-Z0-9.]+$")) return;
        if (model.isEmpty()) return;
        // Put new password in storage
        PasswordManager.storage.put(
                new Metadata(website,false,
                        Arrays.stream(model.toArray()).map((o) -> o.toString())
                                .toArray(String[]::new)));
        // hide window
        addFrm.setVisible(false);
        // update tree
        reload();
        // clear data in feilds
        txfWebsite.setText("");
        model.clear();
    }//GEN-LAST:event_btnAddPassActionPerformed

    private void btnAddActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddActionPerformed
        addFrm.setLocationRelativeTo(this);
        addFrm.setTitle(PasswordManager.NAME + " - Add Password");
        addFrm.setVisible(true);
    }//GEN-LAST:event_btnAddActionPerformed

    private void btnStatusActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStatusActionPerformed
        statusFrm.setLocationRelativeTo(this);
        statusFrm.setVisible(true);
    }//GEN-LAST:event_btnStatusActionPerformed

    private void btnCancelActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCancelActionPerformed
        statusFrm.setVisible(false);
    }//GEN-LAST:event_btnCancelActionPerformed

    private void btnHelpActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnHelpActionPerformed
        // Show README
        try {
            // Get README file
            File file = Path.of("")
                .toAbsolutePath()
                    .resolve("README.pdf")
                    .toFile();
            // if the file hasent been created yet. Create it from emmbeded resources
            if (!file.exists()){
                file.createNewFile();
                InputStream iStream = PasswordManager.class.getResourceAsStream("/README.pdf");
                OutputStream oStream = new FileOutputStream(file);
                oStream.write(iStream.readAllBytes());
                iStream.close();
                oStream.flush();
                oStream.close();
            }
            // hide window
            statusFrm.setVisible(false);
            // Show the pdf in the broswer
            Desktop.getDesktop().browse(file.toURI());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }//GEN-LAST:event_btnHelpActionPerformed
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JFrame addFrm;
    private javax.swing.JButton btnAdd;
    private javax.swing.JButton btnAddPass;
    private javax.swing.JButton btnCancel;
    private javax.swing.JButton btnHelp;
    private javax.swing.JButton btnStatus;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lblPasswords;
    public javax.swing.JLabel lblStatus;
    private javax.swing.JLabel lblTitle;
    private javax.swing.JLabel lblTitle2;
    private javax.swing.JLabel lblWebsite;
    private javax.swing.JPanel panel;
    private javax.swing.JList<String> passList;
    private javax.swing.JScrollPane scrollPane;
    private javax.swing.JFrame statusFrm;
    private javax.swing.JTextField txfPassword;
    private javax.swing.JTextField txfSearch;
    private javax.swing.JTextField txfWebsite;
    // End of variables declaration//GEN-END:variables
}