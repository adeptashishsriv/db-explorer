package com.dbexplorer.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/**
 * About dialog with product icon loaded from classpath and app details.
 */
public class AboutDialog extends JDialog {

    private static final String ICON_PATH = "/icons/db-explorer-icon.png";

    /** Reads a property from the filtered app.properties resource. Falls back to the given default. */
    private static String loadAppProperty(String key, String fallback) {
        try (InputStream is = AboutDialog.class.getResourceAsStream("/app.properties")) {
            if (is != null) {
                java.util.Properties props = new java.util.Properties();
                props.load(is);
                String v = props.getProperty(key);
                if (v != null && !v.isBlank()) return v;
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private static String loadVersion() {
        return loadAppProperty("app.version", "Unknown");
    }

    private static String loadBuildDate() {
        return loadAppProperty("app.build.date", "Unknown");
    }

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

        JLabel editionLabel = new JLabel("Professional Community Edition");
        editionLabel.setFont(editionLabel.getFont().deriveFont(Font.ITALIC, 13f));
        editionLabel.setForeground(new Color(60, 160, 120));
        editionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel taglineLabel = new JLabel("Enterprise-grade features. Zero cost.");
        taglineLabel.setFont(taglineLabel.getFont().deriveFont(Font.BOLD, 11f));
        taglineLabel.setForeground(new Color(60, 160, 120));
        taglineLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel versionLabel = new JLabel("Version " + loadVersion());
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 12f));
        versionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel releaseLabel = new JLabel("Release Date: " + loadBuildDate());
        releaseLabel.setFont(releaseLabel.getFont().deriveFont(Font.PLAIN, 11f));
        releaseLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(280, 2));
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel companyLabel = new JLabel("\u00a9 2026 Astro Adept AI Labs");
        companyLabel.setFont(companyLabel.getFont().deriveFont(Font.BOLD, 12f));
        companyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // JLabel locationLabel = new JLabel("Vancouver, Canada");
        // locationLabel.setFont(locationLabel.getFont().deriveFont(Font.PLAIN, 11f));
        // locationLabel.setForeground(Color.GRAY);
        // locationLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel rightsLabel = new JLabel("All rights reserved.");
        rightsLabel.setFont(rightsLabel.getFont().deriveFont(Font.PLAIN, 10f));
        rightsLabel.setForeground(Color.GRAY);
        rightsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(editionLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(taglineLabel);
        textPanel.add(Box.createVerticalStrut(6));
        textPanel.add(versionLabel);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(releaseLabel);
        textPanel.add(Box.createVerticalStrut(12));
        textPanel.add(sep);
        textPanel.add(Box.createVerticalStrut(12));
        textPanel.add(companyLabel);
        textPanel.add(Box.createVerticalStrut(2));
        // textPanel.add(locationLabel);
        // textPanel.add(Box.createVerticalStrut(2));
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
     * Load the product icon from classpath. Returns a 96x96 rounded-square icon.
     */
    private ImageIcon loadIcon() {
        try (InputStream is = getClass().getResourceAsStream(ICON_PATH)) {
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                return new ImageIcon(roundedSquare(img, 96, 20));
            }
        } catch (Exception ignored) {}
        return new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
    }

    /**
     * Load the product icon as an Image with rounded corners
     * (for window icon and welcome panel use).
     */
    public static Image loadWindowIcon() {
        try (InputStream is = AboutDialog.class.getResourceAsStream(ICON_PATH)) {
            if (is != null) return roundedSquare(ImageIO.read(is), 128, 24);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Scales {@code src} to {@code size}×{@code size} and clips it to a
     * rounded rectangle with corner radius {@code radius}.
     *
     * Uses progressive halving (each step ≤ 50% reduction) before the final
     * bicubic step — this preserves far more edge detail than a single large
     * downscale jump.
     */
    private static BufferedImage roundedSquare(BufferedImage src, int size, int radius) {
        // Progressive halving until we're within 2× of the target
        BufferedImage current = src;
        while (current.getWidth() > size * 2 || current.getHeight() > size * 2) {
            int hw = Math.max(size, current.getWidth()  / 2);
            int hh = Math.max(size, current.getHeight() / 2);
            BufferedImage half = new BufferedImage(hw, hh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D gh = half.createGraphics();
            gh.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gh.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            gh.drawImage(current, 0, 0, hw, hh, null);
            gh.dispose();
            current = half;
        }

        // Final step: bicubic to exact target size
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gs = scaled.createGraphics();
        gs.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        gs.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        gs.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        // Maintain aspect ratio — center in the square
        int sw = current.getWidth(), sh = current.getHeight();
        int dw, dh, dx, dy;
        if (sw >= sh) {
            dw = size; dh = size * sh / sw;
            dx = 0;    dy = (size - dh) / 2;
        } else {
            dh = size; dw = size * sw / sh;
            dy = 0;    dx = (size - dw) / 2;
        }
        gs.drawImage(current, dx, dy, dw, dh, null);
        gs.dispose();

        // Clip to rounded rectangle using alpha compositing
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRoundRect(0, 0, size, size, radius, radius);
        g.setComposite(java.awt.AlphaComposite.SrcIn);
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return out;
    }
}
