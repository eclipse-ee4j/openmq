/*
 * Copyright (c) 2000, 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

import java.util.HashMap;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.table.*;

import javax.management.*;
import javax.management.remote.*;
import javax.management.openmbean.CompositeData;

import com.sun.messaging.AdminConnectionFactory;
import com.sun.messaging.AdminConnectionConfiguration;
import com.sun.messaging.jms.management.server.*;

public class MQClusterMonitor implements ActionListener, Runnable {

	JFrame f;
	MQConnectDialog connectDialog = null;
	JMenuItem exit, connect, disconnect;
	JTextArea logMsgArea, statusTextArea;
	JLabel brokerAddress;
	JTable clsTable;

	String address = null, adminUser = "admin", adminPasswd = "admin";

	boolean stopRequested = false;
	long seconds = 2;

	AdminConnectionFactory acf;
	JMXConnector jmxc;
	MBeanServerConnection mbsc;
	ObjectName clsMon = null;
	NotificationFilterSupport myFilter = null;

	public MQClusterMonitor(JFrame f, String address, String adminUser, String adminPasswd, long seconds) {
		this.f = f;
		this.address = address;
		this.adminUser = adminUser;
		this.adminPasswd = adminPasswd;
		this.seconds = seconds;

		try {
			clsMon = new ObjectName(MQObjectName.CLUSTER_MONITOR_MBEAN_NAME);
		} catch (Exception e) {
			addStatusText("Caught exception while creating Log MBean ObjectName: " + e);
		}

		initGUI();

		if ((address != null) && (adminUser != null) && (adminPasswd != null)) {
			doConnect();
		}
	}

	public synchronized void requestStop() {
		stopRequested = true;
		ClusterTableModel m = (ClusterTableModel) clsTable.getModel();
		m.load(null);
	}

	public synchronized void resetStop() {
		stopRequested = false;
	}

	public synchronized boolean stopRequested() {
		return (stopRequested);
	}

	public void run() {
		addStatusText("Monitor thread started (refresh interval = " + seconds + " seconds).");

		while (true) {
			try {
				Thread.sleep(seconds * 1000);
				if (stopRequested()) {
					addStatusText("Monitor thread stopped");
					break;
				}
			} catch (java.lang.InterruptedException ie) {
				addStatusText("Exception caught while waiting to reload: " + ie);
			}

			load();
		}
	}

	public void load() {
		try {
			CompositeData cd[] = (CompositeData[]) mbsc.invoke(clsMon, ClusterOperations.GET_BROKER_INFO, null, null);

			ClusterTableModel m = (ClusterTableModel) clsTable.getModel();
			m.load(cd);
		} catch (Exception e) {
			addStatusText("Exception caught while reloading data: " + e);
			e.printStackTrace();
		}
	}

	private void initGUI() {
		JMenuBar menubar = createMenubar();
		JComponent toolbar = createToolBar();
		JPanel mainPanel = createMainPanel();
		JPanel statusArea = createStatusArea();

		f.setJMenuBar(menubar);
		f.getContentPane().add(toolbar, BorderLayout.NORTH);
		f.getContentPane().add(mainPanel, BorderLayout.CENTER);
		f.getContentPane().add(statusArea, BorderLayout.SOUTH);
	}

	public void addStatusText(String statusText) {
		statusTextArea.append(statusText);
		statusTextArea.setCaretPosition(statusTextArea.getText().length());
		statusTextArea.append("\n");
	}

	public void clearStatus() {
		statusTextArea.setText("");
	}

	public void doConnect() {
		try {
			acf = new AdminConnectionFactory();
			if (address != null) {
				acf.setProperty(AdminConnectionConfiguration.imqAddress, address);
			}
			jmxc = acf.createConnection(adminUser, adminPasswd);
			mbsc = jmxc.getMBeanServerConnection();

			addStatusText("Connected to broker at: " + acf.getProperty(AdminConnectionConfiguration.imqAddress));

			brokerAddress.setText(acf.getProperty(AdminConnectionConfiguration.imqAddress));

			resetStop();

			new Thread(this).start();

			connect.setEnabled(false);
			disconnect.setEnabled(true);
		} catch (Exception e) {
			addStatusText("Caught exception while connecting: " + e);
		}
	}

	public void doDisconnect() {
		try {
			requestStop();

			addStatusText("Disconnecting from broker at: " + acf.getProperty(AdminConnectionConfiguration.imqAddress));

			brokerAddress.setText("<none>");

			if (jmxc != null) {
				jmxc.close();
			}
			jmxc = null;
			mbsc = null;
			acf = null;
			connect.setEnabled(true);
			disconnect.setEnabled(false);
		} catch (Exception e) {
			addStatusText("Caught exception while disconnecting: " + e);
		}
	}

	private JMenuBar createMenubar() {
		JMenuBar menubar;
		JMenu menu, logLevelMenu;

		menubar = new JMenuBar();

		menu = new JMenu("ClusterMonitor");
		menubar.add(menu);

		connect = new JMenuItem("Connect");
		connect.addActionListener(this);
		menu.add(connect);

		disconnect = new JMenuItem("Disconnect");
		disconnect.addActionListener(this);
		disconnect.setEnabled(false);
		menu.add(disconnect);

		menu.addSeparator();

		exit = new JMenuItem("Exit");
		exit.addActionListener(this);
		menu.add(exit);

		return (menubar);
	}

	private JPanel createMainPanel() {
		JPanel p = new JPanel();

		p.setLayout(new BorderLayout());

		clsTable = new JTable(new ClusterTableModel()) {
			public TableCellRenderer getCellRenderer(int row, int col) {
				DefaultTableCellRenderer rowRenderer;

				if (col != 2) {
					return (super.getCellRenderer(row, col));
				}

				int state = getBrokerState(row);

				switch (state) {
				case BrokerState.TAKEOVER_COMPLETE:
					rowRenderer = new DefaultTableCellRenderer();
					rowRenderer.setBackground(Color.yellow);
					return (rowRenderer);

				case BrokerState.BROKER_DOWN:
				case BrokerState.TAKEOVER_STARTED:
				case BrokerState.SHUTDOWN_STARTED:
					rowRenderer = new DefaultTableCellRenderer();
					rowRenderer.setBackground(Color.red);
					return (rowRenderer);

					/*
					 * default case handles: BrokerState.OPERATING:
					 * BrokerState.QUIESCE_STARTED:
					 * BrokerState.QUIESCE_COMPLETE:
					 */
				default:
					return (super.getCellRenderer(row, col));
				}
			}

			public int getBrokerState(int row) {
				ClusterTableModel m = (ClusterTableModel) clsTable.getModel();

				return (m.getBrokerState(row));
			}

		};

		JScrollPane tablePane = new JScrollPane(clsTable);
		tablePane.setPreferredSize(new Dimension(640, 100));
		p.add(BorderLayout.CENTER, tablePane);

		return (p);
	}

	private JComponent createToolBar() {
		JPanel p = new JPanel();
		JLabel l;

		p.setLayout(new FlowLayout(FlowLayout.LEFT));

		l = new JLabel("Cluster state as reported by broker at address: ");
		p.add(l);

		brokerAddress = new JLabel("<none>");
		p.add(brokerAddress);

		return (p);
	}

	private JPanel createStatusArea() {
		JPanel p = new JPanel();
		p.setLayout(new BorderLayout());
		statusTextArea = new JTextArea(5, 60);
		statusTextArea.setLineWrap(true);
		statusTextArea.setEditable(false);
		JScrollPane statusTextPane = new JScrollPane(statusTextArea);
		p.add(statusTextPane, BorderLayout.CENTER);

		return (p);
	}

	public void actionPerformed(ActionEvent e) {
		Object src = e.getSource();

		if (src instanceof JMenuItem) {
			JMenuItem mi = (JMenuItem) src;

			if (src == exit) {
				System.exit(0);
			} else if (src == connect) {
				showConnectDialog();
			} else if (src == disconnect) {
				doDisconnect();
			}
		} else if (src instanceof JButton) {
			address = connectDialog.getAddress();
			adminUser = connectDialog.getUserName();
			adminPasswd = connectDialog.getPassword();

			doConnect();
		}
	}

	private void showConnectDialog() {
		if (connectDialog == null) {
			connectDialog = new MQConnectDialog(f, "Connect to Broker", this);

			connectDialog.setAddress((address == null) ? getDefaultAddress() : address);
			connectDialog.setUserName((adminUser == null) ? getDefaultUserName() : adminUser);
			connectDialog.setPassword((adminPasswd == null) ? getDefaultPassword() : adminPasswd);
		}
		connectDialog.setLocationRelativeTo(f);
		connectDialog.setVisible(true);
	}

	private static void doExit() {
		System.exit(0);
	}

	private String getDefaultUserName() {
		AdminConnectionFactory acf = new AdminConnectionFactory();
		String addr;

		try {
			addr = acf.getProperty(AdminConnectionConfiguration.imqDefaultAdminUsername);
		} catch (Exception e) {
			addr = null;
		}

		return (addr);
	}

	private String getDefaultPassword() {
		AdminConnectionFactory acf = new AdminConnectionFactory();
		String addr;

		try {
			addr = acf.getProperty(AdminConnectionConfiguration.imqDefaultAdminPassword);
		} catch (Exception e) {
			addr = null;
		}

		return (addr);
	}

	private String getDefaultAddress() {
		/*
		 * AdminConnectionFactory acf = new AdminConnectionFactory(); String
		 * addr;
		 * 
		 * try { addr =
		 * acf.getProperty(AdminConnectionConfiguration.imqAddress); }
		 * catch(Exception e) { addr = null; }
		 * 
		 * return (addr);
		 */
		return ("localhost:7676");
	}

	public static void main(String[] args) {
		JFrame frame;
		MQClusterMonitor s;
		String address = null, adminUser = null, adminPasswd = null, secondStr = null;
		long seconds = 2;

		for (int i = 0; i < args.length; ++i) {
			if (args[i].equals("-b")) {
				if (++i >= args.length) {
					usage();
				}
				address = args[i];
			} else if (args[i].equals("-u")) {
				if (++i >= args.length) {
					usage();
				}
				adminUser = args[i];
			} else if (args[i].equals("-p")) {
				if (++i >= args.length) {
					usage();
				}
				adminPasswd = args[i];
			} else if (args[i].equals("-int")) {
				if (++i >= args.length) {
					usage();
				}
				secondStr = args[i];

				try {
					seconds = Long.valueOf(secondStr).longValue();
				} catch (NumberFormatException nfe) {
					usage("Failed to parse interval value: " + secondStr);
				}
			} else {
				usage();
			}
		}

		frame = new JFrame("MQ Cluster Monitor");
		s = new MQClusterMonitor(frame, address, adminUser, adminPasswd, seconds);

		frame.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				doExit();
			}
		});

		frame.pack();
		frame.setVisible(true);
	}

	public static void usage() {
		usage(null);
	}

	public static void usage(String msg) {
		if (msg != null) {
			System.err.println(msg);
		}

		System.err.println("java MQClusterMonitor" + "[-b <host:port>] [-u <admin user name>] [-p <admin password>]");
		doExit();
	}
}

