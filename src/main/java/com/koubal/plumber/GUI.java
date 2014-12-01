package com.koubal.plumber;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class GUI extends JFrame {
	private final JTextArea statusArea;
	private final JScrollPane scrollPane;
	private final JButton startButton;
	private final JProgressBar progressBar;

	public GUI() {
		setSize(512 + 16, 256 + 32 + 42);
		setResizable(false);
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setTitle("Plumber");

		JPanel panel = new JPanel();

		statusArea = new JTextArea();
		statusArea.setEditable(false);
		DefaultCaret caret = (DefaultCaret) statusArea.getCaret();
		caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

		scrollPane = new JScrollPane();
		scrollPane.setPreferredSize(new Dimension(512, 256));
		scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scrollPane.getViewport().add(statusArea);
		panel.add(scrollPane);

		startButton = new JButton("Start");
		startButton.setPreferredSize(new Dimension(128, 32));
		startButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (Plumber.isRunning()) {
					return;
				}


				new Thread(new Runnable() {
					@Override
					public void run() {
						try {
							Plumber.run();
						} catch (Exception e) {
							appendStatus(e.getMessage());
							for (StackTraceElement traceElement : e.getStackTrace()) {
								appendStatus(traceElement.toString());
							}
							startButton.setText("Error!");
							progressBar.setForeground(Color.RED);
						}
					}
				}).start();
				startButton.setText("Running");
			}
		});
		panel.add(startButton);

		progressBar = new JProgressBar();
		progressBar.setPreferredSize(new Dimension(384, 32));
		progressBar.setStringPainted(true);
		panel.add(progressBar);

		add(panel);
	}

	public void appendStatus(String status) {
		statusArea.append(status + "\n");
	}

	public void setProgress(int progress) {
		progress = (int) (progress * (100.0/27));
		progressBar.setValue(progress);
		progressBar.setString(progress + "%");

		if (progress >= 100) {
			progressBar.setForeground(Color.GREEN);
			startButton.setText("Success!");
			progressBar.setString("100%");
		}
	}
}
