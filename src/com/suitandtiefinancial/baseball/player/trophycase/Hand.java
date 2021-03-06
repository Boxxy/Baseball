package com.suitandtiefinancial.baseball.player.trophycase;

import com.suitandtiefinancial.baseball.game.Card;
import com.suitandtiefinancial.baseball.game.Game;
import com.suitandtiefinancial.baseball.game.SpotState;

import java.util.Iterator;

// TODO(stfinancial): I think this is only our representation about our own hand.

/**
 * Player internal representation of the player's knowledge about a hand.
 */
class Hand implements Iterable<Hand.Spot> {
    private Spot[][] spots;
    private int rows;
    private int columns;

    Hand(int rows, int columns) {
        spots = new Spot[rows][columns];
        for (int row = 0; row < rows; ++row) {
            for (int column = 0; column < columns; ++column) {
                spots[row][column] = new Spot(row, column);
            }
        }
    }

    void clear() {
        for (int row = 0; row < rows; ++row) {
            for (int column = 0; column < columns; ++column) {
                spots[row][column].clear();
            }
        }
    }

    /** Mark a face-down card as peeked, set a card value if we peeked at our own card */
    void setPeekedCard(Card c, int row, int column) {
        // TODO(stfinancial): Allow setting null values for cards to represent other players' peeks
        spots[row][column].card = c;
        spots[row][column].state = SpotState.FACE_DOWN_PEEKED;
    }

//    void flipCard(int row, int column) {
//        spots[row][column].
//    }

    /** Set the card value of a spot, and set to face-up if not already */
    void setCard(Card c, int row, int column) {
        spots[row][column].card = c;
        spots[row][column].state = SpotState.FACE_UP;
    }

    SpotState getSpotState(int row, int column) {
        return spots[row][column].state;
    }

    /** If the card is face-down and not been peeked, return null. Otherwise, return the Card */
    Card viewCard(int row, int column) {
        if (spots[row][column].state == SpotState.FACE_DOWN) {
            return null;
        }
        return spots[row][column].card;
    }

    void collapseColumn(int column) {
        for (int row = 0; row < Game.ROWS; ++row) {
            spots[row][column].state = SpotState.COLLAPSED;
            spots[row][column].card = null;
        }
    }

    @Override
    /** Iterates over the hand spots by column then by row. That is, column is the inner loop and row is the outer loop */
    public Iterator<Spot> iterator() {
        return new Iterator<Spot>() {
            int row = 0;
            int column = 0;

            @Override
            public boolean hasNext() {
                return row < Game.ROWS;
            }

            @Override
            public Spot next() {
                Spot s = spots[row][column++];
                if (column < Game.COLUMNS) {
                    column = 0;
                    ++row;
                }
                return s;
            }
        };
    }

    class Spot {
        private Card card = null;
        private SpotState state = SpotState.FACE_DOWN;
        private int row;
        private int column;

        private Spot(int row, int column) {
            this.row = row;
            this.column = column;
        }

        private void clear() {
            card = null;
            state = SpotState.FACE_DOWN;
        }

        int getRow() { return row; }
        int getColumn() { return column; }
        Card getCard() { return card; }
        SpotState getState() { return state; }
    }
}
