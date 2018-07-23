package com.suitandtiefinancial.baseball.game;

import java.util.*;

import com.suitandtiefinancial.baseball.game.Rules.StartStyle;
import com.suitandtiefinancial.baseball.player.Player;

/**
 * 
 * @author Boxxy
 *
 */
public class Game {
	public static final int ROWS = 3;
	public static final int COLUMNS = 3;

	private final int numberOfPlayers;
	private final Rules rules;
	private final Shoe shoe;
	private final ArrayList<Hand> hands;
	private final ArrayList<HandView> handViews;
	private final ArrayList<Player> players;
	private final GameView view;
	// TODO(stfinancial): Maybe recreate an eventQueue in GameRecord?
	private final Queue<Event> eventQueue;
	public boolean DEBUG_PRINT = false;

	private int currentPlayerIndex = 0;
	private int round = 0;
	Card lastDrawnCard = null;
	private int playerWentOut = -1;

	private boolean gameOver = false;

	public Game(int numberOfPlayers, int numberOfDecks, Rules rules) {
		this.numberOfPlayers = numberOfPlayers;
		eventQueue = new LinkedList<>();
		shoe = new Shoe(numberOfDecks);
		this.rules = rules;
		hands = new ArrayList<Hand>(numberOfPlayers);
		handViews = new ArrayList<HandView>(numberOfPlayers);
		createHandsAndHandViews();
		players = new ArrayList<Player>(numberOfPlayers);
		view = new GameView(this, rules, numberOfDecks, numberOfPlayers, players, handViews, shoe);

		eventQueue.add(new Event(EventType.SHUFFLE));
		dealHands();
		eventQueue.add(new Event(EventType.INITIAL_DEAL));
		shoe.pushDiscard(shoe.draw());
		eventQueue.add(new Event(EventType.INITIAL_DISCARD, shoe.peekDiscard()));
		processEventQueue();
	}

	private void createHandsAndHandViews() {
		for (int player = 0; player < numberOfPlayers; ++player) {
			Hand h = new Hand();
			hands.add(h);
			handViews.add(new HandView(h));
		}
	}

	private void dealHands() {
		Hand h;
	 	for (int playerIndex = 0; playerIndex < numberOfPlayers; playerIndex++) {
			h = hands.get(playerIndex);
			for (int row = 0; row < ROWS; row++) {
				for (int column = 0; column < COLUMNS; column++) {
					h.dealCard(shoe.draw(), row, column);
				}
			}
		}
	}

	public void addPlayer(Player p) {
		players.add(p);
	}

	public void tick() {
		if (round < 2) {
			tickOpener();
		} else {
			tickGame();
		}
	}

	private void tickOpener() {
		if (players.size() != numberOfPlayers) {
			throw new IllegalStateException("Started game before all expected players were added");
		}
		Move m = players.get(currentPlayerIndex).getOpener();
		Hand h = hands.get(currentPlayerIndex);
		if (m.getMoveType() == MoveType.FLIP) {
			eventQueue.add(new Event(EventType.FLIP, currentPlayerIndex, h.peekCard(m.getRow(), m.getColumn()), m.getRow(), m.getColumn()));
			h.flip(m.getRow(), m.getColumn());
		} else if (m.getMoveType() == MoveType.PEEK && rules.getStartStyle() != StartStyle.FLIP) {
			eventQueue.add(new Event(EventType.PEEK, currentPlayerIndex, m.getRow(), m.getColumn()));
			Card peekedCard = h.peekCard(m.getRow(), m.getColumn());
			players.get(currentPlayerIndex).showPeekedCard(m.getRow(), m.getColumn(), peekedCard);
		} else {
			throw new IllegalStateException("Illegal opening move of " + m + " by player " + currentPlayerIndex);
		}
		finishPlayerTurn();
	}

	private void tickGame() {
		if (lastDrawnCard == null) {
			tickStartOfTurn();
		} else {
			tickAfterDraw();
		}
	}