class ClusterTableModel extends AbstractTableModel {
	private String[] columnNames = { "Broker ID", "Broker Address", "Broker State", "# Msgs in Store",
			"Takeover Broker ID", "Time since last status timestamp" };

	CompositeData clusterInfo[];

	public ClusterTableModel() {
	}

	public void load(CompositeData cd[]) {
		if (cd != null) {
			clusterInfo = cd;
		} else {
			clusterInfo = null;
		}
		fireTableDataChanged();
	}

	public int getRowCount() {
		if (clusterInfo == null) {
			return (0);
		}

		return (clusterInfo.length);
	}

	public int getColumnCount() {
		return (columnNames.length);
	}

	public String getColumnName(int column) {
		return columnNames[column];
	}

	public Object getValueAt(int row, int column) {
		if ((clusterInfo == null) || (clusterInfo.length == 0)) {
			return ("");
		}

		CompositeData info = clusterInfo[row];

		switch (column) {

		case 0:
			// only set for HA clusters, otherwise null
			return (info.get(BrokerClusterInfo.ID));

		case 1:
			return (info.get(BrokerClusterInfo.ADDRESS));

		case 2:
			return (BrokerState.toString(((Integer) info.get(BrokerClusterInfo.STATE)).intValue()));

		case 3:
			Object numMsgsObj = info.get(BrokerClusterInfo.NUM_MSGS);
			// only set for HA clusters, otherwise null
			if (numMsgsObj != null) {
				return numMsgsObj.toString();
			} else {
				return "";
			}
			// return ((info.get(BrokerClusterInfo.NUM_MSGS)).toString());

		case 4:
			// only set for HA clusters, otherwise null
			return (info.get(BrokerClusterInfo.TAKEOVER_BROKER_ID));

		case 5:
			// only set for HA clusters, otherwise null
			Long statusTimestampObj = (Long) info.get(BrokerClusterInfo.STATUS_TIMESTAMP);
			if (statusTimestampObj != null) {
				long tmpLong = ((Long) info.get(BrokerClusterInfo.STATUS_TIMESTAMP)).longValue();
				long idle = System.currentTimeMillis() - tmpLong;
				return (getTimeString(idle));
			} else {
				return "";
			}

		}

		return ("");
	}

	public int getBrokerState(int row) {
		if ((clusterInfo == null) || (clusterInfo.length == 0)) {
			return (BrokerState.BROKER_DOWN);
		}

		CompositeData info = clusterInfo[row];

		if (info == null) {
			return (BrokerState.BROKER_DOWN);
		}

		int state = ((Integer) info.get(BrokerClusterInfo.STATE)).intValue();

		return (state);
	}

	private String getTimeString(long millis) {
		String ret = null;

		if (millis < 1000) {
			ret = millis + " milliseconds";
		} else if (millis < (60 * 1000)) {
			long seconds = millis / 1000;
			ret = seconds + " seconds";
		} else if (millis < (60 * 60 * 1000)) {
			long mins = millis / (60 * 1000);
			ret = mins + " minutes";
		} else {
			ret = "> 1 hour";
		}

		return (ret);
	}
}
