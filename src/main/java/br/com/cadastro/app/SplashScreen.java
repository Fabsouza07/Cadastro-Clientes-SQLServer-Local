package br.com.cadastro.app;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

final class SplashScreen extends JWindow {
  SplashScreen() {
    JPanel content = new JPanel(new GridBagLayout());
    content.setBackground(new Color(15, 23, 42));
    content.setBorder(new EmptyBorder(34, 52, 34, 52));

    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.anchor = GridBagConstraints.CENTER;
    JLabel mark = new JLabel("C", SwingConstants.CENTER);
    mark.setOpaque(true);
    mark.setBackground(new Color(37, 99, 235));
    mark.setForeground(Color.WHITE);
    mark.setFont(new Font("Segoe UI", Font.BOLD, 30));
    mark.setPreferredSize(new Dimension(62, 62));
    content.add(mark, c);

    c.gridy = 1;
    c.insets = new Insets(18, 0, 3, 0);
    JLabel title = new JLabel("Cadastro de Clientes");
    title.setForeground(Color.WHITE);
    title.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 24));
    content.add(title, c);

    c.gridy = 2;
    c.insets = new Insets(0, 0, 18, 0);
    JLabel subtitle = new JLabel("Preparando seu ambiente de trabalho");
    subtitle.setForeground(new Color(148, 163, 184));
    subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    content.add(subtitle, c);

    c.gridy = 3;
    c.fill = GridBagConstraints.HORIZONTAL;
    c.weightx = 1;
    JProgressBar progress = new JProgressBar();
    progress.setIndeterminate(true);
    progress.setBorderPainted(false);
    progress.setPreferredSize(new Dimension(290, 5));
    content.add(progress, c);
    setContentPane(content);
    pack();
    setLocationRelativeTo(null);
  }
}