	private void tickStartOfTurn() {
		Move m = players.get(currentPlayerIndex).getMove();
		List<Card> toDiscard = Collections.emptyList();
		switch (m.getMoveType()) {
		case DRAW:
			if (shoe.isDeckEmpty()) {
				shoe.reset();
				eventQueue.add(new Event(EventType.SHUFFLE));
			}
			lastDrawnCard = shoe.draw();
			eventQueue.add(new Event(EventType.DRAW, currentPlayerIndex));
			break;
		case FLIP:
			toDiscard = hands.get(currentPlayerIndex).flip(m.getRow(), m.getColumn());
			if (!toDiscard.isEmpty()) {
				eventQueue.add(new Event(EventType.FLIP, currentPlayerIndex, toDiscard.get(0), m.getRow(), m.getColumn()));
				eventQueue.add(new Event(EventType.COLLAPSE, currentPlayerIndex, m.getColumn()));
			} else {
				eventQueue.add(new Event(EventType.FLIP, currentPlayerIndex, hands.get(currentPlayerIndex).peekCard(m.getRow(), m.getColumn()), m.getRow(), m.getColumn()));
			}
			break;
		case REPLACE_WITH_DISCARD:
			Card fromDiscard = shoe.popDiscard();
			eventQueue.add(new Event(EventType.DRAW_DISCARD, currentPlayerIndex, fromDiscard));
			Hand h = hands.get(currentPlayerIndex);
			eventQueue.add(new Event(EventType.SET, currentPlayerIndex, fromDiscard, m.getRow(), m.getColumn()));
			toDiscard = h.replace(fromDiscard, m.getRow(), m.getColumn());
			if (toDiscard.size() > 1) {
				eventQueue.add(new Event(EventType.COLLAPSE, currentPlayerIndex, m.getColumn()));
			}
			break;
		default:
			throw new IllegalStateException("Illegal move " + m + " by player " + currentPlayerIndex);
		}
		toDiscard.forEach(c -> eventQueue.add(new Event(EventType.DISCARD, currentPlayerIndex, c)));
		shoe.pushDiscard(toDiscard);
		finishPlayerTurn();
	}

	private void tickAfterDraw() {
		Move m = players.get(currentPlayerIndex).getMoveWithDraw(lastDrawnCard);
		switch (m.getMoveType()) {
		case DECLINE_DRAWN_CARD:
			shoe.pushDiscard(lastDrawnCard);
			eventQueue.add(new Event(EventType.DISCARD, currentPlayerIndex, lastDrawnCard));
			break;
		case REPLACE_WITH_DRAWN_CARD:
			Hand h = hands.get(currentPlayerIndex);
			eventQueue.add(new Event(EventType.SET, currentPlayerIndex, lastDrawnCard, m.getRow(), m.getColumn()));
			List<Card> toDiscard = h.replace(lastDrawnCard, m.getRow(), m.getColumn());
			if (toDiscard.size() > 1) {
				eventQueue.add(new Event(EventType.COLLAPSE, currentPlayerIndex, m.getColumn()));
			}
			toDiscard.forEach(c -> eventQueue.add(new Event(EventType.DISCARD, currentPlayerIndex, c)));
			shoe.pushDiscard(toDiscard);
			break;
		default:
			throw new IllegalStateException("Illegal move " + m + " by player " + currentPlayerIndex);

		}
		lastDrawnCard = null;
		finishPlayerTurn();
	}

