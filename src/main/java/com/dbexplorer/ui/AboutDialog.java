package com.dbexplorer.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * About dialog with product icon loaded from classpath and app details.
 */
public class AboutDialog extends JDialog {

    private static final String ICON_PATH = "/icons/db-explorer-icon.png";

    public AboutDialog(Frame owner) {
        super(owner, "About DB Explorer", true);
        setResizable(false);
        initUI();
        pack();
        setLocationRelativeTo(owner);
    }

    private void initUI() {
        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));

        // --- Icon from classpath resource ---
        ImageIcon icon = loadIcon();
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

        // --- Text panel ---
        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel titleLabel = new JLabel("DB Explorer");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel editionLabel = new JLabel("Community Edition");
        editionLabel.setFont(editionLabel.getFont().deriveFont(Font.ITALIC, 13f));
        editionLabel.setForeground(new Color(100, 140, 200));
        editionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel versionLabel = new JLabel("Version 1.0.0");
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 12f));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel releaseLabel = new JLabel("Release Date: March 22, 2026");
        releaseLabel.setFont(releaseLabel.getFont().deriveFont(Font.PLAIN, 11f));
        releaseLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(280, 2));
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel builtByPrefix = new JLabel("Built by ");
        builtByPrefix.setFont(builtByPrefix.getFont().deriveFont(Font.PLAIN, 12f));
        builtByPrefix.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Clickable LinkedIn link
        JPanel builtByPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        builtByPanel.setOpaque(false);
        JLabel prefixLbl = new JLabel("Built by ");
        prefixLbl.setFont(prefixLbl.getFont().deriveFont(Font.PLAIN, 12f));
        JLabel linkLabel = new JLabel("<html><a href=''>Ashish Srivastava</a></html>");
        linkLabel.setFont(linkLabel.getFont().deriveFont(Font.PLAIN, 12f));
        linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        linkLabel.setToolTipText("https://www.linkedin.com/in/ashishsriv/");
        linkLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                try {
                    Desktop.getDesktop().browse(new java.net.URI("https://www.linkedin.com/in/ashishsriv/"));
                } catch (Exception ignored) {}
            }
        });
        builtByPanel.add(prefixLbl);
        builtByPanel.add(linkLabel);
        builtByPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel companyLabel = new JLabel("\u00a9 2026 Adept Software");
        companyLabel.setFont(companyLabel.getFont().deriveFont(Font.BOLD, 12f));
        companyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel locationLabel = new JLabel("Vancouver, Canada");
        locationLabel.setFont(locationLabel.getFont().deriveFont(Font.PLAIN, 11f));
        locationLabel.setForeground(Color.GRAY);
        locationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel rightsLabel = new JLabel("All rights reserved.");
        rightsLabel.setFont(rightsLabel.getFont().deriveFont(Font.PLAIN, 10f));
        rightsLabel.setForeground(Color.GRAY);
        rightsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(editionLabel);
        textPanel.add(Box.createVerticalStrut(6));
        textPanel.add(versionLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(releaseLabel);
        textPanel.add(Box.createVerticalStrut(12));
        textPanel.add(sep);
        textPanel.add(Box.createVerticalStrut(12));
        textPanel.add(builtByPanel);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(companyLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(locationLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(rightsLabel);

        // --- OK button ---
        JButton okBtn = new JButton("OK");
        okBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        okBtn.addActionListener(e -> dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.add(okBtn);

        content.add(iconLabel, BorderLayout.NORTH);
        content.add(textPanel, BorderLayout.CENTER);
        content.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(okBtn);
    }

    /**
     * Load the product icon from classpath. Returns a scaled 96x96 icon.
     */
    private ImageIcon loadIcon() {
        try (InputStream is = getClass().getResourceAsStream(ICON_PATH)) {
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                Image scaled = img.getScaledInstance(96, 96, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
        } catch (Exception ignored) {}
        // Fallback: simple text icon
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }

    /**
     * Load the product icon as an Image (for window icon use).
     */
    public static Image loadWindowIcon() {
        try (InputStream is = AboutDialog.class.getResourceAsStream(ICON_PATH)) {
            if (is != null) return ImageIO.read(is);
        } catch (Exception ignored) {}
        return null;
    }
}
