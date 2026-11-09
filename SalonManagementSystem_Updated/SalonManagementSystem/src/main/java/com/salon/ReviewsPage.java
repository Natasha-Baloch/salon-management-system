package com.salon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/** Customer reviews as cards: stars, comment, service and an optional photo. Used by clients, staff and the owner. */
public class ReviewsPage extends BackgroundPanel {
    private final ScrollPanel list = new ScrollPanel(new GridLayout(0, 1, 0, 12));
    private final JLabel summary = new JLabel(" ");

    /** @param writeFeedback shown as a button when not null (clients only) */
    public ReviewsPage(String title, String subtitle, Runnable writeFeedback) {
        super(new BorderLayout(12, 12), 77);
        setBorder(new EmptyBorder(14, 14, 14, 14));
        RoundedPanel head = new RoundedPanel(new BorderLayout(10, 6), 20);
        head.setBackground(Color.WHITE);
        JLabel t = new JLabel(title);
        t.setFont(Theme.H2); t.setForeground(Theme.PURPLE_DARK);
        JLabel st = new JLabel(subtitle);
        st.setFont(Theme.BODY); st.setForeground(Theme.MUTED);
        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(t); text.add(st);
        summary.setFont(Theme.serif(Font.BOLD, 20));
        summary.setForeground(new Color(214, 140, 40));
        summary.setHorizontalAlignment(SwingConstants.CENTER);
        head.add(text, BorderLayout.WEST);
        head.add(summary, BorderLayout.CENTER);
        if (writeFeedback != null) {
            JButton w = Theme.button("✍ WRITE FEEDBACK", Theme.ROSE_GOLD);
            w.addActionListener(e -> writeFeedback.run());
            head.add(w, BorderLayout.EAST);
        }
        add(head, BorderLayout.NORTH);

        list.setBorder(new EmptyBorder(2, 2, 2, 8));
        JPanel holder = new ScrollPanel(new BorderLayout());
        holder.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(18);
        add(sp, BorderLayout.CENTER);
        Ui.register(this::refresh);
        refresh();
    }

    public static String stars(int n) { return "★".repeat(Math.max(0, n)) + "☆".repeat(Math.max(0, 5 - n)); }

    public final void refresh() {
        try {
            List<Salon.Review> reviews = Salon.reviews();
            list.removeAll();
            double sum = 0;
            for (Salon.Review r : reviews) { sum += r.rating(); list.add(card(r)); }
            summary.setText(reviews.isEmpty() ? "No reviews yet" : String.format("%s  %.1f / 5  (%d review%s)", stars((int) Math.round(sum / reviews.size())),
                    sum / reviews.size(), reviews.size(), reviews.size() == 1 ? "" : "s"));
            if (reviews.isEmpty()) {
                JLabel none = new JLabel("  Reviews will appear here after clients share their feedback.");
                none.setFont(Theme.BODY); none.setForeground(Theme.MUTED);
                list.add(none);
            }
            list.revalidate(); list.repaint();
        } catch (Exception e) { Ui.error(this, e); }
    }

    private JComponent card(Salon.Review r) {
        RoundedPanel c = new RoundedPanel(new BorderLayout(14, 4), 20);
        c.setBackground(Color.WHITE);
        c.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel stars = new JLabel(stars(r.rating()));
        stars.setFont(new Font("SansSerif", Font.BOLD, 20));
        stars.setForeground(new Color(224, 160, 40));
        JLabel who = new JLabel("  " + r.client());
        who.setFont(Theme.BOLD); who.setForeground(Theme.PURPLE_DARK);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        top.setOpaque(false);
        top.add(stars); top.add(who);
        JLabel meta = new JLabel(r.service() + (r.staff().isBlank() ? "" : "  •  served by " + r.staff()) + "  •  " + r.date());
        meta.setFont(Theme.BODY); meta.setForeground(Theme.MUTED);
        JTextArea comment = new JTextArea(r.comment().isBlank() ? "(no comment)" : r.comment());
        comment.setLineWrap(true); comment.setWrapStyleWord(true); comment.setEditable(false); comment.setOpaque(false);
        comment.setFont(Theme.BODY); comment.setForeground(Theme.TEXT);
        JPanel body = new JPanel(new BorderLayout(0, 3));
        body.setOpaque(false);
        JPanel head = new JPanel(new GridLayout(2, 1));
        head.setOpaque(false);
        head.add(top); head.add(meta);
        body.add(head, BorderLayout.NORTH);
        body.add(comment, BorderLayout.CENTER);
        c.add(body, BorderLayout.CENTER);
        if (r.photo() != null && r.photo().length > 0) {
            JLabel pic = new JLabel(ImageUtil.icon(r.photo(), "", "", 150, 110));
            pic.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1));
            c.add(pic, BorderLayout.EAST);
        }
        return c;
    }
}