	private void finishPlayerTurn() {
		if (lastDrawnCard != null) {
			return; // Player will need to make another move after they see their drawn card
		}
		if (playerWentOut < 0) {
			if (HandUtils.isOut(hands.get(currentPlayerIndex))) {
				playerWentOut = currentPlayerIndex;
			}
		} else {
			// This is hacky and gross, figure a better way to see if something was collapsed in the reveal.
			boolean col0, col1, col2;
			col0 = hands.get(currentPlayerIndex).getSpotState(0, 0) == SpotState.COLLAPSED;
			col1 = hands.get(currentPlayerIndex).getSpotState(0, 1) == SpotState.COLLAPSED;
			col2 = hands.get(currentPlayerIndex).getSpotState(0, 2) == SpotState.COLLAPSED;
			
			
			//Adding to the shit stain, I want flip events for the reports;
			for(int column = 0; column < COLUMNS; column++) {
				if(hands.get(currentPlayerIndex).getSpotState(0, column)== SpotState.COLLAPSED) {
					continue;
				}
				for(int row = 0; row < ROWS; row++) {
					if(hands.get(currentPlayerIndex).getSpotState(row, column) != SpotState.FACE_UP) {
						eventQueue.add(new Event(EventType.FLIP, currentPlayerIndex, hands.get(currentPlayerIndex).peekCard(row, column), row, column));
					}
				}
			}
			
			
			
			
			List<Card> toDiscard = hands.get(currentPlayerIndex).revealAll();
			if (!col0 && hands.get(currentPlayerIndex).getSpotState(0, 0) == SpotState.COLLAPSED) {
				eventQueue.add(new Event(EventType.COLLAPSE, currentPlayerIndex, 0));
			}
			if (!col1 && hands.get(currentPlayerIndex).getSpotState(0, 1) == SpotState.COLLAPSED) {
				eventQueue.add(new Event(EventType.COLLAPSE, currentPlayerIndex, 1));
			}
			if (!col2 && hands.get(currentPlayerIndex).getSpotState(0, 2) == SpotState.COLLAPSED) {
				eventQueue.add(new Event(EventType.COLLAPSE, currentPlayerIndex, 2));
			}
			toDiscard.forEach(c -> eventQueue.add(new Event(EventType.DISCARD, currentPlayerIndex, c)));
			shoe.pushDiscard(toDiscard);
		}

		currentPlayerIndex++;
		if (currentPlayerIndex == numberOfPlayers) {
			currentPlayerIndex = 0;
			round++;
		}

		if (currentPlayerIndex == playerWentOut) {
			gameOver = true;
			eventQueue.add(new Event(EventType.GAME_OVER));
		}

		processEventQueue();
	}

	private void processEventQueue() {
		Event e;
		while (!eventQueue.isEmpty()) {
			e = eventQueue.remove();
			if(this.DEBUG_PRINT) {
				System.out.println(e);
			}
			for (Player p: players) {
				p.processEvent(e);
			}
		}
	}

	public void printGameOver() {
		int minimum = 500000;
		int winner = 0;
		for (int playerIndex = 0; playerIndex < numberOfPlayers; playerIndex++) {
			int localTotal = HandUtils.getRevealedTotal(hands.get(playerIndex));
			System.out.println("Player " + playerIndex + " " + localTotal);
			if (localTotal < minimum) {
				winner = playerIndex;
				minimum = localTotal;
			}
		}
		System.out.println("Player " + winner + " wins with " + minimum);
	}

	@Override
	public String toString() {
		String s = "\n------------------------------------------------------------------------------------\n";

		for (int playerIndex = 0; playerIndex < numberOfPlayers; playerIndex++) {
			s += "P" + playerIndex + "   ";
		}
		s += "\n";
		for (int row = 0; row < Game.ROWS; row++) {
			for (int playerIndex = 0; playerIndex < numberOfPlayers; playerIndex++) {
				s += HandUtils.displayRow(hands.get(playerIndex), row) + "  ";
			}
			s += "\n";
		}
		s += "------------------------------------------------------------------------------------\n";
		return s;
	}

	public boolean isLastRound() {
		return playerWentOut != -1;
	}

	public boolean isGameOver() {
		return gameOver;
	}

	public int getWinner() {
		if (!gameOver) {
			throw new IllegalStateException();
		}
		int minimum = 50000, winner = -1;
		for (int playerIndex = 0; playerIndex < numberOfPlayers; playerIndex++) {
			int localTotal = HandUtils.getRevealedTotal(hands.get(playerIndex));
			System.out.println("Player " + playerIndex + " " + localTotal);
			if (localTotal < minimum) {
				winner = playerIndex;
				minimum = localTotal;
			}
		}
		return winner;
	}

	public int getRound() {
		return round;
	}

	public int getPlayerWhoWentOut() {
		return playerWentOut;
	}

	public GameView getGameView() { return view; }

	public GameRecord generateGameRecord(int focusPlayerIndex) {
		return new GameRecord(focusPlayerIndex, this);
	}

}
