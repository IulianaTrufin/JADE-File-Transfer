package agents;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.lang.acl.ACLMessage;

public class ManagerAgent extends Agent {

	private JFrame frame;

	@Override
	protected void setup() {
		System.out.println(getLocalName() + " pornit.");
		createGui();
	}

	@Override
	protected void takeDown() {
		if (frame != null) {
			SwingUtilities.invokeLater(() -> {
				try {
					frame.dispose();
				} catch (Exception ignored) {
				}
			});
		}
		System.out.println(getLocalName() + " se opreste.");
	}

	private void createGui() {
		frame = new JFrame("Manager Agent - Control");
		JButton shutdownButton = new JButton("Shutdown System");

		shutdownButton.addActionListener(this::onShutdownClicked);

		frame.getContentPane().add(shutdownButton, BorderLayout.CENTER);
		frame.pack();
		frame.setLocationRelativeTo(null);
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setVisible(true);
	}

	private void onShutdownClicked(ActionEvent e) {
		addBehaviour(new OneShotBehaviour() {
			@Override
			public void action() {
				broadcastShutdown();
			}
		});
	}

	private void broadcastShutdown() {
		try {
			DFAgentDescription template = new DFAgentDescription();
			DFAgentDescription[] result = DFService.search(this, template);

			for (DFAgentDescription dfd : result) {
				AID aid = dfd.getName();
				if (aid.equals(getAID())) {
					continue;
				}

				ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
				msg.addReceiver(aid);
				msg.setOntology("shutdown");
				msg.setContent("Please shutdown");
				send(msg);
			}

			doDelete();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
